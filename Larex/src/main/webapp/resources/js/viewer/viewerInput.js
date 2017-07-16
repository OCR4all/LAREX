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
					_controller.moveImage(event.delta);
				}else{
					_controller.startRectangleSelect();
				}
				break;
			// middleclick
			case 1:
				_controller.moveImage(event.delta);
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
				_controller.startRectangleSelect();
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
		console.log("test");
		_controller.unSelect();
		_controller.closeContextMenu();
	}
}
