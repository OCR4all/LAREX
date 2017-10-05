function ViewerInput(controller) {
	var _controller = controller;
	var _mouseSelecting = false;

	this.enterSection = function(sectionID, info,event) {
		_controller.enterSegment(sectionID, true, info);
	}

	this.leaveSection = function(sectionID, info,event) {
		_controller.leaveSegment(sectionID, false, info);
	}

	this.selectSection = function(sectionID, info,event) {
		switch (event.event.button) {
			// leftclick
			case 0:
				_controller.selectSegment(sectionID, info);
				break;
			// middleclick
			case 1:
				break;
			// rightclick
			case 2:
				if(!_controller.isSegmentSelected(sectionID)){
					_controller.unSelect();
					_controller.selectSegment(sectionID, info);
					_controller.openContextMenu(true);
				}
				_controller.endCreateReadingOrder();
				break;
		}
	}

	this.dragImage = function(event){
		switch (event.event.button) {
			// leftclick
			case 0:
				if(event.modifiers.shift){
					_controller.startRectangleSelect();
				}else{
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

	this.dragBackground = function(event){
		switch (event.event.button) {
			// leftclick
			case 0:
				if(event.modifiers.shift){
					_controller.startRectangleSelect();
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

	this.clickImage = function(event){
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
		if(!event.modifiers.control){
			_controller.unSelect();
		}
		_controller.closeContextMenu();
	}

	this.clickBackground = function(event){
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
		if(!event.modifiers.control){
			_controller.unSelect();
		}
		_controller.closeContextMenu();
	}
}
