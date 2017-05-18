function NavigationController(){
	var _gui;
	var _viewer;

	this.setGUI = function(gui){
		_gui = gui;
	}
	this.setViewer = function(viewer){
		_viewer = viewer;
	}

	//Navigation
	this.center = function() {
		_viewer.center();
	}
	this.setZoom = function(zoomfactor, point) {
		if(!_viewer.isEditing){
			_viewer.setZoom(zoomfactor, point);
			_gui.updateZoom();
		}
	}
	this.zoomIn = function(zoomfactor, point) {
		if(!_viewer.isEditing){
			_viewer.zoomIn(zoomfactor, point);
			_gui.updateZoom();
		}
	}
	this.zoomOut = function(zoomfactor, point) {
		if(!_viewer.isEditing){
			_viewer.zoomOut(zoomfactor, point);
			_gui.updateZoom();
		}
	}
	this.zoomFit = function() {
		if(!_viewer.isEditing){
			_viewer.center();
			_viewer.zoomFit();
			_gui.updateZoom();
		}
	}
	this.move = function(x, y) {
		_viewer.move(x, y);
	}
	this.moveCanvas = function(doMove) {
		_gui.moveCanvas(doMove);
	}
}
