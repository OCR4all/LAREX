function KeyInput(navigationController, controller, gui) {
	this.isActive = true;
	var _navigationController = navigationController;
	var _controller = controller;
	var _gui = gui;
	var _this = this;
	var _mousePosition;

	document.onkeydown = function(event) {
		if (_this.isActive) {
			var validKey = false;

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
				_navigationController.zoomFit();
				validKey = true;
				break;
			case 187: // +
				_navigationController.zoomIn(0.1);
				validKey = true;
				break;
			case 189: // -
				_navigationController.zoomOut(0.1);
				validKey = true;
				break;
			case 17: // CTRL
				_controller.selectmultiple = true;
				break;
			case 16: // Shift
				_controller.selectinbewteen = true;
				break;
			case 89: // Y
				if (event.ctrlKey) {
					_controller.redo();
					validKey = true;
				}
				break;
			case 90: // Z
				if (event.ctrlKey) {
					_controller.undo();
					validKey = true;
				}
				break;
			case 46: // DELETE
				_controller.deleteSelected();
				validKey = true;
				break;

			case 27: // ESC
				_controller.escape();
				validKey = true;
				break;
			case 49: // 1
				_controller.createRectangle('region');
				validKey = true;
				break;
			case 50: // 2
				_controller.createBorder(false);
				validKey = true;
				break;
			case 51: // 3
				_controller.createRectangle('segment');
				validKey = true;
				break;
			case 52: // 4
				_controller.createPolygon(true);
				validKey = true;
				break;
			case 53: // 5
				_controller.createCut();
				validKey = true;
				break;
			case 77: // M
				_controller.moveSelected();
				validKey = true;
				break;
			case 83: // S
				_controller.scaleSelected();
				validKey = true;
				break;
			case 18: // ALT
				document.body.style.cursor = "move";

				_controller.applyGrid();
				break;

			 //default: //Debug to get key codes
			 //	alert(event.keyCode);

			}

			if (validKey = true) {
				event.cancelBubble = true;
				event.returnValue = false;
			}
			return event.returnValue;
		}
	}

	document.onkeyup = function(event) {
		if (_this.isActive) {
			var validKey = false;

			switch (event.keyCode) {
				case 16: // Shift
					_controller.selectinbewteen = false;
					break;
				case 17: // CTRL
					_controller.selectmultiple = false;
					break;
				case 18: // ALT
					document.body.style.cursor = "auto";
					_controller.removeGrid();
					break;
			}

			if (validKey = true) {
				event.cancelBubble = true;
				event.returnValue = false;
			}
			return event.returnValue;
		}
	}

	var wheelEvent = 'onwheel' in document ? 'wheel': 'mousewheel DOMMouseScroll';
	$("canvas").bind(wheelEvent, function(event) {
		if (_this.isActive) {
			var canvasOffset = $(this).offset();
			var scrollDirection; //positive => down, negative => up

			switch(event.type){
				case 'wheel': //Modern Browser is used
					scrollDirection = event.originalEvent.deltaY;
					break;
				case 'mousewheel': //Old non Firefox or Opera browser is used
					scrollDirection = event.originalEvent.wheelDelta*-1;
					break;
				case 'DOMMouseScroll': //Old version of Firefox or Opera is used
					scrollDirection = event.originalEvent.detail;
					break;
			}

			var mousepoint = new paper.Point(event.originalEvent.pageX-canvasOffset.left,
																				event.originalEvent.pageY-canvasOffset.top);
			if (scrollDirection < 0) {
				_navigationController.zoomIn(0.1, mousepoint);
			} else {
				_navigationController.zoomOut(0.1, mousepoint);
			}
		}
	});

	var lastPositionX;
	var lastPositionY;

	$(window).mousemove(function(event) {
		if (_this.isActive) {
			var deltaX = event.pageX - lastPositionX;
			var deltaY = event.pageY - lastPositionY;

			if (_gui.doMoveCanvas) {
				if (deltaX && deltaY)
					_navigationController.move(deltaX, deltaY);
			}

			lastPositionX = event.pageX;
			lastPositionY = event.pageY;
		}
	});
}
