// Unused File that should represent the Interface of the Viewer
// (Javascript does not support Inheritance in any way)
function IViewer(viewer) {
	this.setImage = function(id){}
	this.addSegment = function(segment){}
	this.clear = function() {}
	this.updateSegment = function(segment) {} //
	this.removeSegment = function(id) {}
	this.highlightSegment = function(id, doHighlight) {}
	this.selectSegment = function(id, doSelect) {}
	this.getBoundaries = function(){}
	
	// Navigation
	this.center = function() {}
	this.getZoom = function(){}
	this.setZoom = function(zoomfactor) {}
	this.zoomIn = function(zoomfactor) {}
	this.zoomOut = function(zoomfactor) {}
	this.zoomFit = function() {}
	this.movePoint = function(delta) {}
	this.move = function(x, y) {}

	//Protected functions
	this.drawPath = function(segment, doFill, color){}
	this.getImageCanvas = function(){}
}