function KeyInput(_navigationController, _controller, _gui, _textViewer, _selector, viewerFocus) {
	this.isActive = true;
	const _this = this;
	const _special_keys = {};

	document.onkeydown = function (event) {
		if (_this.isActive) {
			let validKey = false;
			const mode = _controller.getMode();

			if(mode == Mode.TEXT && (_gui.isTextLineContentActive() || _textViewer.isOpen())){
				switch(event.keyCode){
					case 27: // ESC
						_controller.escape();
						_controller.endEditReadingOrder();
						validKey = true;
						break;
					case 9: // TAB
						_selector.selectNext(event.shiftKey);
						validKey = true;
						break;
					case 16: // SHIFT
						if(_gui.isTextLineContentActive){
							_gui.hideTextline(true);
							_special_keys[event.keyCode] = true;
						}
						break;
					case 18: // ALT
						break;
					case 13: // Enter
						_controller.saveLine();
						_selector.selectNext();
						validKey = true;
						break;
					case 187: // +
					case 171: // +
						if(_textViewer.isOpen() && !_textViewer.isAnyLineFocused()){
							if(event.ctrlKey){
								_textViewer.zoomGlobalText(0.05);
							}else{
								_textViewer.zoomGlobalImage(0.05);
							}
							validKey = true;
						}
						break;
					case 189: // -
					case 173: // -
						if(_textViewer.isOpen() && !_textViewer.isAnyLineFocused()){
							if(event.ctrlKey){
								_textViewer.zoomGlobalText(-0.05);
							}else{
								_textViewer.zoomGlobalImage(-0.05);
							}
							validKey = true;
						}
						break;
					case 32: // space
						if(_textViewer.isOpen() && !_textViewer.isAnyLineFocused()){
							if(event.ctrlKey){
								_textViewer.resetGlobalTextZoom();
							} else {
								_textViewer.resetGlobalImageZoom();
							}
							validKey = true;
						}
						break;
					case 83: // S
						if (event.ctrlKey) {
							_controller.exportPageXML();
							validKey = true;
						}
						break;
					case 89: // Y
						if (event.ctrlKey) {
							_controller.redo();
							validKey = true;
						}
						break;
					case 90: // Z
						if (event.ctrlKey) {
							if (event.shiftKey) {
								_controller.redo();
							} else {
								_controller.undo();
							}
							validKey = true;
						}
						break;
				}
			}else{
				switch (event.keyCode) {
					case 37: // left
						_navigationController.move(-10, 0);
						validKey = true;
						break;
					case 38: // up
						_navigationController.move(0, -10);
						validKey = true;
						break;
					case 39: // right
						_navigationController.move(10, 0);
						validKey = true;
						break;
					case 40: // left
						_navigationController.move(0, 10);
						validKey = true;
						break;
					case 32: // space
						if (!event.ctrlKey) {
							_navigationController.zoomFit();
							validKey = true;
						} else {
							if(mode === Mode.SEGMENT){
								_controller.requestSegmentation()
								validKey = true;
							}
						}
						break;
					case 187: // +
					case 171: // +
						_navigationController.zoomIn(0.1);
						validKey = true;
						break;
					case 189: // -
					case 173: // +
						_navigationController.zoomOut(0.1);
						validKey = true;
						break;
					case 17: // CTRL
						_selector.selectMultiple = true;
						validKey = true;
						break;
					case 16: // Shift
						_selector.selectBox = true;
						_special_keys[event.keyCode] = true;
						validKey = true;
						break;
					case 9: // TAB
						_selector.selectNext(event.shiftKey);
						validKey = true;
						break;
					case 65: // A
						if (event.ctrlKey) {
							_selector.selectAll();
							validKey = true;
						}
						break;
					case 89: // Y
						if (event.ctrlKey) {
							_controller.redo();
							validKey = true;
						}
						break;
					case 90: // Z
						if (event.ctrlKey) {
							if (event.shiftKey) {
								_controller.redo();
							} else {
								_controller.undo();
							}
							validKey = true;
						}
						break;
					case 46: // DELETE
						_controller.deleteSelected();
						validKey = true;
						break;

					case 27: // ESC
						_controller.escape();
						_controller.endEditReadingOrder();
						validKey = true;
						break;
					case 49: // 1
						if(mode === Mode.SEGMENT){
							_controller.createRectangle(ElementType.AREA);
							validKey = true;
						}
						break;
					case 50: // 2
						if(mode === Mode.SEGMENT){
							_controller.createRegionAreaBorder();
							validKey = true;
						}
						break;
					case 51: // 3
						if(mode === Mode.SEGMENT || mode === Mode.EDIT){
							_controller.createRectangle(ElementType.SEGMENT);
							validKey = true;
						} else if(mode === Mode.LINES){
							_controller.createRectangle(ElementType.TEXTLINE);
							validKey = true;
						}
						break;
					case 52: // 4
						if(mode === Mode.SEGMENT || mode === Mode.EDIT){
							_controller.createSegmentPolygon();
							validKey = true;
						} else if(mode === Mode.LINES){
							_controller.createTextLinePolygon();
							validKey = true;
						}
						break;
					case 53: // 5
						if(mode === Mode.SEGMENT){
							_controller.createCut();
							validKey = true;
						}
						break;
					case 54: // 6
						if(mode === Mode.SEGMENT || mode === Mode.EDIT || mode === Mode.LINES){
							_controller.displayContours();
							validKey = true;
						}
						break;
					case 67: // C
						if(mode === Mode.SEGMENT || mode === Mode.EDIT || mode === Mode.LINES){
							_controller.mergeSelectedSegments();
							validKey = true;
						}
						break;
					case 70: // F
						if(mode === Mode.SEGMENT){
							_controller.fixSelected();
							validKey = true;
						}
						break;
					case 82: // R
						if (!event.ctrlKey && !event.shiftKey) {
							_controller.addSelectedToReadingOrder();
							validKey = true;
						} else if(event.ctrlKey && !event.shiftKey){
							_controller.toggleEditReadingOrder();
							validKey = true;
						}
						break;
					case 83: // S
						if (event.ctrlKey) {
							_controller.exportPageXML();
							validKey = true;
						}
						break;
					case 18: // ALT
						//document.body.style.cursor = "move";

						_controller.applyGrid();
						break;
					default: //Debug to get key codes
					//alert(event.keyCode);
				}
			}

			if (validKey == true) {
				event.cancelBubble = true;
				event.returnValue = false;
			}
			return event.returnValue;
		}
	}

	document.onkeyup = function (event) {
		if (_this.isActive) {
			let validKey = false;
			const mode = _controller.getMode();

			if(mode == Mode.TEXT && _gui.isTextLineContentActive()){
				switch (event.keyCode) {
					case 16: // Shift
						_gui.hideTextline(false);
						_special_keys[event.keyCode] = false;
						break;
				}
			} else {
				switch (event.keyCode) {
					case 16: // Shift
						_selector.selectBox = false;
						_special_keys[event.keyCode] = false;
						validKey = true;
						break;
					case 17: // CTRL
						_selector.selectMultiple = false;
						validKey = true;
						break;
					case 18: // ALT
						document.body.style.cursor = "auto";
						_controller.removeGrid();
						validKey = true;
						break;
				}
			}

			if (validKey == true) {
				event.cancelBubble = true;
				event.returnValue = false;
			}
			return event.returnValue;
		}
	}

	// Scroll Viewer
	const wheelEvent = 'onwheel' in document ? 'wheel' : 'mousewheel DOMMouseScroll';
	let isZooming = false;
	let lastTimeout = null;
	$(viewerFocus.join(",")).bind(wheelEvent, function (event) {
		if (_this.isActive && !isZooming) {
			const mode = _controller.getMode();
			isZooming = true;
			let scrollDirection; //positive => down, negative => up

			switch (event.type) {
				case 'wheel': //Modern Browser is used
					scrollDirection = event.originalEvent.deltaY;
					break;
				case 'mousewheel': //Old non Firefox or Opera browser is used
					scrollDirection = event.originalEvent.wheelDelta * -1;
					break;
				case 'DOMMouseScroll': //Old version of Firefox or Opera is used
					scrollDirection = event.originalEvent.detail;
					break;
			}
			if(mode === Mode.TEXT && _textViewer.isOpen()){
				if(event.originalEvent.ctrlKey){
					const is_target_image = event.target && ($(event.target).parent().data("id") == _textViewer.getFocusedId()) 
														&& event.target.classList.contains("textline-image");
					if (scrollDirection < 0) {
						//Zoom in
						if(is_target_image){
							_textViewer.zoomImage(1.1);
						} else {
							_textViewer.zoomTextInput(1.1);
						}
						_textViewer.resizeTextline(_textViewer.getFocusedId());
					} else {
						//Zoom out
						if(is_target_image){
							_textViewer.zoomImage(0.9);
						} else {
							_textViewer.zoomTextInput(0.9);
						}
						_textViewer.resizeTextline(_textViewer.getFocusedId());
					}
				} else if (event.originalEvent.shiftKey) {
					// Scroll
					if (scrollDirection < 0) {
						_textViewer.moveTextInput(2);
					} else {
						_textViewer.moveTextInput(-2);
					}
				} else {
					isZooming = false;
					return;
				}
			} else {
				if(event.originalEvent.ctrlKey){
					if (scrollDirection < 0) {
						_gui.zoomInTextline(0.1);
					} else {
						_gui.zoomOutTextline(0.1);
					}
				} else if (event.originalEvent.shiftKey) {
					// Display textline for 1 sec after scrolling
					_gui.hideTextline(false);
					clearTimeout(lastTimeout);
					lastTimeout = setTimeout(() => _gui.hideTextline(_special_keys[16]),500);

					// Scroll
					if (scrollDirection < 0) {
						_gui.moveTextline(10);
					} else {
						_gui.moveTextline(-10);
					}
				} else {
					const canvasOffset = $(viewerFocus[0]).offset();

					const mousepoint = new paper.Point(event.originalEvent.pageX - canvasOffset.left,
						event.originalEvent.pageY - canvasOffset.top);
					if (scrollDirection < 0) {
						_navigationController.zoomIn(0.1, mousepoint);
					} else {
						_navigationController.zoomOut(0.1, mousepoint);
					}
				}
			} 
			isZooming = false;
			event.preventDefault();
		}
	});

}
