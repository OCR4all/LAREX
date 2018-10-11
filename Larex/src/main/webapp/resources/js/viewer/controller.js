var PageStatus = {TODO:'statusTodo',SESSIONSAVED:'statusSession',SERVERSAVED:'statusServer',UNSAVED:'statusUnsaved'}

function Controller(bookID, canvasID, regionColors, colors, globalSettings) {
	const _actionController = new ActionController(this);
	const _communicator = new Communicator();
	const _colors = new Colors(colors,regionColors);
	let _selector;
	let _gui;
	let _guiInput;
	let _editor;
	let _currentPage;
	let _segmentedPages = [];
	let _savedPages = [];
	let _book;
	let _segmentation = {};
	let _settings;
	let _contours = {};
	let _presentRegions = [];
	let _pageXMLVersion = "2017-07-15";
	let _displayReadingOrder = false;
	let _tempReadingOrder = null;
	let _allowLoadLocal = true;
	let _visibleRegions = {}; // !_visibleRegions.contains(x) and _visibleRegions[x] == false => x is hidden
	let _fixedSegments = {};
	let _editReadingOrder = false;

	let _newPolygonCounter = 0;

	// main method
	$(window).ready(() => {
		// Init PaperJS
		paper.setup(document.getElementById(canvasID));

		//set height before data is loaded //TODO rework
		$canvas = $("canvas");
		$sidebars = $('.sidebar');
		const height = $(window).height() - $canvas.offset().top;
		$canvas.height(height);
		$sidebars.height(height);

		_currentPage = 0;
		this.showPreloader(true);
		_communicator.loadBook(bookID, _currentPage).done((data) => {
			_book = data.book;
			_settings = data.settings;

			const regions = _settings.regions;
			Object.keys(regions).forEach((key) => {
				const region = regions[key];

				if (region.type !== 'ignore' && $.inArray(region.type, _presentRegions) < 0) {
					_presentRegions.push(region.type);
				}
			});
			// Init the viewer
			const viewerInput = new ViewerInput(this);

			// Inheritance Editor extends Viewer
			_editor = new Editor(viewerInput, _colors, this);

			_selector = new Selector(_editor, this);
			_gui = new GUI(canvasID, _editor,_colors,data.regionTypes);
			_gui.resizeViewerHeight();

			_gui.setParameters(_settings.parameters, _settings.imageSegType, _settings.combine);
			_gui.setRegionLegendColors();
			_gui.loadVisiblePreviewImages();
			_gui.highlightSegmentedPages(_segmentedPages);

			_gui.setPageXMLVersion(_pageXMLVersion);

			_gui.setAllRegionColors();
			_gui.updateAvailableColors();
			_gui.addPreviewImageListener();

			const navigationController = new NavigationController(_gui,_editor);
			// setup paper again because of pre-resize bugcallbackNewFixedSegment
			// (streched)
			paper.setup(document.getElementById(canvasID));


			// Init inputs
			const keyInput = new KeyInput(navigationController,
				this, _gui, _selector);
			$("#" + canvasID).mouseover(() => keyInput.isActive = true);
			$("#" + canvasID).mouseleave(() => keyInput.isActive = false);
			_guiInput = new GuiInput(navigationController, this, _gui);

			this.showPreloader(false);
			this.displayPage(0);

			// on resize
			$(window).resize(() => _gui.resizeViewerHeight());


			// init Search
			$(document).ready(function(){
				let pageData = {};
				_book.pages.forEach(page => pageData[page.image] = null );
			});
		});
	});

	this.displayPage = function (pageNr) {
		_currentPage = pageNr;

		this.showPreloader(true);

		if (_segmentedPages.indexOf(_currentPage) < 0 && _savedPages.indexOf(_currentPage) < 0) {
			this._requestSegmentation(_currentPage, _allowLoadLocal);
		} else {
			const imageId = _book.pages[_currentPage].id + "image";
			// Check if image is loadedreadingOrder
			const image = $('#' + imageId);
			if (!image[0]) {
				_communicator.loadImage(_book.pages[_currentPage].image, imageId).done(() => this.displayPage(pageNr));
				return false;
			}
			if (!image[0].complete) {
				// break until image is loaded
				image.load(() => this.displayPage(pageNr));
				return false;
			}

			_editor.clear();
			_editor.setImage(imageId);

			const pageSegments = _segmentation[_currentPage] ? _segmentation[_currentPage].segments : null;

			if (pageSegments) {
				// Iterate over Segment-"Map" (Object in JS)
				Object.keys(pageSegments).forEach((key) => {
					_editor.addSegment(pageSegments[key], this.isSegmentFixed(key));
				});
			}

			const regions = _settings.regions;
			// Iterate over Regions-"Map" (Object in JS)
			Object.keys(regions).forEach((key) => {
				const region = regions[key];

				// Iterate over all Polygons in Region
				Object.keys(region.polygons).forEach((polygonKey) => {
					let polygon = region.polygons[polygonKey];
					_editor.addRegion(polygon);

					if (!_visibleRegions[region.type] & region.type !== 'ignore') {
						_editor.hideSegment(polygon.id, true);
					}
				});

				if (region.type !== 'ignore' && $.inArray(region.type, _presentRegions) < 0) {
					//_presentRegions does not contains region.type
					_presentRegions.push(region.type);
				}
			});

			const pageCuts = _settings.pages[_currentPage].cuts;
			// Iterate over FixedSegment-"Map" (Object in JS)
			Object.keys(pageCuts).forEach((key) => _editor.addLine(pageCuts[key]));
			_editor.center();
			_editor.zoomFit();

			_gui.updateZoom();
			_gui.showUsedRegionLegends(_presentRegions);
			_gui.setReadingOrder(_segmentation[_currentPage].readingOrder, _segmentation[_currentPage].segments);
			_guiInput.addDynamicListeners();
			this.displayReadingOrder(_displayReadingOrder);
			_gui.setRegionLegendColors();

			_gui.selectPage(pageNr);
			_tempReadingOrder = null;
			this.endCreateReadingOrder();
			this.showPreloader(false);
		}
	}

	this.redo = function () {
		_actionController.redo(_currentPage);
	}
	this.undo = function () {
		_actionController.undo(_currentPage);
	}

	this.addPresentRegions = function (regionType) {
		if (regionType !== 'ignore' && $.inArray(regionType, _presentRegions) < 0) {
			//_presentRegions does not contains region.type
			_presentRegions.push(regionType);
		}
		_gui.showUsedRegionLegends(_presentRegions);
	}
	this.removePresentRegions = function (regionType) {
		_presentRegions = jQuery.grep(_presentRegions, (value) => value != regionType);
		_gui.showUsedRegionLegends(_presentRegions);
	}

	// New Segmentation with different Settings
	this.doSegmentation = function (pageID) {
		_settings.parameters = _gui.getParameters();

		_segmentedPages = _savedPages.slice(0); //clone saved Pages

		this._requestSegmentation(pageID, false);
	}

	this.loadExistingSegmentation = function () {
		_settings.parameters = _gui.getParameters();

		_segmentedPages = _savedPages.slice(0); //clone saved Pages

		this._requestSegmentation(_currentPage, true);
	}

	this.uploadExistingSegmentation = function (file) {
		_segmentedPages = _savedPages.slice(0); //clone saved Pages
		this._uploadSegmentation(file, _currentPage);
	}

	this._requestSegmentation = function (pageID, allowLoadLocal) {
		if (!pageID) {
			pageID = _currentPage;
		}

		//Add fixed Segments to settings
		const activesettings = JSON.parse(JSON.stringify(_settings));
		activesettings.pages[pageID].segments = {};
		if (!_fixedSegments[pageID]) _fixedSegments[pageID] = [];
		_fixedSegments[pageID].forEach(s => activesettings.pages[pageID].segments[s] = _segmentation[pageID].segments[s]);

		_communicator.segmentBook(activesettings, pageID, allowLoadLocal).done((result) => {
			const failedSegmentations = [];
			const missingRegions = [];

			_gui.highlightLoadedPage(pageID, false);
			switch (result.status) {
				case 'LOADED':
					_gui.highlightLoadedPage(pageID, true);
				case 'SUCCESS':
					_segmentation[pageID] = result;

					_actionController.resetActions(pageID);
					//check if all necessary regions are available
					Object.keys(result.segments).forEach((id) => {
						let segment = result.segments[id];
						if ($.inArray(segment.type, _presentRegions) == -1) {
							//TODO as Action
							this.changeRegionSettings(segment.type, 0, -1);
							const colorID = _colors.getColorID(_colors.getColor(segment.type));
							this.setRegionColor(segment.type,colorID);
							missingRegions.push(segment.type);
						}
					});
					let readingOrder = [];
					result.readingOrder.forEach((id) => readingOrder.push(id));
					_actionController.addAndExecuteAction(new ActionChangeReadingOrder(_segmentation[pageID].readingOrder, readingOrder, this, _segmentation, pageID), pageID);
					break;
				default:
					failedSegmentations.push(pageID);
			}

			_segmentedPages.push(pageID);
			if (missingRegions.length > 0) {
				_gui.displayWarning('Warning: Some regions were missing and have been added.');
			}

			this.displayPage(pageID);
			_gui.highlightSegmentedPages(_segmentedPages);

			_communicator.getSegmented(_book.id).done((pages) =>{
				pages.forEach(page => { _gui.addPageStatus(page,PageStatus.SERVERSAVED)});
			})

		});
	}

	this._uploadSegmentation = function (file, pageNr) {
		this.showPreloader(true);
		if (!pageNr) {
			pageNr = _currentPage;
		}
		_communicator.uploadPageXML(file, pageNr, _book.id).done((page) => {
			const failedSegmentations = [];
			const missingRegions = [];

			switch (page.status) {
				case 'SUCCESS':
					_segmentation[pageNr] = page;

					_actionController.resetActions(pageNr);

					//check if all necessary regions are available
					Object.keys(page.segments).forEach((id) => {
						let segment = page.segments[id];
						if ($.inArray(segment.type, _presentRegions) == -1) {
							//TODO as Action
							this.changeRegionSettings(segment.type, 0, -1);
							const colorID = _colors.getColorID(_colors.getColor(segment.type));
							this.setRegionColor(segment.type,colorID);
							missingRegions.push(segment.type);
						}
					});
					let readingOrder = [];

					page.readingOrder.forEach((id) => readingOrder.push(id));
					_actionController.addAndExecuteAction(
						new ActionChangeReadingOrder(_segmentation[pageNr].readingOrder, readingOrder, this, _segmentation, pageNr)
						, pageNr);
					break;
				default:
					failedSegmentations.push(pageNr);
			}

			_segmentedPages.push(pageNr);
			if (missingRegions.length > 0) {
				_gui.displayWarning('Warning: Some regions were missing and have been added.');
			}

			this.displayPage(pageNr);
			this.showPreloader(false);
			_gui.highlightSegmentedPages(_segmentedPages);
		});
	}
	this.setPageXMLVersion = function (pageXMLVersion) {
		_pageXMLVersion = pageXMLVersion;
	}

	this.exportPageXML = function () {
		_gui.setExportingInProgress(true);

		_communicator.exportSegmentation(_segmentation[_currentPage], _book.id, _pageXMLVersion).done((data) => {
			// Set export finished
			_savedPages.push(_currentPage);
			_gui.setExportingInProgress(false);
			_gui.addPageStatus(_currentPage,PageStatus.SESSIONSAVED);

			// Download
			if (globalSettings.downloadPage) {
				var a = window.document.createElement('a');
				a.href = window.URL.createObjectURL(new Blob([new XMLSerializer().serializeToString(data)], { type: "text/xml;charset=utf-8" }));
				const fileName = _book.pages[_currentPage].fileName;
				a.download = _book.name + "_" + fileName.substring(0, fileName.lastIndexOf(".")) + ".xml";

				// Append anchor to body.
				document.body.appendChild(a);
				a.click();
			}
		});
	}

	this.setChanged = function(pageID){
		_gui.addPageStatus(pageID,PageStatus.UNSAVED);
	}

	this.saveSettingsXML = function () {
		_gui.setSaveSettingsInProgress(true);
		_settings.parameters = _gui.getParameters();
		_communicator.exportSettings(_settings).done((data) => {
			var a = window.document.createElement('a');
			a.href = window.URL.createObjectURL(new Blob([new XMLSerializer().serializeToString(data)], { type: "text/xml;charset=utf-8" }));
			a.download = "settings_" + _book.name + ".xml";

			// Append anchor to body.
			document.body.appendChild(a);
			a.click();

			// Remove anchor from body
			document.body.removeChild(a);

			_gui.setSaveSettingsInProgress(false);
		});
	}

	this.uploadSettings = function (file) {
		_communicator.uploadSettings(file, _book.id).done((settings) => {
			if (settings) {
				_settings = settings;
				_presentRegions = [];
				Object.keys(_settings.regions).forEach((regionType) => {
					if (regionType !== 'ignore') {
						_presentRegions.push(regionType);
					}
				});
				_gui.showUsedRegionLegends(_presentRegions);
				_gui.setParameters(_settings.parameters, _settings.imageSegType, _settings.combine);

				this.displayPage(_currentPage);
				this.hideAllRegions(true);
				_gui.forceUpdateRegionHide(_visibleRegions);
				_actionController.resetActions(_currentPage);
			}
		});
	}

	this.fixSegment = function (id, doFix = true) {
		if (!_fixedSegments[_currentPage]) _fixedSegments[_currentPage] = [];
		let arrayPosition = $.inArray(id, _fixedSegments[_currentPage]);
		if (doFix && arrayPosition < 0) {
			_fixedSegments[_currentPage].push(id)
		} else if (!doFix && arrayPosition > -1) {
			//remove from _fixedSegments
			_fixedSegments[_currentPage].splice(arrayPosition, 1);
		}
		_editor.fixSegment(id, doFix);
	}

	this.createSegmentPolygon = function () {
		this.endEditing();
		_editor.startCreatePolygon('segment');
		_gui.selectToolBarButton('segmentPolygon', true);
	}
	this.createRectangle = function (type) {
		this.endEditing();

		_editor.createRectangle(type);
		switch (type) {
			case 'segment':
				_gui.selectToolBarButton('segmentRectangle', true);
				break;
			case 'region':
				_gui.selectToolBarButton('regionRectangle', true);
				break;
			case 'ignore':
				_gui.selectToolBarButton('ignore', true);
				break;
			case 'roi':
				_gui.selectToolBarButton('roi', true);
				break;
		}
	}

	this.createCut = function () {
		this.endEditing();
		_editor.startCreateLine();
		_gui.selectToolBarButton('cut', true);
	}

	this.fixSelected = function () {
		if (_selector.selectedType === 'segment') {
			const actions = [];
			const selected = _selector.getSelectedSegments();
			const selectType = _selector.getSelectedPolygonType();
			for (let i = 0, selectedlength = selected.length; i < selectedlength; i++) {
				if (selectType === "region") {
				} else if (selectType === "segment") {
					if (!_fixedSegments[_currentPage]) _fixedSegments[_currentPage] = [];
					let wasFixed = $.inArray(selected[i], _fixedSegments[_currentPage]) > -1;
					actions.push(new ActionFixSegment(selected[i], this, !wasFixed));
				} else if (selectType === "cut") {
				}
			}
			let multiFix = new ActionMultiple(actions);
			_actionController.addAndExecuteAction(multiFix, _currentPage);
			selected.forEach(s => _selector.select(s));
		}
	}

	this.moveSelectedPoints = function () {
		const selected = _selector.getSelectedSegments();
		const selectType = _selector.getSelectedPolygonType();
		let points = _selector.getSelectedPoints();

		if (points && points.length > 0 && selected.length === 1 && selectType === "segment") {
			_editor.startMovePolygonPoints(selected[0], 'segment', points);
		}
	}

	this.endEditing = function () {
		_editor.endEditing();
		_gui.unselectAllToolBarButtons();
	}

	this.deleteSelected = function () {
		const selected = _selector.getSelectedSegments();
		const points = _selector.getSelectedPoints();
		const selectType = _selector.getSelectedPolygonType();

		if (selected.length === 1 && points.length > 0) {
			// Points inside of a polygon is selected => Delete points
			if(selectType === 'segment'){
				const segments = _segmentation[_currentPage].segments[selected[0]].points;
				let filteredSegments = segments;

				points.forEach(p => { filteredSegments = filteredSegments.filter(s => !(s.x === p.x && s.y === p.y))});

				_actionController.addAndExecuteAction(new ActionTransformSegment(selected[0], filteredSegments, _editor, _segmentation, _currentPage, this), _currentPage);
			}
		}else{
			//Polygon is selected => Delete polygon
			const actions = [];
			for (let i = 0, selectedlength = selected.length; i < selectedlength; i++) {
				if (selectType === "region") {
					actions.push(new ActionRemoveRegion(this._getRegionByID(selected[i]), _editor, _settings, _currentPage, this));
				} else if (selectType === "segment") {
					let segment = _segmentation[_currentPage].segments[selected[i]];
					actions.push(new ActionRemoveSegment(segment, _editor, _segmentation, _currentPage, this));
				} else if (selectType === "cut") {
					let cut = _settings.pages[_currentPage].cuts[selected[i]];
					actions.push(new ActionRemoveCut(cut, _editor, _settings, _currentPage));
				}
			}
			let multidelete = new ActionMultiple(actions);
			_actionController.addAndExecuteAction(multidelete, _currentPage);
		} 
	}
	this.mergeSelectedSegments = function () {
		const selected = _selector.getSelectedSegments();
		const selectType = _selector.getSelectedPolygonType();
		if (selectType === 'segment' && selected.length > 1) {
			const actions = [];
			const segments = [];
			for (let i = 0, selectedlength = selected.length; i < selectedlength; i++) {
				if (selectType === "segment") {
					let segment = _segmentation[_currentPage].segments[selected[i]];
					//filter special case image (do not merge images)
					if (segment.type !== 'image') {
						segments.push(segment);
						actions.push(new ActionRemoveSegment(segment, _editor, _segmentation, _currentPage, this));
					}
				}
			}
			if (segments.length > 1) {
				_communicator.mergeSegments(segments, _currentPage, _book.id).done((data) => {
					const mergedSegment = data;
					actions.push(new ActionAddSegment(mergedSegment.id, mergedSegment.points, mergedSegment.type,
						_editor, _segmentation, _currentPage, this));

					let mergeAction = new ActionMultiple(actions);
					_actionController.addAndExecuteAction(mergeAction, _currentPage);
					this.selectSegment(mergedSegment.id);
					this.openContextMenu(true);
				});
			}
		}
	}
	this.changeTypeSelected = function (newType) {
		const selected = _selector.getSelectedSegments();
		const selectType = _selector.getSelectedPolygonType();
		const selectedlength = selected.length;
		if (selectedlength || selectedlength > 0) {
			const actions = [];
			for (let i = 0; i < selectedlength; i++) {
				if (selectType === "region") {
					const regionPolygon = this._getRegionByID(selected[i]);
					actions.push(new ActionChangeTypeRegionPolygon(regionPolygon, newType, _editor, _settings, _currentPage, this));

					this.hideRegion(newType, false);
				} else if (selectType === "segment") {
					actions.push(new ActionChangeTypeSegment(selected[i], newType, _editor, this, _segmentation, _currentPage, false));
				}
			}
			const multiChange = new ActionMultiple(actions);
			_actionController.addAndExecuteAction(multiChange, _currentPage);
		}
	}
	this.createRegionBorder = function () {
		this.endEditing();
		_editor.startCreateBorder('region');
		_gui.selectToolBarButton('regionBorder', true);
	}
	this.callbackNewRegion = function (regionpoints, regiontype) {
		const newID = "created" + _newPolygonCounter;
		_newPolygonCounter++;
		if (!regiontype) {
			type = _presentRegions[0];
			if (!type) {
				type = "other";
			}
		} else {
			type = regiontype;
		}

		const actionAdd = new ActionAddRegion(newID, regionpoints, type,
			_editor, _settings, _currentPage);

		_actionController.addAndExecuteAction(actionAdd, _currentPage);
		if (!regiontype) {
			this.openContextMenu(false, newID);
		}
		_gui.unselectAllToolBarButtons();
	}

	this.callbackNewRoI = function (regionpoints) {
		let left = 1;
		let right = 0;
		let top = 1;
		let down = 0;

		$.each(regionpoints, function (index, point) {
			if (point.x < left)
				left = point.x;
			if (point.x > right)
				right = point.x;
			if (point.y < top)
				top = point.y;
			if (point.y > down)
				down = point.y;
		});

		const actions = [];

		//Create 'inverted' ignore rectangle
		actions.push(new ActionAddRegion("created" + _newPolygonCounter, [{ x: 0, y: 0 }, { x: 1, y: 0 }, { x: 1, y: top }, { x: 0, y: top }], 'ignore',
			_editor, _settings, _currentPage));
		_newPolygonCounter++;

		actions.push(new ActionAddRegion("created" + _newPolygonCounter, [{ x: 0, y: 0 }, { x: left, y: 0 }, { x: left, y: 1 }, { x: 0, y: 1 }], 'ignore',
			_editor, _settings, _currentPage));
		_newPolygonCounter++;

		actions.push(new ActionAddRegion("created" + _newPolygonCounter, [{ x: 0, y: down }, { x: 1, y: down }, { x: 1, y: 1 }, { x: 0, y: 1 }], 'ignore',
			_editor, _settings, _currentPage));
		_newPolygonCounter++;

		actions.push(new ActionAddRegion("created" + _newPolygonCounter, [{ x: right, y: 0 }, { x: 1, y: 0 }, { x: 1, y: 1 }, { x: right, y: 1 }], 'ignore',
			_editor, _settings, _currentPage));
		_newPolygonCounter++;

		_actionController.addAndExecuteAction(new ActionMultiple(actions), _currentPage);
		_gui.unselectAllToolBarButtons();
	}

	this.callbackNewSegment = function (segmentpoints) {
		const newID = "created" + _newPolygonCounter;
		_newPolygonCounter++;
		let type = _presentRegions[0];
		if (!type) {
			type = "other";
		}
		const actionAdd = new ActionAddSegment(newID, segmentpoints, type,
			_editor, _segmentation, _currentPage, this);

		_actionController.addAndExecuteAction(actionAdd, _currentPage);
		this.openContextMenu(false, newID);
		_gui.unselectAllToolBarButtons();
	}
	this.callbackNewCut = function (segmentpoints) {
		const newID = "created" + _newPolygonCounter;
		_newPolygonCounter++;

		const actionAdd = new ActionAddCut(newID, segmentpoints,
			_editor, _settings, _currentPage);

		_actionController.addAndExecuteAction(actionAdd, _currentPage);
		_gui.unselectAllToolBarButtons();
	}

	this.movePolygonPoints = function (id, segmentPoints) {
		this.transformSegment(id,segmentPoints);
		_selector.unSelect();
		_selector.select(id);
	}

	this.transformSegment = function (id, segmentPoints) {
		const actionTransformSegment = new ActionTransformSegment(id, segmentPoints, _editor, _segmentation, _currentPage, this);
		_actionController.addAndExecuteAction(actionTransformSegment, _currentPage);
	}

	this.scaleSelectedRegion = function () {
		const selectType = _selector.getSelectedPolygonType();
		const selected = _selector.getSelectedSegments();

		if (selectType === 'region' && selected.length === 1) 
			_editor.startScalePolygon(selected[0], 'region');
	}

	this.transformRegion = function (regionID, regionSegments) {
		const polygonType = this.getIDType(regionID);
		if (polygonType === "region") {
			let regionType = this._getRegionByID(regionID).type;
			let actionTransformRegion = new ActionTransformRegion(regionID, regionSegments, regionType, _editor, _settings, _currentPage, this);
			_actionController.addAndExecuteAction(actionTransformRegion, _currentPage);
			this.hideRegion(regionType, false);
		}
	}

	this.changeRegionType = function (id, type) {
		const polygonType = this.getIDType(id);
		if (polygonType === "region") {
			const regionPolygon = this._getRegionByID(id);
			if (regionPolygon.type != type) {
				let actionChangeType = new ActionChangeTypeRegionPolygon(regionPolygon, type, _editor, _settings, _currentPage, this);
				_actionController.addAndExecuteAction(actionChangeType, _currentPage);
			}
			this.hideRegion(type, false);
		} else if (polygonType === "segment" || polygonType === "fixed") {
			if (_segmentation[_currentPage].segments[id].type != type) {
				const actionChangeType = new ActionChangeTypeSegment(id, type, _editor, this, _segmentation, _currentPage, false);
				_actionController.addAndExecuteAction(actionChangeType, _currentPage);
			}
		}
	}

	this.openRegionSettings = function (regionType, doCreate) {
		let region = _settings.regions[regionType];
		if (!region) {
			region = _settings.regions['paragraph']; //TODO replace, is to fixed
		}
		const colorID = _colors.getColorID(_colors.getColor(regionType));

		_gui.openRegionSettings(regionType, region.minSize, region.maxOccurances, region.priorityPosition, doCreate, colorID);
	}

	this.setRegionColor = function (regionType, colorID) {
		_colors.setColor(regionType, colorID);
		_gui.updateAvailableColors();

		const pageSegments = _segmentation[_currentPage].segments;
		Object.keys(pageSegments).forEach((key) => {
				let segment = pageSegments[key];
				if (segment.type === regionType) {
					_editor.updateSegment(segment);
				}
		});
		
		const region = _settings.regions[regionType];
		// Iterate over all Polygons in Region
		Object.keys(region.polygons).forEach((polygonKey) => {
			let polygon = region.polygons[polygonKey];
			if (polygon.type === regionType) {
				_editor.updateSegment(polygon);
			}
		});
		_gui.setRegionLegendColors();
	}

	this.autoGenerateReadingOrder = function () {
		this.endCreateReadingOrder();
		let readingOrder = [];
		const pageSegments = _segmentation[_currentPage].segments;

		// Iterate over Segment-"Map" (Object in JS)
		Object.keys(pageSegments).forEach((key) => {
			let segment = pageSegments[key];
			if (segment.type !== 'image') {
				readingOrder.push(segment.id);
			}
		});
		readingOrder = _editor.getSortedReadingOrder(readingOrder);
		_actionController.addAndExecuteAction(new ActionChangeReadingOrder(_segmentation[_currentPage].readingOrder, readingOrder, this, _segmentation, _currentPage), _currentPage);
	}

	this.createReadingOrder = function () {
		_actionController.addAndExecuteAction(new ActionChangeReadingOrder(_segmentation[_currentPage].readingOrder, [], this, _segmentation, _currentPage), _currentPage);
		_editReadingOrder = true;
		_gui.doEditReadingOrder(true);
	}

	this.endCreateReadingOrder = function () {
		_editReadingOrder = false;
		_gui.doEditReadingOrder(false);
	}

	this.setBeforeInReadingOrder = function (segment1ID, segment2ID, doUpdate) {
		if (!_tempReadingOrder) {
			_tempReadingOrder = JSON.parse(JSON.stringify(_segmentation[_currentPage].readingOrder));
		}

		let readingOrder = _tempReadingOrder;
		let index1;
		let segment1;
		let segment2;
		for (let index = 0; index < readingOrder.length; index++) {
			const currentSegmentID = readingOrder[index];
			if (currentSegmentID === segment1ID) {
				index1 = index;
				segment1ID = currentSegmentID;
			} else if (currentSegmentID === segment2ID) {
				segment2ID = currentSegmentID;
			}
		}
		readingOrder.splice(index1, 1);
		readingOrder.splice(readingOrder.indexOf(segment2ID), 0, segment1ID);
		if (doUpdate) {
			_gui.setBeforeInReadingOrder(segment1ID, segment2ID);

			_actionController.addAndExecuteAction(new ActionChangeReadingOrder(_segmentation[_currentPage].readingOrder, _tempReadingOrder, this, _segmentation, _currentPage), _currentPage);
		}
		this.displayReadingOrder(_displayReadingOrder, true);
	}

	this.displayReadingOrder = function (doDisplay, doUseTempReadingOrder) {
		_displayReadingOrder = doDisplay;
		if (doDisplay) {
			const readingOrder = doUseTempReadingOrder ? _tempReadingOrder : _segmentation[_currentPage].readingOrder;
			_editor.displayReadingOrder(readingOrder);

		} else {
			_editor.hideReadingOrder();
		}
		_gui.displayReadingOrder(doDisplay);
	}

	this.forceUpdateReadingOrder = function (forceHard) {
		_gui.forceUpdateReadingOrder(_segmentation[_currentPage].readingOrder, forceHard, _segmentation[_currentPage].segments);
		_gui.setRegionLegendColors();
		_guiInput.addDynamicListeners();
		this.displayReadingOrder(_displayReadingOrder);
	}

	this.removeFromReadingOrder = function (id) {
		_actionController.addAndExecuteAction(new ActionRemoveFromReadingOrder(id, _currentPage, _segmentation, this), _currentPage);
	}

	this.changeImageMode = function (imageMode) {
		_settings.imageSegType = imageMode;
	}

	this.changeImageCombine = function (doCombine) {
		_settings.combine = doCombine;
	}

	this.applyGrid = function () {
		_editor.addGrid();
	}

	this.removeGrid = function () {
		_editor.removeGrid();
	}

	this._readingOrderContains = function (id) {
		const readingOrder = _segmentation[_currentPage].readingOrder;
		for (let i = 0; i < readingOrder.length; i++) {
			if (readingOrder[i] === id) {
				return true;
			}
		}
		return false;
	}
	// Display
	this.selectSegment = function (sectionID, hitTest) {
		const idType = this.getIDType(sectionID);

		if (_editReadingOrder && idType === 'segment') {
			const segment = this._getPolygon(sectionID);
			if (!this._readingOrderContains(sectionID)) {
				_actionController.addAndExecuteAction(new ActionAddToReadingOrder(segment, _currentPage, _segmentation, this), _currentPage);
			}
		} else {
			let points;
			if (hitTest && hitTest.type == 'segment') {
				const nearestPoint = hitTest.segment.point;
				points = [_editor._convertCanvasToGlobal(nearestPoint.x, nearestPoint.y)];
				_selector.select(sectionID, points);
			}else{
				_selector.select(sectionID);
			}

			this.closeContextMenu();
		}
	}

	this.hasPointsSelected = function() {
		return _selector.getSelectedPolygonType() === 'segment' && _selector.getSelectedSegments().length === 1 && _selector.getSelectedPoints().length > 0;
	}

	this.unSelect = function () {
		_selector.unSelect();
	}
	this.unSelectSegment = function (segmentID) {
		_selector.unSelectSegment(segmentID);
	}
	this.isSegmentSelected = function (id) {
		return _selector.isSegmentSelected(id);
	}
	this.boxSelect = function () {
		_selector.boxSelect();
	}
	this.enterSegment = function (sectionID) {
		if (!_editor.isEditing) {
			_editor.highlightSegment(sectionID, true);
			_gui.highlightSegment(sectionID, true);
		}
	}
	this.leaveSegment = function (sectionID) {
		if (!_editor.isEditing) {
			_editor.highlightSegment(sectionID, false);
			_gui.highlightSegment(sectionID, false);
		}
	}
	this.hideAllRegions = function (doHide) {
		// Iterate over Regions-"Map" (Object in JS)
		Object.keys(_settings.regions).forEach((key) => {
			const region = _settings.regions[key];
			if (region.type !== 'ignore') {
				// Iterate over all Polygons in Region
				Object.keys(region.polygons).forEach((polygonKey) => {
					let polygon = region.polygons[polygonKey];
					_editor.hideSegment(polygon.id, doHide);
				});

				_visibleRegions[region.type] = !doHide;
			}
		});
	}
	this.hideRegion = function (regionType, doHide) {
		_visibleRegions[regionType] = !doHide;

		const region = _settings.regions[regionType];
		// Iterate over all Polygons in Region
		Object.keys(region.polygons).forEach((polygonKey) => {
			let polygon = region.polygons[polygonKey];
			_editor.hideSegment(polygon.id, doHide);
		});
		_gui.forceUpdateRegionHide(_visibleRegions);
	}

	this.selectContours = function() {
		this.endEditing();
		_gui.selectToolBarButton('segmentContours',true);
		if(!_contours[_currentPage]){
			this.showPreloader(true);
			_editor.startEditing();
			_communicator.extractContours(_currentPage,_book.id).done((result) => {
				_contours[_currentPage] = result; 
				this.showPreloader(false);
				this.endEditing();
				_editor.selectContours(_contours[_currentPage]);
			});
		}else{
			_editor.selectContours(_contours[_currentPage]);
		}
	}
	this.combineContours = function(contours){
		if(contours.length > 0){
			_communicator.combineContours(contours,_currentPage,_book.id).done((segment) => {
				const action = new ActionAddSegment(segment.id, segment.points, segment.type,
					_editor, _segmentation, _currentPage, this);

				this.endEditing();

				_actionController.addAndExecuteAction(action, _currentPage);
				this.selectSegment(segment.id);
				this.openContextMenu(true);
			});
		}
		
	}

	this.changeRegionSettings = function (regionType, minSize, maxOccurances) {
		let region = _settings.regions[regionType];
		//create Region if not present
		if (!region) {
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
	this.deleteRegionSettings = function (regionType) {
		if ($.inArray(regionType, _presentRegions) >= 0 && regionType != 'image' && regionType != 'paragraph') {
			_actionController.addAndExecuteAction(new ActionRemoveCompleteRegion(regionType, this, _editor, _settings, this), _currentPage);
		}
	}
	this.showPreloader = function (doShow) {
		if (doShow) {
			$('#preloader').removeClass('hide');
		} else {
			$('#preloader').addClass('hide');
		}
	}
	this.moveImage = function (delta) {
		if (!_editor.isEditing) {
			_editor.movePoint(delta);
		}
	}
	this.openContextMenu = function (doSelected, id) {
		const selected = _selector.getSelectedSegments();
		const selectType = _selector.getSelectedPolygonType();
		if (doSelected && selected && selected.length > 0 && (selectType === 'region' || selectType === "segment")) {
			_gui.openContextMenu(doSelected, id);
		} else {
			let polygonType = this.getIDType(id);
			if (polygonType === 'region' || polygonType === "segment") {
				_gui.openContextMenu(doSelected, id);
			}
		}
	}
	this.closeContextMenu = function () {
		_gui.closeContextMenu();
	}
	this.escape = function () {
		_selector.unSelect();
		this.closeContextMenu();
		this.endEditing();
		_gui.closeRegionSettings();
	}
	this.allowToLoadExistingSegmentation = function (allowLoadLocal) {
		_allowLoadLocal = allowLoadLocal;
	}

	this.isSegmentFixed = function (id) {
		if (!_fixedSegments[_currentPage])
			_fixedSegments[_currentPage] = [];

		let isFixed = ($.inArray(id, _fixedSegments[_currentPage]) !== -1);
		return isFixed;
	}

	this._getRegionByID = function (id) {
		let regionPolygon;
		Object.keys(_settings.regions).some((key) => {
			let region = _settings.regions[key];

			let polygon = region.polygons[id];
			if (polygon) {
				regionPolygon = polygon;
				return true;
			}
		});
		return regionPolygon;
	}

	this.getIDType = function (id) {
		polygon = _segmentation[_currentPage].segments[id];
		if (polygon) return "segment";

		polygon = this._getRegionByID(id);
		if (polygon) return "region";

		polygon = _settings.pages[_currentPage].cuts[id];
		if (polygon) return "cut";
	}

	this._getPolygon = function (id) {
		let polygon = _segmentation[_currentPage].segments[id];
		if (polygon) return polygon;

		polygon = this._getRegionByID(id);
		if (polygon) return polygon;

		polygon = _settings.pages[_currentPage].cuts[id];
		if (polygon) return polygon;
	}
}