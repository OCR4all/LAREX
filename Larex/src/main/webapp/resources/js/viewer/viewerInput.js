function ViewerInput(controller) {
	var _controller = controller;

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
				if(event.modifiers.alt){
					_controller.moveImage(event.delta);
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

	this.clickImage = function(event){
		_controller.closeContextMenu();
	}
}
