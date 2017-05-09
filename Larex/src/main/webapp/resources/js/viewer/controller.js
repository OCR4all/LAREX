function Controller(bookID, canvasID, specifiedColors) {
	var _bookID = bookID;
	var _communicator = new Communicator();
	var _gui;
	var _editor;
	var _currentPage;
	var _segmentedPages = [];
	var _book;
	var _segmentation;
	var _settings;
	var _activesettings;
	var _segmentationtypes;
	var _actions = [];
	var _actionpointer = -1;
	var _presentRegions = [];

	var _thisController = this;
	var _selected = [];
	this.selectmultiple = false;
	var _selectType;
	var _visibleRegions = {}; // !_visibleRegions.contains(x) and _visibleRegions[x] == false => x is hidden

	var _newPathCounter = 0;

	// main method
	$(window).ready(function() {
				// Init PaperJS
				paper.setup(document.getElementById(canvasID));
				resizeViewerHeight();

				_currentPage = 0;
				_thisController.showPreloader(true);
				_communicator.loadBook(_bookID,_currentPage).done(function(data) {
							_book = data.book;
							_segmentation = data.segmentation;
							_segmentationtypes = data.segmenttypes;
							_settings = data.settings;
							// clone _settings
							_activesettings = JSON.parse(JSON.stringify(_settings));
							_segmentedPages.push(_currentPage);

							// Init the viewer
							var navigationController = new NavigationController();
							var viewerInput = new ViewerInput(_thisController);

							// Inheritance Editor extends Viewer
							_editor = new Editor(new Viewer(
									_segmentationtypes, viewerInput,specifiedColors),
									_thisController);

							_gui = new GUI(canvasID, _editor);
							_gui.setCanvasUITopRight();
							_gui.resizeViewerHeight();

							_gui.setParameters(_settings.parameters);
							_gui.setRegionLegendColors(_segmentationtypes);

							navigationController.setGUI(_gui);
							navigationController.setViewer(_editor);
							// setup paper again because of pre-resize bug
							// (streched)
							paper.setup(document.getElementById(canvasID));

							_thisController.displayPage(0);

							_thisController.showPreloader(false);

							// Init inputs
							var keyInput = new KeyInput(navigationController,
									_thisController, _gui);
							$("#"+canvasID).mouseover(function(){keyInput.isActive = true;});
							$("#"+canvasID).mouseleave(function(){keyInput.isActive = false;});
							var guiInput = new GuiInput(navigationController,
									_thisController, _gui);

							// on resize
							$(window).resize(function() {
								_gui.setCanvasUITopRight();
								_gui.resizeViewerHeight();
							});
						});
			});

	this.displayPage = function(pageNr) {
		_currentPage = pageNr;

		if (_segmentedPages.indexOf(_currentPage) < 0) {
				requestSegmentation([_currentPage]);
		}else{
				_editor.clear();
				_editor.setImage(_book.pages[_currentPage].image);
				var pageSegments = _segmentation.pages[_currentPage].segments;
				// Iterate over Segment-"Map" (Object in JS)
				Object.keys(pageSegments).forEach(function(key) {
					_editor.addSegment(pageSegments[key]);
				});
				var pageFixedSegments = _settings.pages[_currentPage].segments;
				// Iterate over FixedSegment-"Map" (Object in JS)
				Object.keys(pageFixedSegments).forEach(function(key) {
					_editor.addSegment(pageFixedSegments[key]);
				});

				var regions = _settings.regions;
				// Iterate over Regions-"Map" (Object in JS)
				Object.keys(regions).forEach(function(key) {
					var region = regions[key];

					// Iterate over all Polygons in Region
					Object.keys(region.polygons).forEach(function(polygonKey) {
						var polygon = region.polygons[polygonKey];

						_editor.addRegion(polygon);

						if(!_visibleRegions[region.type]){
							_editor.hideSegment(polygon.id,true);
						}
					});

					if($.inArray(region.type, _presentRegions) < 0){
						//_presentRegions does not contains region.type
						_presentRegions.push(region.type);
					}
				});

				var pageCuts = _settings.pages[_currentPage].cuts;
				// Iterate over FixedSegment-"Map" (Object in JS)
				Object.keys(pageCuts).forEach(function(key) {
					_editor.addLine(pageCuts[key]);
				});
				_editor.center();
				_editor.zoomFit();

				_gui.updateZoom();
				_gui.showUsedRegionLegends(_presentRegions);
		}
	}
	this.addPresentRegions = function(regionType){
		if($.inArray(regionType, _presentRegions) < 0){
			//_presentRegions does not contains region.type
			_presentRegions.push(regionType);
		}
		_gui.showUsedRegionLegends(_presentRegions);
	}
	this.removePresentRegions = function(regionType){
		_presentRegions = jQuery.grep(_presentRegions, function(value) {
  		return value != regionType;
		});
		_gui.showUsedRegionLegends(_presentRegions);
	}

	// New Segmentation with different Settings
	this.doSegmentation = function(pages) {
		var parameters = _gui.getParameters();
		_settings.parameters = parameters;

		// clone _settings
		_activesettings = JSON.parse(JSON.stringify(_settings));
		_segmentedPages = [];
		requestSegmentation(pages);
	}

	var requestSegmentation = function(pages){
		_thisController.showPreloader(true);
		if(pages == null){
				pages = [_currentPage];
		}

		_communicator.segmentBook(_activesettings,pages).done(function(data){
				_segmentedPages.push.apply(_segmentedPages,pages);
				_segmentation = data;
				_thisController.displayPage(pages[0]);
				_thisController.showPreloader(false);
		});
	}

	// Actions
	this.redo = function() {
		if (_actionpointer < _actions.length - 1) {
			this.unSelect();
			_actionpointer++;
			_actions[_actionpointer].execute();
		}
	}
	this.undo = function() {
		if (_actionpointer >= 0) {
			this.unSelect();
			_actions[_actionpointer].undo();
			_actionpointer--;
		}
	}
	this.createPolygon = function(doSegment) {
		_editor.startCreatePolygon(doSegment);
	}
	this.createRectangle = function(doSegment) {
		_editor.startCreateRectangle(doSegment);
	}
	this.createCut = function() {
		_editor.startCreateLine();
	}
	this.endEditing = function(){
		_editor.endEditing();
	}
	this.deleteSelected = function() {
		var actions = [];
		for (var i = 0, selectedlength = _selected.length; i < selectedlength; i++) {
			if (_selectType === "region") {
				var regionPolygon = getRegionByID(_selected[i]);
				actions.push(new ActionRemoveRegion(regionPolygon, _editor, _settings, _currentPage,_thisController));
			} else if(_selectType === "segment"){
				var segment = _segmentation.pages[_currentPage].segments[_selected[i]];
				//Check if result segment or fixed segment (null -> fixed segment)
				if(segment != null){
					actions.push(new ActionRemoveSegment(segment,_editor,_segmentation,_currentPage));
				}else{
					segment = _settings.pages[_currentPage].segments[_selected[i]];
					actions.push(new ActionRemoveSegment(segment,_editor,_settings,_currentPage));
				}
			}else if(_selectType === "line"){
				var cut = _settings.pages[_currentPage].cuts[_selected[i]];
				actions.push(new ActionRemoveCut(cut,_editor,_settings,_currentPage));
			}
		}
		this.unSelect();
		var multidelete = new ActionMultiple(actions);
		addAndExecuteAction(multidelete);
	}
	this.changeTypeSelected = function(newType) {
		var selectedlength = _selected.length;
		if(selectedlength != null || selectedlength > 0){
			var actions = [];
			for (var i = 0, selectedlength; i < selectedlength; i++) {
				if(_selectType === "region"){
					var regionPolygon = getRegionByID(_selected[i]);
					actions.push(new ActionChangeTypeRegionPolygon(regionPolygon, newType, _editor, _settings,_currentPage,_thisController));

					_thisController.hideRegion(newType,false);
				} else if(_selectType === "segment"){
					var isFixedSegment = (_settings.pages[_currentPage].segments[_selected[i]] != null);
					if(isFixedSegment){
						actions.push(new ActionChangeTypeSegment(_selected[i], newType, _editor, _settings, _currentPage));
					}else{
						actions.push(new ActionChangeTypeSegment(_selected[i], newType, _editor, _segmentation, _currentPage));
					}
				}
			}
			var multiChange = new ActionMultiple(actions);
			addAndExecuteAction(multiChange);
		}
	}
	this.createBorder = function(doSegment) {
		_editor.startCreateBorder(doSegment);
	}
	this.callbackNewRegion = function(regionpoints) {
		var newID = "created" + _newPathCounter;
		_newPathCounter++;
		var type = _presentRegions[0];
		if(!type){
			type = "other";
		}
		var actionAdd = new ActionAddRegion(newID, regionpoints, type,
				_editor, _settings, _currentPage);

		addAndExecuteAction(actionAdd);
		_thisController.openContextMenu(false,newID);
	}
	this.callbackNewFixedSegment = function(segmentpoints) {
		var newID = "created" + _newPathCounter;
		_newPathCounter++;
		var type = _presentRegions[0];
		if(!type){
			type = "other";
		}
		var actionAdd = new ActionAddFixedSegment(newID, segmentpoints, type,
				_editor, _settings, _currentPage);

		addAndExecuteAction(actionAdd);
		_thisController.openContextMenu(false,newID);
	}
	this.callbackNewCut = function(segmentpoints) {
		var newID = "created" + _newPathCounter;
		_newPathCounter++;

		var actionAdd = new ActionAddCut(newID, segmentpoints,
				_editor, _settings, _currentPage);

		addAndExecuteAction(actionAdd);
	}
	this.changeRegionType = function(id, type){
		var polygonType = getPolygonMainType(id);
		if(polygonType === "region"){
			var regionPolygon = getRegionByID(id);
			if(regionPolygon.type != type){
				var actionChangeType = new ActionChangeTypeRegionPolygon(regionPolygon, type, _editor, _settings, _currentPage,_thisController);
				addAndExecuteAction(actionChangeType);
			}
			_thisController.hideRegion(type,false);
		}else if(polygonType === "segment" || polygonType === "fixed"){
			// is Segment
			_thisController.changeSegmentType(id,type);
		}
	}

	this.changeSegmentType = function(id, type){
		var polygonType = getPolygonMainType(id);
		if(polygonType === "result"){
			if(_segmentation.pages[_currentPage].segments[id].type != type){
				var actionChangeType = new ActionChangeTypeSegment(id, type, _editor, _segmentation, _currentPage);
				addAndExecuteAction(actionChangeType);
			}
		}else if(polygonType === "fixed"){
			//segment is fixed segment not result segment
			if(_settings.pages[_currentPage].segments[id].type != type){
				var actionChangeType = new ActionChangeTypeSegment(id, type, _editor, _settings, _currentPage);
				addAndExecuteAction(actionChangeType);
			}
		}

	}

	this.openRegionSettings = function(regionType,doCreate){
		var region = _settings.regions[regionType];
		if(region == null){
			region = _settings.regions['paragraph']; //TODO replace, is to fixed
		}
		_gui.openRegionSettings(regionType,region.minSize,region.maxOccurances,region.priorityPosition,doCreate);
	}

	// Display
	this.selectSegment = function(sectionID, info) {
		var currentType = (info === null) ? "segment" : info.type;

		if (!this.selectmultiple || currentType !== _selectType) {
			for (var i = 0, selectedsize = _selected.length; i < selectedsize; i++) {
				_editor.selectSegment(_selected[i], false);
			}
			_selected = [];
		}
		_selectType = currentType;
		_editor.selectSegment(sectionID, true);
		_selected.push(sectionID);

		var selectedSegments = [];

		for (var i = 0, selectedsize = _selected.length; i < selectedsize; i++) {
			if (info === null || info.type === "segment") {
				var segment = _segmentation.pages[_currentPage].segments[_selected[i]];
				if(segment == null){
					//is fixed segment
					segment = _settings.pages[_currentPage].segments[_selected[i]];
				}
				selectedSegments.push(segment);
			} else if (info !== null && info.type === "region") {
				var region = getRegionByID(_selected[i]);
				if(region != null){
					selectedSegments.push(region);
				}
			} else if (info !== null && info.type === "line") {
				var line = _settings.pages[_currentPage].cuts[_selected[i]];
				if(line != null){
					selectedSegments.push(line);
				}
			}
		}

		_gui.displaySelected(selectedSegments);
	}
	this.unSelect = function(){
		for (var i = 0, selectedsize = _selected.length; i < selectedsize; i++) {
			_editor.selectSegment(_selected[i], false);
		}
		_selected = [];
	}
	this.toggleSegment = function(sectionID, isSelected, info) {
		_editor.selectSegment(sectionID, isSelected);
	}
	this.enterSegment = function(sectionID, info) {
		_editor.highlightSegment(sectionID, true);
	}
	this.leaveSegment = function(sectionID, info) {
		_editor.highlightSegment(sectionID, false);
	}
	this.hideAllRegions = function(doHide){
		// Iterate over Regions-"Map" (Object in JS)
		Object.keys(_settings.regions).forEach(function(key) {
			var region = _settings.regions[key];

			// Iterate over all Polygons in Region
			Object.keys(region.polygons).forEach(function(polygonKey) {
				var polygon = region.polygons[polygonKey];
				_editor.hideSegment(polygon.id,doHide);
			});

			_visibleRegions[region.type] = !doHide;
		});
	}
	this.hideRegion = function(regionType, doHide){
		_visibleRegions[regionType] = !doHide;

		var region = _settings.regions[regionType];
		// Iterate over all Polygons in Region
		Object.keys(region.polygons).forEach(function(polygonKey) {
			var polygon = region.polygons[polygonKey];
			_editor.hideSegment(polygon.id,doHide);
		});
		_gui.forceUpdateRegionHide(_visibleRegions);
	}
	this.changeRegionSettings = function(regionType,minSize, maxOccurances){
		var region = _settings.regions[regionType];
		//create Region if not present
		if(region == null){
			region = {};
			region.type = regionType;
			region.polygons = {};
			_settings.regions[regionType] = region;
			_presentRegions.push(regionType);
			_gui.showUsedRegionLegends(_presentRegions);
		}
		region.minSize = minSize;
		region.maxOccurances = maxOccurances;
	}
	this.deleteRegionSettings = function(regionType){
		if($.inArray(regionType, _presentRegions) > 0){
			addAndExecuteAction(new ActionRemoveCompleteRegion(regionType,_thisController,_editor,_settings,_thisController));
		}
	}
	this.showPreloader = function(doShow){
		if(doShow){
			$('#preloader').removeClass('hide');
		}else{
			$('#preloader').addClass('hide');
		}
	}
	this.moveImage = function(delta){
		_editor.movePoint(delta);
	}
	this.openContextMenu = function(doSelected,id){
		if(doSelected && _selected != null && _selected.length > 0 && _selectType != 'cut'){
			_gui.openContextMenu(doSelected, id);
		} else {
			var polygonType = getPolygonMainType(id);
			if(polygonType != null && polygonType != 'cut'){
				_gui.openContextMenu(doSelected, id);
			}
		}
	}
	this.closeContextMenu = function(){
		_gui.closeContextMenu();
	}
	this.escape = function(){
			_thisController.unSelect();
			_thisController.endEditing();
			_thisController.closeContextMenu();
			_gui.closeRegionSettings();
	}

	var idIsFromRegion = function(regionID){
		var region = getRegionByID(regionID);
		if(region == null){
			return false;
		}else{
			return true;
		}
	}

	var addAndExecuteAction = function(action) {
		// Remove old undone actions
		if (_actions.length > 0)
			_actions = _actions.slice(0, _actionpointer + 1);

		// Execute and add new Action
		action.execute();
		_actions.push(action);
		_actionpointer++;
	}

	var getRegionByID = function(id){
		var regionPolygon;
		Object.keys(_settings.regions).some(function(key) {
			var region = _settings.regions[key];

			var polygon = region.polygons[id];
			if(!(polygon == null || polygon == undefined)){
				regionPolygon = polygon;
				return true;
			}
		});
		return regionPolygon;
	}

	var getPolygonMainType = function(polygonID){
		var polygon = _segmentation.pages[_currentPage].segments[polygonID];
		if(polygon != null){
			return "result";
		}

		polygon = _settings.pages[_currentPage].segments[polygonID];
		if(polygon != null){
			return "fixed";
		}

		polygon = getRegionByID(polygonID);
		if(polygon != null){
			return "region";
		}

		polygon = _settings.pages[_currentPage].cuts[polygonID];
		if(polygon != null){
			return "cut";
		}
	}

	//TODO replace
	var resizeViewerHeight = function(){
		$canvas = $("canvas");
		$sidebars = $('.sidebar');
		var height = $(window).height() - $canvas.offset().top;

		$canvas.height(height);
		$sidebars.height(height);
	}
}
