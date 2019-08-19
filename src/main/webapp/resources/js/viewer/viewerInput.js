function ViewerInput(controller) {
	const _controller = controller;
	this.navigationController;
	this.selector;

	/**
	 * Action fired, when an element is entered in the viewer.
	 * e.g. via mouse hover
	 */
	this.enterElement = function (sectionID, event, mode=ViewerMode.POLYGON) {
		if(mode != ViewerMode.CONTOUR)
			_controller.highlightSegment(sectionID, true);
	}

	/**
	 * Action fired, when an element is left in the viewer.
	 * e.g. via mouse hover
	 */
	this.leaveElement = function (sectionID, event, mode=ViewerMode.POLYGON) {
		if(mode != ViewerMode.CONTOUR)
			_controller.highlightSegment(sectionID, false);
	}

	/**
	 * Action fired, when an element is clicked in the viewer.
	 */
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
					if (!this.selector.isSegmentSelected(sectionID)) {
						this.selector.unSelect();
						_controller.selectSegment(sectionID, hitTest);
						_controller.openContextMenu(true);
					}
					_controller.endEditReadingOrder();
				} else if(mode == ViewerMode.CONTOUR){

				} else {
					throw new ValueError('Unkown selection mode: '+mode)
				}
				break;
		}
	}

	/**
	 * Action fired, when the image in the viewer is dragged.
	 */
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
						this.navigationController.move(event.delta.x,event.delta.y);
				}
				break;
			// middleclick
			case 1:
				break;
			// rightclick
			case 2:
				_controller.endEditReadingOrder();
				break;
		}
	}

	/**
	 * Action fired, when the background in the viewer is dragged. 
	 */
	this.dragBackground = function (event) {
		switch (event.event.button) {
			// leftclick
			case 0:
				if (event.modifiers.shift) { 
					this.selector.boxSelect(event.point);
				} else {
					this.navigationController.move(event.delta.x,event.delta.y);
				}
				break;
			// middleclick
			case 1:
				break;
			// rightclick
			case 2:
				_controller.endEditReadingOrder();
				break;
		}
	}

	/**
	 * Action fired, when the image in the viewer is clicked.
	 */
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
				_controller.endEditReadingOrder();
				break;
		}
		if (!event.modifiers.control) {
			this.selector.unSelect();
		}
		_controller.closeContextMenu();
	}

	/**
	 * Action fired, when the background in the viewer is clicked
	 */
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
				_controller.endEditReadingOrder();
				break;
		}
		if (!event.modifiers.control) {
			this.selector.unSelect();
		}
		_controller.closeContextMenu();
	}
}
