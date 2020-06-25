function NavigationController(gui,viewer,getMode) {
	let _gui = gui;
	let _viewer = viewer;
	let _getMode = getMode;

	//Navigation
	this.center = function () {
		_viewer.center();
		if(_getMode() === Mode.TEXT && _gui.isTextLineContentActive()){
			_gui.placeTextLineContent();
		}
	}
	this.setZoom = function (zoomfactor, point) {
		_viewer.setZoom(zoomfactor, point);
		_gui.updateZoom();
		if(_getMode() === Mode.TEXT && _gui.isTextLineContentActive()){
			_gui.placeTextLineContent();
		}
	}
	this.zoomIn = function (zoomfactor, point) {
		_viewer.zoomIn(zoomfactor, point);
		_gui.updateZoom();
		if(_getMode() === Mode.TEXT && _gui.isTextLineContentActive()){
			_gui.placeTextLineContent();
		}
	}
	this.zoomOut = function (zoomfactor, point) {
		_viewer.zoomOut(zoomfactor, point);
		_gui.updateZoom();
		if(_getMode() === Mode.TEXT && _gui.isTextLineContentActive()){
			_gui.placeTextLineContent();
		}
	}
	this.zoomFit = function () {
		_viewer.center();
		_viewer.zoomFit();
		_gui.updateZoom();
		if(_getMode() === Mode.TEXT && _gui.isTextLineContentActive()){
			_gui.placeTextLineContent();
		}
	}
	this.move = function (x, y) {
		if(!_viewer.isEditing){
			_viewer.move(x, y);
			if(_getMode() === Mode.TEXT && _gui.isTextLineContentActive()){
				_gui.placeTextLineContent();
			}
		}
	}
}
