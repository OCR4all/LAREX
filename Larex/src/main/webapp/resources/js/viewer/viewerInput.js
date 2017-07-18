function ViewerInput(controller) {
	var _controller = controller;
	var _mouseSelecting = false;

	this.enterSection = function(sectionID, info) {
		_controller.enterSegment(sectionID, true, info);
	}

	this.leaveSection = function(sectionID, info) {
		_controller.leaveSegment(sectionID, false, info);
	}

	this.selectSection = function(sectionID, info) {
		_controller.selectSegment(sectionID, info);
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
				break;
		}
	}

	this.clickImage = function(event){
		_controller.unSelect();
		_controller.closeContextMenu();
	}

	this.clickBackground = function(event){
		_controller.unSelect();
		_controller.closeContextMenu();
	}
}
