function GuiInput(navigationController, controller, gui, textViewer, selector) {
	const _navigationController = navigationController;
	const _controller = controller;
	const _gui = gui;
	const _textViewer = textViewer;
	const _selector = selector;

	$(window).click((event) => {
		//Cancel viewer actions, if outside of viewer or a menu icon
		const $target = $(event.target);
		if (!$target.is('body') && !$target.is('#viewer') && !$target.parents("#viewer").is("#viewer")
			&& !$target.is('.infocus') && !$target.parents(".infocus").is(".infocus")) {
			_controller.escape();
		}
	});

	// button registration
	$("#viewer").contextmenu(() => {
		_controller.openContextMenu(true);
		return false; //prevents default contextmenu
	});
	let block_mode_switch = false;
	$('.mode').click(function(){
		if(!block_mode_switch){
			block_mode_switch = true;
			_controller.setMode($(this).data("mode"));
			block_mode_switch = false;
		}
	});
	$('.doSegment').click(() => _controller.requestSegmentation());
	$('.exportPageXML').click(() => _controller.exportPageXML());
	$('.pageXMLVersionSelect').click(function () {
		const version = $(this).data('version');
		_gui.setPageXMLVersion(version);
		$('.dropDownPageXML').dropdown('close');
	});
	$('.dropDownPageXMLCorner').click((event) => {
		event.stopPropagation();
		$('.dropDownPageXML').dropdown('open');
	});
	$('.saveSettingsXML').click(() => _controller.saveSettingsXML());
	$('#upload-input:file').on('change', function () {
		const file = this.files[0];
		$(this).val("");
		if (file) {
			if (file.size < 1024 * 1024) {
				_controller.uploadSettings(file);
			} else {
				_gui.warning('Can\'t upload settings files larger than 1MB.')
			}
		}
	});
	$('#upload-segmentation-input:file').on('change', function () {
		const file = this.files[0];
		$(this).val("");
		if (file) {
			if (file.size < 2048 * 1024) {
				_controller.uploadSegmentation(file);
			} else {
				_gui.warning('Can\'t upload segmentation files larger than 2MB.')
			}
		}
	});
	$('.upload-virtual-keyboard').on('change', function(){
		const reader = new FileReader();
		reader.onload = function(){
			_gui.setVirtualKeyboard(reader.result,isRaw=true);
		}
		if (this.files[0].size < 512 * 1024) {
			reader.readAsText(this.files[0]);
		} else {
			_gui.warning('Can\'t upload virtual keybord files larger than 512kB.')
		}
	});
	$('.vk-download').click(() => _controller.saveVirtualKeyboard());

	$('.reload').click(() => location.reload());
	$('.settings-image-mode').on('change', function () {
		if (this.value) {
			_controller.changeImageMode(this.value);
		}
	})
	$('.settings-combine-image').on('change', function () {
		const doCombine = $(this).find('input').prop('checked');
		if (doCombine !== undefined) {
			_controller.changeImageCombine(doCombine);
		}
	});
	$('.createRegionRectangle').click(() => _controller.createRectangle('region'));
	$('.setRegionOfInterest').click(() => _controller.createRectangle('roi'));

	$('.createIgnore').click(() => _controller.createRectangle('ignore'));
	$('.createRegionBorder').click(() => _controller.createRegionBorder());
	$('.createSegmentPolygon').click(() => _controller.createSegmentPolygon(true));
	$('.createSegmentRectangle').click(() => _controller.createRectangle('segment'));
	$('.createTextLinePolygon').click(() => _controller.createTextLinePolygon(true));
	$('.createTextLineRectangle').click(() => _controller.createRectangle('textline'));
	$('.createCut').click(() => _controller.createCut());

	$('.combineSelected').click(() => _controller.mergeSelectedSegments());
	$('.deleteSelected').click(() => _controller.deleteSelected());
	$('.fixSelected').click(() => _controller.fixSelected());
	$('.editContours').click(() => _controller.displayContours());

	$('.displayTextView').click(() => _controller.displayTextViewer(true));
	$('.hideTextView').click(() => _controller.displayTextViewer(false));

	$('.zoomin').click(() => {
		if(_textViewer.isOpen()){
			_textViewer.zoomGlobalImage(0.05);
		} else {
			_navigationController.zoomIn(0.1);
		}
	});
	$('.zoomout').click(() => {
		if(_textViewer.isOpen()){
			_textViewer.zoomGlobalImage(-0.05);
		} else {
			_navigationController.zoomOut(0.1);
		}
	});
	$('.zoomin_second').click(() => {
		if(_textViewer.isOpen()){
			_textViewer.zoomGlobalText(0.05);
		}
	});
	$('.zoomout_second').click(() => {
		if(_textViewer.isOpen()){
			_textViewer.zoomGlobalText(-0.05);
		}
	});
	$('.zoomfit').click(() => {
		if(_textViewer.isOpen()){
			_textViewer.resetGlobalImageZoom();
		} else {
			_navigationController.zoomFit();
		}
	});
	$('.zoomfit_second').click(() => {
		if(_textViewer.isOpen()){
			_textViewer.resetGlobalTextZoom();
		}
	});

	$('.moveright').click(() => _navigationController.move(10, 0));
	$('.moveleft').click(() => _navigationController.move(-10, 0));
	$('.movedown').click(() => _navigationController.move(0, 10));
	$('.moveup').click(() => _navigationController.move(0, -10));
	$('.movecenter').click(() => _navigationController.center());

	$('.undo').click(() => _controller.undo());
	$('.redo').click(() => _controller.redo());

	$('.changePage').click(function (e) {
		_controller.displayPage($(this).data("page"), $(this).data("imagenr"));
		e.stopPropagation();
		return true;
	});
	$('.regionlegend').click(function () {
		const $this = $(this);
		const $switchBox = $this.find('input');
		_controller.hideRegion($this.data('type'), !$switchBox.prop('checked'));
	});
	$('.regionlegendAll').click(function () {
		const $switchBox = $(this).find('input');
		const $allSwitchBoxes = $('.regionlegend').find('input');
		$allSwitchBoxes.prop('checked', $switchBox.prop('checked'))

		_controller.hideAllRegions(!$switchBox.prop('checked'));
	});
	$('.regionSettings, #regioneditorSelect .collection-item').click(function () { _controller.openRegionSettings($(this).data("type")) });
	$('.regionCreate').click(function () { _controller.openRegionSettings() });
	$('.regionDelete').click(() => {
		const regionType = $('#regioneditor').find('#regionType').text();
		_controller.deleteRegionSettings(regionType);
		_gui.closeRegionSettings();
	});
	$('.contextTypeOption').click(function () {
		const $this = $(this);
		const $contextmenu = $("#contextmenu");
		const doSelected = $contextmenu.data('doSelected');
		const regionType = $this.data('type');

		if (doSelected) {
			_controller.changeTypeSelected(regionType);
		} else {
			const id = $contextmenu.data('id');
			_controller.changeRegionType(id, regionType);
		}
		_gui.closeContextMenu();
	});

	$('#regioneditorSave').click(() => {
		const $regioneditor = $('#regioneditor');

		const regionType = $regioneditor.find('#regionType').text();
		const minSize = $regioneditor.find('#regionMinSize').val();
		const maxOccurances = $regioneditor.find('#regionMaxOccurances').val();
		let colorID = $regioneditor.find('#regionColor').data('colorID');
		_controller.changeRegionSettings(regionType, minSize, maxOccurances);
		_controller.setRegionColor(regionType, colorID);
		_gui.closeRegionSettings();
	});
	$('#regioneditorCancel').click(() => _gui.closeRegionSettings());
	$('#regioneditor #regionType').click(() => {
		const $regioneditor = $('#regioneditor');
		$('#regioneditorSelect').removeClass('hide');
	});
	$('#regioneditor #regionColor').click(() => {
		const $regioneditorColorSelect = $('#regioneditorColorSelect');
		if ($regioneditorColorSelect.hasClass('hide')) {
			_gui.updateAvailableColors();
			$('#regioneditorColorSelect').removeClass('hide');
		} else {
			$('#regioneditorColorSelect').addClass('hide');
		}
	});
	$('.regioneditorColorSelectItem').click(function () {
		_gui.setEditRegionColor($(this).data('colorID'));
	});

	$('.collapsible-header').click(function () {
		if ($(this).is('#reading-order-header')) {
			const wasActive  = $(this).hasClass("active");
			_controller.displayReadingOrder(!wasActive);
		}else if ($(this).is('#reading-order-header-lines')) {
			const wasActive  = $(this).hasClass("active");
			_controller.forceUpdateReadingOrder();
			_controller.displayReadingOrder(!wasActive);
		} else {
			_controller.displayReadingOrder(false);
		}
	});
	$('#textline-text').on('input', function() {
		_gui.resizeTextLineContent();
		_gui.saveTextLine(false,false);
	}).trigger('input');

	// Set begin
	const $loadSwitchBox = $('.settings-load-existing-xml').find('input');
	_controller.allowToLoadExistingSegmentation($loadSwitchBox.prop('checked'));
	$('.settings-load-existing-xml').click(function () {
		const $this = $(this);
		const $switchBox = $this.find('input');
		_controller.allowToLoadExistingSegmentation($switchBox.prop('checked'));
	});

	// Set begin
	const $autosegmentSwitchBox = $('.settings-autosegment').find('input');
	_controller.allowToAutosegment($autosegmentSwitchBox.prop('checked'));
	$('.settings-autosegment').click(function () {
		const $this = $(this);
		const $switchBox = $this.find('input');
		_controller.allowToAutosegment($switchBox.prop('checked'));
	});

	$('.loadExistingSegmentation').click(() => _controller.requestSegmentation(true));

	$('.addToReadingOrder').click(() => _controller.addSelectedToReadingOrder());

	$('.autoGenerateReadingOrder').click(() => _controller.autoGenerateReadingOrder());

	$('.editReadingOrder').click(() => _controller.toggleEditReadingOrder());

	$('.saveReadingOrder').click(() => _controller.toggleEditReadingOrder());

	$('.delete-reading-order').click((e) => {
		_controller.deleteReadingOrder()
		e.stopPropagation();
	});

	$('#pageLegend > .pageIconTodo').click(function(event) { 
		$this = $(this);
		const isChecked = !$this.hasClass('checked');
		_gui.hideTodoPages(!isChecked); 
		if(isChecked) $this.addClass('checked'); else $this.removeClass('checked');
	});
	$('#pageLegend > .pageIconSession').click(function(event) {
		$this = $(this);
		const isChecked = !$this.hasClass('checked');
		_gui.hideSessionPages(!isChecked); 
		if(isChecked) $this.addClass('checked'); else $this.removeClass('checked');
	});
	$('#pageLegend > .pageIconServer').click(function(event) {
		$this = $(this);
		const isChecked = !$this.hasClass('checked');
		_gui.hideServerPages(!isChecked); 
		if(isChecked) $this.addClass('checked'); else $this.removeClass('checked');
	});
	$('#pageLegend > .pageIconUnsaved').click(function(event) {
		$this = $(this);
		const isChecked = !$this.hasClass('checked');
		_gui.hideUnsavedPages(!isChecked); 
		if(isChecked) $this.addClass('checked'); else $this.removeClass('checked');
	});

	/*** Dynamically added listeners 
	 * (Add Listeners to document and use selector in on function)
	 ***/
	let _hasBeenDropped = false;

	$(document).on("mouseover",'.reading-order-segment',function () {
		const $this = $(this);
		const id = $this.data('id');
		_controller.highlightSegment(id,true);
	});

	$(document).on("mouseleave",'.reading-order-segment',function () {
		const $this = $(this);
		const id = $this.data('id');
		_controller.highlightSegment(id,false);
	});

	$(document).on("click",'.textline-container', function (){
		const $this = $(this);
		const id = $this.data('id');
		_controller.selectElement(id);
	});

	/**
	 * Drag'n Drop Objects
	 */
	let $drag_target = null;
	$(document).on('dragstart','.draggable', function (event) {
		_hasBeenDropped = false;
		$drag_target = $(this);
		event.originalEvent.dataTransfer.setData('Text', this.id);
	});
	$(document).on('dragover','.draggable', (event) => false);
	$(document).on('dragleave','.draggable', function (event) {
		$(this).removeClass('draggable-target');
		event.preventDefault();
	});
	$(document).on('dragenter','.draggable', function (event) {
		const $this = $(this);
		if($this.data("drag-group") == $(event.target).data('drag-group')){
			$this.addClass('draggable-target');
		}
	});
	$(document).on('drop','.draggable', function (event) {
		const $this = $(this);

		if($drag_target && $this.data("drag-group") == $drag_target.data('drag-group')){
			$drag_target.insertBefore($this);
		}
	});
	$(document).on('dragend','.draggable', (event) => {
		$('.draggable').removeClass("draggable-target");
	});

	/* Virtual Keyboard */
	$(document).on("click",'.vk-btn', function(event){
		const character = $(this).text();
		if(_textViewer.isOpen()){
			const selected = _selector.getSelected();
			if(selected){
				_textViewer.setFocus(selected[0]);
				_textViewer.insertCharacterTextLine(character);
			}
		}else{
			_gui.insertCharacterTextLine(character);
		}
	});
	$(document).on('drop','.vk-row', function (event) {
		_gui.capKeyboardRowLength($(this));
	});
	$(document).on("click",'.vk-lock', function(event){
		_gui.lockVirtualKeyboard(true);
	});
	$(document).on("click",'.vk-unlock', function(event){
		_gui.lockVirtualKeyboard(false);
	});
	$(document).on('drop','.vk-delete', function (event) {
		if($drag_target.data('drag-group') == "keyboard"){
			_gui.deleteVirtualKeyboardButton($drag_target);
		}
	});
	$('.vk-add').click(() =>  _gui.openAddVirtualKeyboardButton());
	$('#vk-save').click(() => _gui.closeAddVirtualKeyboardButton(true));
	$('#vk-cancel').click(() => _gui.closeAddVirtualKeyboardButton(false));

	/* Reading Order */
	$(document).on('dragenter','.reading-order-segment', function (event) {
		const $this = $(this);
	});
	$(document).on('drop','.reading-order-segment', function (event) {
		const $this = $(this);
		const $other = $(event.target);
		if ($this.data("drag-group") == $other.data('drag-group')) {
			_gui.setBeforeInReadingOrder($this.data('id'), $other.data('id'));
			_controller.saveReadingOrder();
		}
		_hasBeenDropped = true;
	});

	$(document).on('dragend','.reading-order-segment', (event) => {
	});
	$(document).on("click",'.delete-reading-order-segment', function () {
		const $this = $(this);
		const id = $this.data('id');
		_controller.removeFromReadingOrder(id);
	});

	// Text View
	$("#viewerText").on('input','.textline-text', function() {
		const id = $(this).closest(".textline-container").data("id");
		_textViewer.resizeTextline(id)
		_textViewer.saveTextLine(id,false);
	}).trigger('input');
}
