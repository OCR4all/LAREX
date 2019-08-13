function GUI(canvas, viewer, colors, accessible_modes) {
	const _viewer = viewer;
	const _colors = colors;
	let _canvas = canvas;
	let _mouse;
	let _visiblePageStyles = [PageStatus.TODO,PageStatus.SERVERSAVED,PageStatus.SESSIONSAVED,PageStatus.UNSAVED];
	let _mode;
	let _textlineZoom = 1.0;
	let _textlineDelta = 0;

	$(document).mousemove((event) => _mouse = { x: event.pageX, y: event.pageY });

	/**
	 * Update the gui display of the viewer zoom
	 */
	this.updateZoom = function () {
		let zoom = _viewer.getZoom();
		zoom = Math.round(zoom * 10000) / 100;

		$('.zoomvalue').text(zoom);
		this.resizeTextLineContent();	
	}

	/**
	 * Reset custom user zoom for the textline
	 */
	this.resetZoomTextline = function(){
		_textlineZoom = 1;
	}

	/**
	 * Zoom into textline with zoomfactor ]0,1[
	 */
	this.zoomInTextline = function(zoomFactor){
		_textlineZoom *= (1+zoomFactor);
		this.resizeTextLineContent();
	}

	/**
	 * Zoom out of textline with zoomfactor ]0,1[
	 */
	this.zoomOutTextline = function(zoomFactor){
		_textlineZoom *= (1-zoomFactor);
		this.resizeTextLineContent();
	}

	/**
	 * Reset custom user horizontal delta for the textline
	 */
	this.resetTextlineDelta = function(){
		_textlineDelta = 0;
	}

	/**
	 * Move textline with by delta
	 */
	this.moveTextline = function(delta){
		_textlineDelta += delta;
		this.placeTextLineContent();
	}

	this.hideTextline = function(doHide=true) {
		if(doHide) {
			$('#textline-content').addClass("fade");
		} else {
			$('#textline-content').removeClass("fade");
		}
	}

	this.setMode = function(mode){
		// Open tab for mode if is not open already
		const tabBtnID = {"text":".mode-text",
					   "lines":".mode-lines",
					   "edit":".mode-edit",
					   "segment":".mode-segment"}[mode];
		const tabID = {"text":"text_tab",
					   "lines":"line_tab",
					   "edit":"edit_tab",
					   "segment":"segment_tab"}[mode];
		if (!$(`.tab${tabBtnID} > a`).hasClass("active")) {
			$('.mainMenu > .tabs').tabs('select_tab',tabID);
		}

		_mode = mode;
		switch (mode) {
			case Mode.LINES:
				$('#sidebar-segment').addClass('hide');
				$('#sidebar-lines').removeClass('hide');
				$('#sidebar-text').addClass('hide');
				this.displayReadingOrder(false);
				this.closeTextLineContent();
				break;
			case Mode.TEXT:
				$('#sidebar-segment').addClass('hide');
				$('#sidebar-lines').addClass('hide');
				$('#sidebar-text').removeClass('hide');
				this.displayReadingOrder(false);
				this.closeTextLineContent();
				break;
			case Mode.EDIT:
				$('.doSegment').addClass('hide');
				$('#collapsible-parameters').addClass('hide');
				$('#collapsible-settings').addClass('hide');
				$('.regionlegend').find(".switch").addClass('hide');
				$('.regionlegendAll').addClass('hide');
				$('.regionSegmentationSettings').addClass('hide');
				const $autosegmentSwitchBox = $('.settings-autosegment').find('input');
				if($autosegmentSwitchBox.prop('checked')){
					$autosegmentSwitchBox.click();
				}
				this.displayReadingOrder($("#reading-order-header").hasClass("active"));
				$('#sidebar-segment').removeClass('hide');
				$('#sidebar-lines').addClass('hide');
				$('#sidebar-text').addClass('hide');
				break;
			case Mode.SEGMENT:
			default:
				$('.doSegment').removeClass('hide');
				$('#collapsible-parameters').removeClass('hide');
				$('#collapsible-settings').removeClass('hide');
				$('.regionlegend').find(".switch").removeClass('hide');
				$('.regionlegendAll').removeClass('hide');
				$('.regionSegmentationSettings').removeClass('hide');
				this.displayReadingOrder($("#reading-order-header").hasClass("active"));
				$('#sidebar-segment').removeClass('hide');
				$('#sidebar-lines').addClass('hide');
				$('#sidebar-text').addClass('hide');
		}
	}

	this.setAccessibleModes = function(modes){
		$(".mode").addClass("hide");
		for(const cur_mode of modes){
			if(!(cur_mode === Mode.EDIT && Mode.SEGMENT in modes)){
				$(`.mode-${cur_mode}`).removeClass("hide");
			}
		}
		if (modes.length === 1){
			$(".mainMenu .tabs").addClass("hide")
		}
	}

	this.openContextMenu = function (doSelected, id) {
		const $contextmenu = $("#contextmenu");
		$contextmenu.removeClass("hide");
		const fitsInWindow = _mouse.y + $contextmenu.height() < $(window).height();

		if (fitsInWindow) {
			$contextmenu.css({ top: _mouse.y - 5, left: _mouse.x - 5 });
		} else {
			$contextmenu.css({ top: _mouse.y + 5 - $contextmenu.height(), left: _mouse.x - 5 });
		}
		$contextmenu.data('doSelected', doSelected);
		$contextmenu.data('id', id);
	}

	this.closeContextMenu = function () {
		$("#contextmenu").addClass("hide");
	}

	this.openTextView = function(){
		
	}
	/**
	 * Load and set a virtual keyboard for the gui.
	 * Keyboard can either be a list of list of characters (e.g. keyboard=[[a,b,c],[d,e]]) 
	 * or a new line and whitespace seperated string 
	 * (e.g. keyboard=`a b c
	 * 				   d e`   )
	 */
	this.setVirtualKeyboard = function (keyboard, isRaw=false){
		if(isRaw){
			try{
				keyboard = keyboard.split(/\n/).map(line => line.split(/\s+/));
			} catch{return;}
		}

		// Clear grid before loading all items
		$virtualKeyboard = $(".virtual-keyboard");
		$virtualKeyboard.empty();
		for(let x = 0; x < keyboard.length; x++){
			const row = keyboard[x];
			if(row.length > 0){
				const divRow = $('<div class="vk-row row"></div>');
				$virtualKeyboard.append(divRow);
				for(let y = 0; y < row.length; y++){
					if(row[y].length > 0){
						divRow.append($(`<div class="vk-drag draggable col s1 infocus" data-drag-group="keyboard" draggable="false">
											<a class="vk-btn btn infocus">${row[y]}</a>
										</div>`));
					}
				}
			}
		}
	}
	/**
	 * Get the current virtual keyboard from displayed in the gui.
	 * Keyboard can either be returned as list of list of characters (e.g. keyboard=[[a,b,c],[d,e]]) 
	 * or a new line and whitespace seperated string (asRaw)
	 * (e.g. keyboard=`a b c
	 * 				   d e`   )
	 */
	this.getVirtualKeyboard = function (asRaw=false){
		let virtualKeyboard = asRaw ? "" : [];

		$virtualKeyboard = $(".virtual-keyboard");
		for(const row of $virtualKeyboard.children(".vk-row")){
			const vkRow = [];
			for(const btn of $(row).find(".vk-btn")){
				vkRow.push(btn.innerHTML);
			}
			if(asRaw){
				virtualKeyboard += vkRow.join(" ")+"\n";
			}else{
				virtualKeyboard.push(vkRow);
			}
		}
		return virtualKeyboard;
	}
	/**
	 * Lock and unlock the virtual keyboard, in order to change and lock changes in the keyboard
	 */
	this.lockVirtualKeyboard = function(doLock){
		if(doLock){ 
			$('.vk-lock').addClass("hide");
			$('.vk-unlock').removeClass("hide");
			$('.vk-drag').attr("draggable",false);
		}else{
			$('.vk-lock').removeClass("hide");
			$('.vk-unlock').addClass("hide");
			$('.vk-drag').attr("draggable",true);
		}
	}
	/**
	 * Add a string as button to the virtual keyboard
	 */
	this.addVirtualKeyboardButton = function (btnValue){
		// Clear grid before loading all items
		const row = $('.vk-row').last();

		btnValue = btnValue.replace(/\s/,'');
		if(btnValue.length > 0){
			row.append($(`<div class="vk-drag draggable col s1 infocus" data-drag-group="keyboard" draggable="false">
								<a class="vk-btn btn infocus">${btnValue}</a>
							</div>`));
		} else {
			this.displayWarning(`Can't add empty buttons to the virtual keyboard.`);
		}
	}
	this.deleteVirtualKeyboardButton = function($button){
		$button.remove();
	}
	/**
	 * Return the length of buttons in a given row inside the virtual keyboard
	 */
	this.capKeyboardRowLength = function ($row){
		$children = $row.children();
		if($children.length > 12){
			const $newRow = $('<div class="vk-row row"></div>');
			$newRow.insertAfter($row);
			for(let i = 12; i < $children.length; i++){
				$newRow.append($children[i]);
			}
		}
		// Check for empty rows and delete them
		$('.vk-row').each((i,r)=> {
			if($(r).children().length == 0){
				$(r).remove()
			}
		});
	}
	/**
	 * Open the menu for adding new virtual keyboard buttons
	 */
	this.openAddVirtualKeyboardButton = function () {
		$('#vk-btn-value').val('');	
		$vk_add = $('#virtual-keyboard-add');
		$vk_add.removeClass('hide');
		$sidebarOffset = $('.virtual-keyboard-tools').first().offset();
		$vk_add.css({ top: $sidebarOffset.top, left: $sidebarOffset.left - $vk_add.width() });
	}
	/**
	 * Close the menu for adding new virtual keyboard buttons.
	 * Saving the button will add it to the virtual keyboard if its value is valid.
	 */
	this.closeAddVirtualKeyboardButton = function (doSave=false) {
		$('#virtual-keyboard-add').addClass("hide");
		if(doSave){
			this.addVirtualKeyboardButton($('#vk-btn-value').val());
		}
	}

	/**
	 * Open the textline content, ready to edit
	 */
	this.openTextLineContent = function (textline) {
		this.hideTextline(false);
		const $textlinecontent = $("#textline-content");
		$textlinecontent.removeClass("hide");
		
		if(!this.tempTextline || this.tempTextline.id != textline.id){
			this.tempTextline = textline ? textline : this.tempTextline; 
			this.updateTextLine(textline.id);
		}
		this.placeTextLineContent();
	}

	/**
	 * Place the textline content onto the viewer
	 */
	this.placeTextLineContent = function(textline=this.tempTextline){
		const $textlinecontent = $("#textline-content");
		let anchorX = Infinity;
		let anchorY = 0;

		if(textline){
			textline.points.forEach((point) => {
				anchorX = anchorX < point.x ? anchorX: point.x; 	
				anchorY = anchorY > point.y ? anchorY: point.y; 	
			});

			const viewerPoint = _viewer._convertGlobalToCanvas(anchorX+_textlineDelta,anchorY);
			$viewerCanvas = $("#viewer")[0];
			const left = $viewerCanvas.offsetLeft
			const top = $viewerCanvas.offsetTop

			$textlinecontent.css({ top:(viewerPoint.y + top), left: (viewerPoint.x + left) });
			$textlinecontent.data('textline', textline);

		}
	}

	/**
	 * Update the textline with its content
	 */
	this.updateTextLine = function(id) {
		if(this.tempTextline && this.tempTextline.id == id){
			const $textlinecontent = $("#textline-content");
			const hasPredict = 1 in this.tempTextline.text;
			const hasGT = 0 in this.tempTextline.text;
			const $textline_text = $("#textline-text");
			const start = $textline_text[0].selectionStart;
			const end = $textline_text[0].selectionEnd;
			if(hasGT){
				$textlinecontent.addClass("line-corrected")
				$textlinecontent.addClass("line-saved");
				$textline_text.val(this.tempTextline.text[0]);
				this.tempTextline.type = "TextLine_gt";
			} else {
				$textlinecontent.removeClass("line-corrected")
				$textlinecontent.removeClass("line-saved");
				this.tempTextline.type = "TextLine";
				if (hasPredict){
					$textline_text.val(this.tempTextline.text[1]);
				} else {
					$textline_text.val("");
				}
			}

			// Correct to last focus
			const content_len = $textline_text.val().length;
			$textline_text.focus();
			$textline_text[0].selectionStart = start < content_len ? start : content_len;
			$textline_text[0].selectionEnd = end < content_len ? end : content_len;
			this.resizeTextLineContent();
		}
	}

	/**
	 * Display a save of the contents of a textline
	 */
	this.saveTextLine = function(id,doSave=true){
		this.updateTextLine(id);
		const $textlinecontent = $("#textline-content");
		if(doSave){
			$textlinecontent.addClass("line-saved")
		}else{
			$textlinecontent.removeClass("line-saved")
		}
	}

	/**
	 * Resize the textline content based on its textline size and a user defined zoom
	 */
	this.resizeTextLineContent = function(){
		$buffer = $("#textline-buffer")[0];
		$buffer.textContent = $("#textline-text")[0].value.replace(/ /g, "\xa0");

		if(this.tempTextline && this.tempTextline.minArea){
			$("#textline-buffer, #textline-text").css({
				'font-size': this.tempTextline.minArea.height*_viewer.getZoom()*_textlineZoom+'px'
			})
		}
		$("#textline-content").css({
			width: $buffer.offsetWidth+'px'
		})
	}

	this.closeTextLineContent = function () {
		$("#textline-content").addClass("hide");
		this.tempTextline = null;
	}

	this.getTextLineContent = function () {
		if(this.tempTextline){
			return {id:this.tempTextline.id,text:$("#textline-text").val()};
		}
		return {};
	}

	this.isTextLineContentActive = function() {
		return !$("#textline-content").hasClass("hide");
	}

	/**
	 * Insert a character into the current poisition on the textline
	 */
	this.insertCharacterTextLine = function(character){
		if(this.isTextLineContentActive()){
			$input = $("#textline-text");
			const start = $input[0].selectionStart;
			const end = $input[0].selectionEnd;
			let text = $input.val();

			$input.val(text.substring(0,start)+character+text.substring(end));
			this.resizeTextLineContent();
			$input.focus();
			$input[0].selectionStart = start+character.length;
			$input[0].selectionEnd = start+character.length;
		}
	}

	this.resizeViewerHeight = function () {
		const $canvas = $("#viewer").children();
		const $sidebars = $('.sidebar');
		const height = $(window).height() - $canvas.offset().top;

		$canvas.outerHeight(height);
		$sidebars.height(height);

		$("#viewerText").outerWidth($("#viewerCanvas").outerWidth());

		this.loadVisiblePreviewImages();
	}

	this.setParameters = function (parameters, imageMode, combineMode) {
		$("#textdilationX").val(parameters['textdilationX']);
		$("#textdilationY").val(parameters['textdilationY']);
		$("#imagedilationX").val(parameters['imagedilationX']);
		$("#imagedilationY").val(parameters['imagedilationY']);

		const $imageMode = $('.settings-image-mode');
		$imageMode.find('option').removeAttr('selected');
		$imageMode.find('option[value="' + imageMode + '"]').attr('selected', 'selected');
		//reinitialize dropdown
		$imageMode.find('select').material_select();

		$('.settings-combine-image').find('input').prop('checked', combineMode);
	}

	this.getParameters = function () {
		const parameters = {};
		parameters['textdilationX'] = $("#textdilationX").val();
		parameters['textdilationY'] = $("#textdilationY").val();
		parameters['imagedilationX'] = $("#imagedilationX").val();
		parameters['imagedilationY'] = $("#imagedilationY").val();
		return parameters;
	}


	this.forceUpdateRegionHide = function (visibleRegions) {
		const $allSwitchBoxes = $('.regionlegend');
		const _visibleRegions = visibleRegions;
		$allSwitchBoxes.each(function () {
			const $this = $(this);
			const $switchBox = $($this.find('input'));
			const regionType = $this.data('type');

			if (_visibleRegions[regionType]) {
				$switchBox.prop('checked', true);
			} else {
				$switchBox.prop('checked', false);
			}
		});
	}
	this.showUsedRegionLegends = function (presentRegions) {
		$('.regionlegend,.contextregionlegend').each(function () {
			const $this = $(this);
			const legendType = $this.data('type');

			if ($.inArray(legendType, presentRegions) > -1) {
				$this.removeClass('hide');
			} else {
				$this.addClass('hide');
			}
		});
		$('.regioneditorSelectItem').each(function () {
			const $this = $(this);
			const legendType = $this.data('type');

			if (legendType == 'ignore' || $.inArray(legendType, presentRegions) > -1) {
				$this.addClass('hide');
			} else {
				$this.removeClass('hide');
			}
		});
	}

	this.openRegionSettings = function (regionType, minSize, maxOccurances, regionColorID) {
		$('#regionType').text(regionType);
		$('#regionMinSize').val(minSize);
		$('#regionMaxOccurances').val(maxOccurances);
		$('#regionType').removeClass('hide');
		$('#regioneditorSelect').addClass('hide');
		$('#regioneditorColorSelect').addClass('hide');
		$('.regionColorSettings').removeClass('hide');
		$('.regionSetting').removeClass('hide');
		$('#regioneditorSave').removeClass('hide');
		if (regionType != 'ImageRegion' && regionType != 'paragraph')
			$('.regionDelete').removeClass('hide');
		else 
			$('.regionDelete').addClass('hide');
		
		if (regionColorID) 
			this.setEditRegionColor(regionColorID);
		
		$settingsOffset = $('#sidebarRegions').offset();
		$regioneditor = $('#regioneditor');
		$regioneditor.removeClass('hide');
		$regioneditor.css({ top: $settingsOffset.top, left: $settingsOffset.left - $regioneditor.width() });
	}

	this.openRegionCreate = function () {
		$('#regionType').addClass('hide');
		$('#regioneditorSelect').removeClass('hide');
		$('#regioneditorColorSelect').addClass('hide');
		$('.regionColorSettings').addClass('hide');
		$('.regionSetting').addClass('hide');
		$('#regioneditorSave').addClass('hide');
		$('.regionDelete').addClass('hide');
		$settingsOffset = $('#sidebarRegions').offset();
		$regioneditor = $('#regioneditor');
		$regioneditor.removeClass('hide');
		$regioneditor.css({ top: $settingsOffset.top, left: $settingsOffset.left - $regioneditor.width() });
	}

	this.setEditRegionColor = function (colorID) {
		const $regioneditor = $('#regioneditor');
		const color = _colors.unpackColor(colorID);
		$regioneditor.find('.regionColorIcon').css("background-color", color.toCSS());
		$regioneditor.find('#regionColor').data('colorID', colorID);
	}

	this.createSelectColors = function () {
		const $collection = $('#regioneditorColorSelect .collection');
		_colors.getAllColorIDs().forEach(id => {
			const color = _colors.unpackColor(id);
			const $colorItem = $('<li class="collection-item regioneditorColorSelectItem color' + id + '"></li>');
			const $icon = $('<div class="legendicon" style="background-color:' + color.toCSS() + ';"></div>');
			$colorItem.data('colorID', id);
			$colorItem.append($icon);
			$collection.append($colorItem);
		});
	}

	this.updateAvailableColors = function () {
		$('.regioneditorColorSelectItem').addClass("hide");
		_colors.getAvailableColorIDs().forEach((id) => {
			$('.regioneditorColorSelectItem.color' + id).removeClass("hide");
		});
	}

	/**
	 * Update the colors of region legends for all supplied regions 
	 */
	this.updateRegionLegendColors = function() {
		let $color_style = $('#global-css-color');
		if($color_style.length == 0){
			$color_style = $('<style id="global-css-color"></style>');
			$('head').append($color_style);
		}
		let css = "";

		const regions = _colors.getAssigned();
		for(const region of Object.keys(regions)){
			css += `.legendicon.${region}{background-color:${_colors.getColor(region).toCSS()};}`;
		}
		$color_style.html(css);
	}

	this.closeRegionSettings = function () {
		$('#regioneditor').addClass('hide');
	}

	/**
	 * Display the reading Order in the gui
	 */
	this.displayReadingOrder = function (doDisplay) {
		$readingOrderList = (_mode === Mode.SEGMENT || _mode === Mode.EDIT) ? $('#reading-order-list') : $('#reading-order-list-lines');
		if (doDisplay) {
			$readingOrderList.removeClass("hide");
			_viewer.displayReadingOrder(this.getReadingOrder());
		} else {
			$readingOrderList.addClass("hide");
			_viewer.hideReadingOrder();
		}
	}

	/**
	 * Check if the current reading order is active in the gui
	 */
	this.isReadingOrderActive = function () {
		if(_mode === Mode.SEGMENT || _mode === Mode.EDIT || _mode === Mode.LINES) {
			$readingOrder = (_mode === Mode.SEGMENT || _mode === Mode.EDIT) ? $('#reading-order-header') : $('#reading-order-header-lines');
			return $readingOrder.hasClass("active");
		}else{
			return false;
		}
	}

	/** Set the in the gui visible reading order */
	this.setReadingOrder = function (readingOrder, segments, warning="Reading order is empty") {
		$readingOrderList = (_mode === Mode.SEGMENT || _mode === Mode.EDIT) ? $('#reading-order-list') : $('#reading-order-list-lines');
		$readingOrderList.empty();
		if(readingOrder && readingOrder.length > 0){
			for (let index = 0; index < readingOrder.length; index++) {
				const segment = segments[readingOrder[index]];
				if(segment){
					const $collectionItem = $('<li class="draggable collection-item reading-order-segment infocus" data-id="' + segment.id + '" data-drag-group="readingorder" draggable="true"></li>');
					const $legendTypeIcon = $('<div class="legendicon infocus ' + segment.type + '"></div>');
					const $deleteReadingOrderSegment = $('<i class="delete-reading-order-segment material-icons infocus" data-id="' + segment.id + '">delete</i>');
					$collectionItem.append($legendTypeIcon);
					const id = segment.id;
					$collectionItem.append(id.substring(id.length - 3, id.length) + "-" + segment.type );
					$collectionItem.append($deleteReadingOrderSegment);
					$readingOrderList.append($collectionItem);
				}
			}
		} else{
			$readingOrderList.append($(`<p class="warning">${warning}</p>`));
		}
	}

	/** Open the reading order collapsible */
	this.openReadingOrderSettings = function (){
		const $readingOrder = (_mode === Mode.SEGMENT || _mode === Mode.EDIT) ? $('#reading-order-header') : $('#reading-order-header-lines');
		if(!$readingOrder.hasClass("active")){
			$readingOrder.click();
		}
	}

	/**
	 * Get the reading order displayed in the gui (list of segment ids)
	 */
	this.getReadingOrder = function (){
		const readingOrder = [];
		const $readingOrderList = (_mode === Mode.SEGMENT || _mode === Mode.EDIT) ? $('#reading-order-list') : $('#reading-order-list-lines');
		const $readingOrderItems = $readingOrderList.children();
		
		$readingOrderItems.each((i,ir) => readingOrder.push($(ir).data("id")));

		return readingOrder;
	}

	this.highlightSegment = function (id, doHighlight) {
		if (doHighlight) {
			$(".reading-order-segment[data-id='" + id + "']").addClass('highlighted');
		} else {
			$(".reading-order-segment[data-id='" + id + "']").removeClass('highlighted');
		}
	}

	this.setBeforeInReadingOrder = function (segment1ID, segment2ID) {
		const $segment1 = $(".reading-order-segment[data-id='" + segment1ID + "']");
		const $segment2 = $(".reading-order-segment[data-id='" + segment2ID + "']");
		$($segment1).insertBefore($segment2);
	}


	this.forceUpdateReadingOrder = function (readingOrder, forceHard, segments) {
		if (forceHard) {
			this.setReadingOrder(readingOrder, segments);
		} else {
			$readingOrderListItems = $('#reading-order-list');

			for (let index = 0; index < readingOrder.length; index++) {
				$readingOrderListItems.append($(".reading-order-segment[data-id='" + readingOrder[index] + "']"));
			}
		}
	}

	this.doEditReadingOrder = function (doEdit) {
		if (doEdit) {
			$('.editReadingOrder').addClass("hide");
			$('.saveReadingOrder').removeClass("hide");
		} else {
			$('.saveReadingOrder').addClass("hide");
			$('.editReadingOrder').removeClass("hide");
		}
	}

	this.selectToolBarButton = function (option, doSelect) {
		let $button = null;
		switch (option) {
			case 'regionRectangle':
				$button = $('.createRegionRectangle');
				break;
			case 'regionBorder':
				$button = $('.createRegionBorder');
				break;
			case 'segmentRectangle':
				$button = $('.createSegmentRectangle');
				break;
			case 'segmentPolygon':
				$button = $('.createSegmentPolygon');
				break;
			case 'textlineRectangle':
				$button = $('.createTextLineRectangle');
				break;
			case 'textlinePolygon':
				$button = $('.createTextLinePolygon');
				break;
			case 'segmentContours':
				$button = $('.editContours');
				break;
			case 'editReadingOrder':
				$button = $('.editReadingOrder');
				break;
			case 'cut':
				$button = $('.createCut');
				break;
			case 'roi':
				$button = $('.setRegionOfInterest');
				break;
			case 'ignore':
				$button = $('.createIgnore');
				break;
			default:
				break;
		}
		if ($button) {
			if (doSelect) {
				$button.addClass('invert');
			} else {
				$button.removeClass('invert');
			}
		}
	}

	this.unselectAllToolBarButtons = function () {
		const $buttons = $('.menuIcon').not('.fixed');
		$buttons.removeClass('invert');
	}

	this.selectPage = function (page, imageNr) {
		$('.pageImageContainer').removeClass('selected');
		const $page_container = $('.pageImageContainer[data-page~="' + page + '"]');
		$page_container.addClass('selected');

		$('.image_version').removeClass('selected');
		$page_container.find('.image_version[data-imagenr~="' + imageNr + '"]').addClass("selected");

		this.scrollToPage(page);
	}

	this.scrollToPage = function (page) {
		const $pagecontainer = $('#pagecontainer');

		//Stop any running animations on pagecontainer
		$pagecontainer.stop(true);
		//Start scroll animation
		$pagecontainer.animate({
			scrollTop: $('.pageImageContainer[data-page~="' + page + '"]').position().top - $pagecontainer.offset().top + $pagecontainer.scrollTop()
		}, 2000);
	}

	this.addPageStatus = function (page, pagestatus = PageStatus.TODO){
		const $page = $('.pageImageContainer[data-page~="' + page + '"]');

		if(pagestatus === PageStatus.TODO){
			for(status in PageStatus){ 
				$page.removeClass(status); 
			}
			$page.find(".pagestatusIcon").addClass('hide');

			$page.addClass(PageStatus.TODO);
			$page.find(".pageIconTodo").removeClass('hide');
		}else{
			$page.removeClass(PageStatus.TODO);
			$page.find(".pageIconTodo").addClass('hide');

			$page.addClass(pagestatus);
			
			switch(pagestatus){
				case PageStatus.SESSIONSAVED:
					$page.find(".pageIconSession").removeClass('hide');
					$page.removeClass(PageStatus.UNSAVED);
					$page.find(".pageIconUnsaved").addClass('hide');
				break;
				case PageStatus.SERVERSAVED:
					$page.find(".pageIconServer").removeClass('hide');
					$page.removeClass(PageStatus.UNSAVED);
					$page.find(".pageIconUnsaved").addClass('hide');
				break;
				case PageStatus.UNSAVED:
					$page.find(".pageIconUnsaved").removeClass('hide');
					break;
			}
		}
	}
	this.highlightSegmentedPages = function (segmentedPages) {
		$('.pageImageContainer').removeClass('segmented');
		$('.pageImageContainer').addClass('statusTodo');
		$(".pageIconTodo").removeClass('hide');
		segmentedPages.forEach((page) => {
			const $segmentedPage = $('.pageImageContainer[data-page~="' + page + '"]');
			$segmentedPage.addClass('segmented');
			$segmentedPage.removeClass('statusTodo');
			$segmentedPage.find(".pageIconTodo").addClass('hide');
		});

	}
	this.highlightLoadedPage = function (exportedPage, doHighlight = true) {
		const $loadedPage = $('.pageImageContainer[data-page~="' + exportedPage + '"]');
		if (doHighlight) {
			$loadedPage.addClass('loaded');
			$loadedPage.find(".pageIconServer").removeClass('hide');
		} else {
			$loadedPage.removeClass('loaded');
			$loadedPage.find(".pageIconServer").addClass('hide');
		}
	}
	
	this.hidePages = function (doHide=true,type=PageStatus.TODO) {
		const indexOfStyle = _visiblePageStyles.indexOf(type);
		if(indexOfStyle >= 0 && doHide)
			_visiblePageStyles.splice(indexOfStyle,1);
		else if(indexOfStyle < 0 && !doHide)
			_visiblePageStyles.push(type);

		if(doHide){
			const excluder = _visiblePageStyles.map(s => (':not(.'+s+')')).join('');
			$('.pageImageContainer.'+type+excluder).addClass('hide');
		}else{
			$('.pageImageContainer.'+type).removeClass('hide');
		}
	}

	this.hideTodoPages = function (doHide=true) { this.hidePages(doHide,PageStatus.TODO); }

	this.hideSessionPages = function (doHide=true) { this.hidePages(doHide,PageStatus.SESSIONSAVED); }
	
	this.hideServerPages = function (doHide=true) { this.hidePages(doHide,PageStatus.SERVERSAVED); }
	
	this.hideUnsavedPages = function (doHide=true) { this.hidePages(doHide,PageStatus.UNSAVED); }

	this.setExportingInProgress = function (isInProgress) {
		if (isInProgress) {
			$('.exportPageXML').find('.progress').removeClass('hide');
		} else {
			$('.exportPageXML').find('.progress').addClass('hide');
		}
	}
	this.setPageXMLVersion = function (version) {
		$('.pageXMLVersion').text(version);
	}
	this.getPageXMLVersion = function () {
		return $('.pageXMLVersion').first().text();
	}
	this.setSaveSettingsInProgress = function (isInProgress) {
		if (isInProgress) {
			$('.saveSettingsXML').find('.progress').removeClass('hide');
		} else {
			$('.saveSettingsXML').find('.progress').addClass('hide');
		}
	}
	this.displayWarning = function (text) {
		Materialize.toast(text, 4000);
		console.warn(text);
	}

	this.addPreviewImageListener = function(){
		$('#pagecontainer').scroll(() => this.loadVisiblePreviewImages());
	}

	this.loadVisiblePreviewImages = function(){
		$previewImages = $('.emptyPreview');
		const pixelBuffer = 500;

		$previewImages.each(function(){
			const $p = $(this);
			const windowsHeight = $(window).height();
			if($p.offset().top < windowsHeight+pixelBuffer){
				const imageSrc = $p.data("image");
				const imageId = $p.data("page");
				const title = $p.data("title");

				const $image = $('<img class="pageImage" alt="'+title+'" title="'+title+'" src="images/books/'+imageSrc+'?resize=true" id="'+imageId+'previewImage" />');
				const $status = $('<div class="pagestatus">'+
									'<i class="material-icons pagestatusIcon pageIconTodo circle">assignment_late</i>'+
									'<i class="material-icons pagestatusIcon pageIconSession circle  hide">save</i>'+
									'<i class="material-icons pagestatusIcon pageIconServer circle hide">lock</i>'+
									'<i class="material-icons pagestatusIcon pageIconChanged circle hide">lock_open</i>'+
									'<i class="material-icons pagestatusIcon pageIconUnsaved circle hide">warning</i>'+
								'</div>');
				$p.append($image);
				$p.append($status);
				$p.removeClass("emptyPreview");
				$image.on('load', () => $p.removeClass("emptyImage"));
			}
		});
		
	}
	
	// Init script
	this.setAccessibleModes(accessible_modes);
}
