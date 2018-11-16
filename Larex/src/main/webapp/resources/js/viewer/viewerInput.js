function ViewerInput(controller) {
	const _controller = controller;

	this.enterElement = function (sectionID, event, selectMode=SelectMode.POLYGON) {
		if(selectMode == SelectMode.POLYGON){
			_controller.highlightSegment(sectionID, true);
		} else if(this.selectMode == SelectMode.CONTOUR){

		} else {
			throw new ValueError('Unkown selection mode: '+this.selectMode)
		}
	}

	this.leaveElement = function (sectionID, event, selectMode=SelectMode.POLYGON) {
		if(selectMode == SelectMode.POLYGON){
			_controller.highlightSegment(sectionID, false);
		} else if(this.selectMode == SelectMode.CONTOUR){

		} else {
			throw new ValueError('Unkown selection mode: '+this.selectMode)
		}
	}

	this.selectSection = function (sectionID, event, hitTest, selectMode=SelectMode.POLYGON) {
		switch (event.event.button) {
			// leftclick
			case 0:
				if(selectMode == SelectMode.POLYGON){
					_controller.selectSegment(sectionID, hitTest);
				} else if(this.selectMode == SelectMode.CONTOUR){

				} else {
					throw new ValueError('Unkown selection mode: '+this.selectMode)
				}
				break;
			// middleclick
			case 1:
				break;
			// rightclick
			case 2:
				if(selectMode == SelectMode.POLYGON){
					if (!_controller.isSegmentSelected(sectionID)) {
						_controller.unSelect();
						_controller.selectSegment(sectionID, hitTest);
						_controller.openContextMenu(true);
					}
					_controller.endCreateReadingOrder();
				} else if(this.selectMode == SelectMode.CONTOUR){

				} else {
					throw new ValueError('Unkown selection mode: '+this.selectMode)
				}
				break;
		}
	}

	this.dragImage = function (event) {
		switch (event.event.button) {
			// leftclick
			case 0:
				if (event.modifiers.shift) 
					_controller.boxSelect();
				else {
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
				if (event.modifiers.shift) 
					_controller.boxSelect();
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
