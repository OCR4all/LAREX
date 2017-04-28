function GuiInput(navigationController, controller, gui){
	var _navigationController = navigationController;
	var _controller = controller;
	var _gui = gui;

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

	$('.createRegionRectangle').click(function() {
		_controller.createRectangle(false);
	});

	$('.createRegionBorder').click(function() {
		_controller.createBorder(false);
	});

	$('.createSegmentPolygon').click(function() {
		_controller.createPolygon(true);
	});

	$('.createSegmentRectangle').click(function() {
		_controller.createRectangle(true);
	});

	$('.createSegmentBorder').click(function() {
		_controller.createBorder(true);
	});

	$('.createCut').click(function() {
		_controller.createCut();
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
			_controller.changeRegionSettings(regionType, minSize,maxOccurances);
			_gui.closeRegionSettings();
	});
	$('#regioneditorCancel').click(function(){
			_gui.closeRegionSettings();
	});
	$('#regioneditor #regionType').click(function(){
			var $regioneditor = $('#regioneditor');
			$('#regioneditorSelect').removeClass('hide');
	});
	$('.modal').modal({
		 dismissible: true, // Modal can be dismissed by clicking outside of the modal
		 opacity: .2, // Opacity of modal background
		 inDuration: 300, // Transition in duration
		 outDuration: 200, // Transition out duration
		 startingTop: '4%', // Starting top style attribute
		 endingTop: '10%', // Ending top style attribute
	});
}
