function GuiInput(navigationController, controller, gui){
	var _navigationController = navigationController;
	var _controller = controller;
	var _gui = gui;
	var _draggedObject = null;

	$(window).click(function() {
		//Cancel viewer actions, if outside of viewer or a menu icon
		$target = $(event.target);
		if(!$target.is('body') && !$target.is('#viewer') && !$target.parents("#viewer").is("#viewer")
		&& !$target.is('.infocus') && !$target.parents(".infocus").is(".infocus")){
			_controller.escape();
		}
	});

	// button registration
	$( "#viewer" ).contextmenu(function() {
		_controller.openContextMenu(true);
		return false; //prevents default contextmenu
	});
	$( "#viewer" ).dblclick(function() {
		_controller.endEditing();
	});
	$('.doSegment').click(function() {
		_controller.doSegmentation();
	});
	$('.exportPageXML').click(function() {
		_controller.exportPageXML();
	});
	$('.downloadPageXML').click(function() {
		_controller.downloadPageXML();
	});
	$('.pageXMLVersion').click(function() {
		var version = $(this).data(version).version;
		_gui.setPageXMLVersion(version);
		$('#dropDownPageXML').dropdown('close');
		_controller.setPageXMLVersion(version);
	});
	$('#dropDownPageXMLCorner').click(function(event) {
    event.stopPropagation();
		$('#dropDownPageXML').dropdown('open');
	});
	$('.saveSettingsXML').click(function() {
		_controller.saveSettingsXML();
	});
	$('.downloadSettingsXML').click(function() {
		_controller.downloadSettingsXML();
	});
	$(':file').on('change', function() {
    var file = this.files[0];
		$(this).val("");
		if(file){
			if (file.size < 1024*1024) {
				_controller.uploadSettings(file);
			}else{
				alert('max upload size is 1MB')
	    }
		}
  });
	$('.reload').click(function() {
		location.reload();
	});
	$('.settings-image-mode').on('change', function() {
		if(this.value){
			_controller.changeImageMode(this.value);
		}
	})

	$('.settings-combine-image').on('change', function() {
		var doCombine = $(this).find('input').prop('checked');
		if(doCombine !== undefined){
		  _controller.changeImageCombine(doCombine);
		}
	});
	$('.createRegionRectangle').click(function() {
		_controller.createRectangle('region');
	});

	$('.setRegionOfInterest').click(function() {
		_controller.createRectangle('roi');
	});

	$('.createIgnore').click(function() {
		_controller.createRectangle('ignore');
	});

	$('.createRegionBorder').click(function() {
		_controller.createBorder(false);
	});

	$('.createSegmentPolygon').click(function() {
		_controller.createPolygon(true);
	});

	$('.createSegmentRectangle').click(function() {
		_controller.createRectangle('segment');
	});

	$('.createSegmentBorder').click(function() {
		_controller.createBorder(true);
	});

	$('.createCut').click(function() {
		_controller.createCut();
	});

	$('.combineSelected').click(function() {
		_controller.mergeSelectedSegments();
	});

	$('.scaleSelected').click(function() {
		_controller.scaleSelected();
	});

	$('.moveSelected').click(function() {
		_controller.moveSelected();
	});

	$('.deleteSelected').click(function() {
		_controller.deleteSelected();
	});

	$('.editMode').click(function() {
		//TODO
		_controller.editLastSelected();
		//_inputhandler.selectEdit();//_viewer.startEdit();
	});

	$('.zoomin').click(function() {
		_navigationController.zoomIn(0.1);
	});

	$('.zoomout').click(function() {
		_navigationController.zoomOut(0.1);
	});

	$('.zoomfit').click(function() {
		_navigationController.zoomFit();
	});

	$('.moveright').click(function() {
		_navigationController.move(10, 0);
	});

	$('.moveleft').click(function() {
		_navigationController.move(-10, 0);
	});

	$('.movedown').click(function() {
		_navigationController.move(0, 10);
	});

	$('.moveup').click(function() {
		_navigationController.move(0, -10);
	});

	$('.movecenter').click(function() {
		_navigationController.center();
	});

	$('.movefree').click(function() {
		if(_gui.doMoveCanvas){
			_gui.moveCanvas(false);
		} else {
			_gui.moveCanvas(true);
		}
	});
	$('.undo').click(function() {
		_controller.undo();
	});
	$('.redo').click(function() {
		_controller.redo();
	});
	$('.deleteEdit').click(function() {
		_controller.deleteSelected();
		//TODO redirect to controller
		$("#segmentedit").addClass("hide");
	});
	$('.closeEdit').click(function() {
		//TODO redirect to controller
		$("#segmentedit").addClass("hide");
	});
	$('.chagePage').click(function() {
		  _controller.displayPage($(this).data("page"));
	});
	$('#selectTypes').on('change', function() {
		  _controller.changeTypeSelected(this.value);
	});
	$('.regionlegend').click(function() {
			var $this = $(this);
			var $switchBox = $this.find('input');
			_controller.hideRegion($this.data('type'), !$switchBox.prop('checked'));
	});
	$('.regionlegendAll').click(function() {
			var $switchBox = $(this).find('input');
			var $allSwitchBoxes = $('.regionlegend').find('input');
			$allSwitchBoxes.prop('checked',$switchBox.prop('checked'))

			_controller.hideAllRegions(!$switchBox.prop('checked'));
	});
	$('.regionSettings, #regioneditorSelect .collection-item').click(function() {
			_controller.openRegionSettings($(this).data("type"),false);
	});
	$('.regionCreate').click(function() {
			_controller.openRegionSettings($(this).data("type"),true);
	});
	$('.regionCancel').click(function() {
		_gui.closeRegionSettings();
	});
	$('.regionDelete').click(function() {
			var regionType = $('#regioneditor').find('#regionType').text();
			_controller.deleteRegionSettings(regionType);
			_gui.closeRegionSettings();
	});
	$('.contextTypeOption').click(function(){
			var $this = $(this);
			var $contextmenu = $("#contextmenu");
			var doSelected = $contextmenu.data('doSelected');
			var regionType = $this.data('type');

			if(doSelected){
				_controller.changeTypeSelected(regionType);
			}else{
				var polygonID = $contextmenu.data('polygonID');
				_controller.changeRegionType(polygonID,regionType);
			}
			_gui.closeContextMenu();
	});
	$('#regioneditorSave').click(function(){
			var $regioneditor = $('#regioneditor');

			var regionType = $regioneditor.find('#regionType').text();
			var minSize = $regioneditor.find('#regionMinSize').val();
			var maxOccurances = $regioneditor.find('#regionMaxOccurances').val();
			var color = $regioneditor.find('#regionColor').data('color');
			color = new paper.Color(color.red,color.green,color.blue);
			_controller.changeRegionSettings(regionType, minSize,maxOccurances);
			_controller.setRegionColor(regionType,_controller.getColorID(color));
			_gui.closeRegionSettings();
	});
	$('#regioneditorCancel').click(function(){
			_gui.closeRegionSettings();
	});
	$('#regioneditor #regionType').click(function(){
			var $regioneditor = $('#regioneditor');
			$('#regioneditorSelect').removeClass('hide');
	});
	$('#regioneditor #regionColor').click(function(){
			var $regioneditorColorSelect = $('#regioneditorColorSelect');
			if($regioneditorColorSelect.hasClass('hide')){
				$('#regioneditorColorSelect').removeClass('hide');
			}else{
				$('#regioneditorColorSelect').addClass('hide');
			}
	});

	$('.regioneditorColorSelectItem').click(function() {
			var color = $(this).data('color');
			color = new paper.Color(color.red,color.green,color.blue);
			_gui.setRegionColor(color);
	});

	this.addDynamicListeners = function(){
		$('.reading-order-segment').mouseover(function(){
				var $this = $(this);
				var segmentID = $this.data('segmentid');
				_controller.enterSegment(segmentID);
		});

		$('.reading-order-segment').mouseleave(function(){
				var $this = $(this);
				var segmentID = $this.data('segmentid');
				_controller.leaveSegment(segmentID);
		});

		$('.reading-order-segment').on('dragstart', function (event) {
				var $this = $(this);
				_draggedObject = $this;
		});

		$('.reading-order-segment').on('dragover', function (event) {
				return false;
		});

		$('.reading-order-segment').on('dragleave', function (event) {
				$(this).removeClass('dragedOver');
				event.preventDefault();
				return false;
		});

		$('.reading-order-segment').on('dragenter', function (event) {
					var $this = $(this);
					$this.addClass('dragedOver');
					if(_draggedObject){
						_controller.setBeforeInReadingOrder(_draggedObject.data('segmentid'),$(event.target).data('segmentid'),false);
					}
				return true;
		});

		$('.reading-order-segment').on('drop', function (event) {
				var $this = $(this);
				$this.removeClass('dragedOver');
				if(_draggedObject){
					_controller.setBeforeInReadingOrder(_draggedObject.data('segmentid'),$(event.target).data('segmentid'),true);
				}
		});
	}
}
