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
		_controller.moveImage(event.delta);
	}

	this.clickImage = function(event){
		_controller.closeContextMenu();
	}
}
