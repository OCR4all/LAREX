function ViewerInput(controller) {
	const _controller = controller;
	this.selector;

	this.enterElement = function (sectionID, event, mode=ViewerMode.POLYGON) {
		if(mode != ViewerMode.CONTOUR)
			_controller.highlightSegment(sectionID, true);
	}

	this.leaveElement = function (sectionID, event, mode=ViewerMode.POLYGON) {
		if(mode != ViewerMode.CONTOUR)
			_controller.highlightSegment(sectionID, false);
	}

	this.clickElement = function (sectionID, event, hitTest, mode=ViewerMode.POLYGON) {
		switch (event.event.button) {
			// leftclick
			case 0:
				if(mode == ViewerMode.POLYGON){
					_controller.selectSegment(sectionID, hitTest);
				} else if(mode == ViewerMode.CONTOUR){
					_controller.selectSegment(sectionID,null,ElementType.CONTOUR);
				} else {
					throw new ValueError('Unkown selection mode: '+mode);
				}
				break;
			// middleclick
			case 1:
				break;
			// rightclick
			case 2:
				if(mode == ViewerMode.POLYGON){
					if (!_controller.isSegmentSelected(sectionID)) {
						_controller.unSelect();
						_controller.selectSegment(sectionID, hitTest);
						_controller.openContextMenu(true);
					}
					_controller.endCreateReadingOrder();
				} else if(mode == ViewerMode.CONTOUR){

				} else {
					throw new ValueError('Unkown selection mode: '+mode)
				}
				break;
		}
	}

	this.dragImage = function (event) {
		switch (event.event.button) {
			// leftclick
			case 0:
				if (event.modifiers.shift) {
					this.selector.boxSelect(event.point);
				} else {
					if(_controller.hasPointsSelected())
						_controller.moveSelectedPoints();
					else
						_controller.moveImage(event.delta);
				}
				break;
			// middleclick
			case 1:
				break;
			// rightclick
			case 2:
				_controller.endCreateReadingOrder();
				break;
		}
	}

	this.dragBackground = function (event) {
		switch (event.event.button) {
			// leftclick
			case 0:
				if (event.modifiers.shift) { 
					this.selector.boxSelect(event.point);
				}
				break;
			// middleclick
			case 1:
				break;
			// rightclick
			case 2:
				_controller.endCreateReadingOrder();
				break;
		}
	}

	this.clickImage = function (event) {
		switch (event.event.button) {
			// leftclick
			case 0:
				break;
			// middleclick
			case 1:
				break;
			// rightclick
			case 2:
				_controller.endCreateReadingOrder();
				break;
		}
		if (!event.modifiers.control) {
			_controller.unSelect();
		}
		_controller.closeContextMenu();
	}

	this.clickBackground = function (event) {
		switch (event.event.button) {
			// leftclick
			case 0:
				break;
			// middleclick
			case 1:
				break;
			// rightclick
			case 2:
				_controller.endCreateReadingOrder();
				break;
		}
		if (!event.modifiers.control) {
			_controller.unSelect();
		}
		_controller.closeContextMenu();
	}
}
