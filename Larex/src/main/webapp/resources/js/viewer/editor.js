// Editor extends viewer
function Editor(viewer,controller) {
	this.isEditing = false;
	var _viewer = viewer;
	var _controller = controller;
	var _editMode = -1; // -1 default, 0 Polygon, 1 Rectangle, 2 Border, 3 Line, 4 Move, 5 Scale
	var _tempPathType;
	var _tempPath;
	var _tempPoint;
	var _tempID;
	var _tempMouseregion;
	var _tempEndCircle;
	var _this = this;
	var _grid = {isActive:false};
	this.mouseregions = {TOP:0,BOTTOM:1,LEFT:2,RIGHT:3,MIDDLE:4,OUTSIDE:5};

	this.addRegion = function(region){
		this.drawPath(region, true, {type: 'region'});
	}

	this.addLine = function(line){
		_this.drawPathLine(line);
	}

	this.removeLine = function(lineID){
		this.removeSegment(lineID);
	}

	this.removeRegion = function(regionID){
		this.removeSegment(regionID);
	}

	this.startCreatePolygon = function(type) {
		if(_this.isEditing === false){
			_editMode = 0;
			_this.isEditing = true;
			_tempPathType = type;
			document.body.style.cursor = "copy";

			var tool = new paper.Tool();
			tool.activate();
			tool.onMouseMove = function(event) {
				if(_tempPath){
					_tempPath.removeSegment(_tempPath.segments.length - 1);
					_tempPath.add(_this.getPointInBounds(event.point, _this.getBoundaries()));
				}
			}

			tool.onMouseDown = function(event) {
				if(_this.isEditing === true){
					var canvasPoint = _this.getPointInBounds(event.point, _this.getBoundaries());

					if (!_tempPath) {
						// Start path
						_tempPath = new paper.Path();
						_tempPath.add(new paper.Point(canvasPoint)); //Add Point for mouse movement
						_tempPath.fillColor = 'grey';
						_tempPath.opacity = 0.3;
						_tempPath.closed = false;
						_tempPath.selected = true;

						// circle to end the path
						_tempEndCircle = new paper.Path.Circle(canvasPoint, 5);
						_tempEndCircle.strokeColor = 'black';
						_tempEndCircle.fillColor = 'grey';
						_tempEndCircle.opacity = 0.5;
						_tempEndCircle.onMouseDown = function(event) {
							_this.endCreatePolygon();
							this.remove();
						}

						var imageCanvas = _this.getImageCanvas();
						imageCanvas.addChild(_tempPath);
						imageCanvas.addChild(_tempEndCircle);
					}
					_tempPath.add(new paper.Point(canvasPoint));
				}else{
					this.remove();
				}
			}
		}
	}

	this.endCreatePolygon = function() {
		if(_this.isEditing){
			_this.isEditing = false;
			if(_tempPath != null){
				_tempPath.closed = true;
				_tempPath.selected = false;
				if(_tempPathType === 'segment'){
					_controller.callbackNewFixedSegment(convertPointsPathToSegment(_tempPath,false));
				}else{
					_controller.callbackNewRegion(convertPointsPathToSegment(_tempPath,true));
				}
				_tempPath.remove();
				_tempPath = null;
				_tempEndCircle.remove();
				_temPath = null;
			}
			document.body.style.cursor = "auto";
		}
	}

	this.startCreateRectangle = function(type) {
		if(_this.isEditing === false){
			_editMode = 1;
			_this.isEditing = true;
			_tempPathType = type;
			createResponsiveRectangle(_this.endCreateRectangle);
		}
	}

	this.endCreateRectangle = function() {
		if(_this.isEditing){
			_this.isEditing = false;
			if(_tempPath != null){
				_tempPath.closed = true;
				_tempPath.selected = false;
				switch(_tempPathType){
					case 'segment':
						_controller.callbackNewFixedSegment(convertPointsPathToSegment(_tempPath,false));
						break;
					case 'region':
						_controller.callbackNewRegion(convertPointsPathToSegment(_tempPath,true));
						break;
					case 'ignore':
						_controller.callbackNewRegion(convertPointsPathToSegment(_tempPath,true),'ignore');
						break;
					case 'roi':
					default:
						_controller.callbackNewRoI(convertPointsPathToSegment(_tempPath,true));
						break;
				}
				_tempPath.remove();
				_tempPath = null;
			}
			document.body.style.cursor = "auto";
		}
	}

	this.startCreateLine = function() {
		if(_this.isEditing === false){
			_editMode = 3;
			_this.isEditing = true;
			document.body.style.cursor = "copy";

			var tool = new paper.Tool();
			tool.activate();
			tool.onMouseMove = function(event) {
				if(_tempPath){
					_tempPath.removeSegment(_tempPath.segments.length - 1);
					_tempPath.add(_this.getPointInBounds(event.point, _this.getBoundaries()));
				}
			}

			tool.onMouseDown = function(event) {
				if(_this.isEditing === true){
					var canvasPoint = _this.getPointInBounds(event.point, _this.getBoundaries());

					if (!_tempPath) {
						// Start path
						_tempPath = new paper.Path();
						_tempPath.add(new paper.Point(canvasPoint)); //Add Point for mouse movement
						_tempPath.strokeColor = new paper.Color(0,0,0);
						_tempPath.closed = false;
						_tempPath.selected = true;

						_this.getImageCanvas().addChild(_tempPath);
					}
					_tempPath.add(new paper.Point(canvasPoint));
				}else{
					this.remove();
				}
			}
		}
	}

	this.endCreateLine = function() {
		if(_this.isEditing){
			_this.isEditing = false;

			if(_tempPath != null){
				_tempPath.closed = false;
				_tempPath.selected = false;
				_controller.callbackNewCut(convertPointsPathToSegment(_tempPath,false));

				_tempPath.remove();
				_tempPath = null;
			}
			document.body.style.cursor = "auto";
		}
	}

	this.startCreateBorder = function(type) {
		if(_this.isEditing === false){
			_this.isEditing = true;
			_editMode = 2;
			_tempPathType = type;

			var tool = new paper.Tool();
			tool.activate();

			if (!_tempPath) {
				// Start path
				_tempPath = new paper.Path();
				_tempPath.fillColor = 'grey';
				_tempPath.opacity = 0.5;
				_tempPath.closed = true;
				//_tempPath.selected = true;

				_this.getImageCanvas().addChild(_tempPath);

				tool.onMouseMove = function(event) {
					if(_this.isEditing === true){
						if (_tempPath) {
							var boundaries = _viewer.getBoundaries();
							var mouseregion = _this.getMouseRegion(boundaries,event.point);
							_tempMouseregion = mouseregion;

							switch(mouseregion){
							case _this.mouseregions.LEFT:
								document.body.style.cursor = "col-resize";

								var topleft = new paper.Point(boundaries.left,boundaries.top);
								var bottommouse = new paper.Point(event.point.x, boundaries.bottom);
								var rectangle = new paper.Path.Rectangle(topleft, bottommouse);

								_tempPath.segments = rectangle.segments;
								break;
							case _this.mouseregions.RIGHT:
								document.body.style.cursor = "col-resize";

								var topright = new paper.Point(boundaries.right,boundaries.top);
								var bottommouse = new paper.Point(event.point.x, boundaries.bottom);
								var rectangle = new paper.Path.Rectangle(topright, bottommouse);

								_tempPath.segments = rectangle.segments;
								break;
							case _this.mouseregions.TOP:
								document.body.style.cursor = "row-resize";

								var topleft = new paper.Point(boundaries.left,boundaries.top);
								var mouseright = new paper.Point(boundaries.right, event.point.y);
								var rectangle = new paper.Path.Rectangle(topleft, mouseright);

								_tempPath.segments = rectangle.segments;
								break;
							case _this.mouseregions.BOTTOM:
								document.body.style.cursor = "row-resize";

								var bottomleft = new paper.Point(boundaries.left,boundaries.bottom);
								var mouseright = new paper.Point(boundaries.right, event.point.y);
								var rectangle = new paper.Path.Rectangle(bottomleft, mouseright);

								_tempPath.segments = rectangle.segments;
								break;
							case _this.mouseregions.MIDDLE:
							default:
								_tempPath.removeSegments();
								document.body.style.cursor = "copy";
								break;
							}
						}
					}
				}
				tool.onMouseDown = function(event) {
					if(_tempPath){
						_this.endCreateBorder();
						this.remove();
					}
				}
			}
		}
	}

	this.endCreateBorder = function() {
		if(_this.isEditing){
			_this.isEditing = false;

			if(_tempPath != null){
				if(_tempPathType === 'segment'){
					_controller.callbackNewFixedSegment(convertPointsPathToSegment(_tempPath,false));
				}else{
					_controller.callbackNewRegion(convertPointsPathToSegment(_tempPath,true));
				}

				_tempPath.remove();
				_tempPath = null;
			}
			document.body.style.cursor = "auto";
		}
	}

	this.startMovePath = function(pathID,type) {
		if(_this.isEditing === false){
			_editMode = 4;
			_this.isEditing = true;
			_tempPathType = type;
			document.body.style.cursor = "copy";

			// Create Copy of movable
			_tempPath = new paper.Path(_this.getPath(pathID).segments);
			//_tempPath = _this.getPath(pathID).clone();
			_tempID = pathID;
			_tempPath.fillColor = 'grey';
			_tempPath.opacity = 0.3;
			_tempPath.closed = true;
			_tempPath.strokeColor = 'black';
			_tempPath.dashArray = [5, 3];

			// Set Grid
			_this.setGrid(_tempPath.position);

			// Position variables between old and new path position
			_tempPoint = new paper.Point(0,0);
			var oldPosition = new paper.Point(_tempPath.position);
			var oldMouse = null;

			var tool = new paper.Tool();
			tool.activate();
			tool.onMouseMove = function(event) {
				if(_this.isEditing === true){
					if(oldMouse === null){
						oldMouse = event.point;
					}
					_tempPoint = oldPosition.add(event.point.subtract(oldMouse));
					if(!_grid.isActive){
						_grid.vertical.visible = false;
						_grid.horizontal.visible = false;
					}else{
						_tempPoint = _this.getPointFixedToGrid(_tempPoint);
						_grid.vertical.visible = true;
						_grid.horizontal.visible = true;
					}
					_tempPath.position = _tempPoint;

					// Correct to stay in viewer bounds
					var tempPathBounds = _tempPath.bounds;
					var pictureBounds = _this.getBoundaries();
					var correctionPoint = new paper.Point(0,0);
					if(tempPathBounds.left < pictureBounds.left){
						correctionPoint = correctionPoint.add(new paper.Point((pictureBounds.left-tempPathBounds.left),0));
					}
					if(tempPathBounds.right > pictureBounds.right){
						correctionPoint = correctionPoint.subtract(new paper.Point((tempPathBounds.right-pictureBounds.right),0));
					}
					if(tempPathBounds.top < pictureBounds.top){
						correctionPoint = correctionPoint.add(new paper.Point(0,(pictureBounds.top-tempPathBounds.top)));
					}
					if(tempPathBounds.bottom > pictureBounds.bottom){
						correctionPoint = correctionPoint.subtract(new paper.Point(0,(tempPathBounds.bottom-pictureBounds.bottom)));
					}
					_tempPoint = _tempPoint.add(correctionPoint);
					_tempPath.position = _tempPoint;
				}else{
					this.remove();
				}
			}
			tool.onMouseDown = function(event) {
				if(_this.isEditing === true){
					_this.endMovePath();
				}
				this.remove();
			}
		}
	}

	this.endMovePath = function() {
		if(_this.isEditing){
			_this.isEditing = false;

			if(_tempPath != null){
				if(_tempPathType === 'segment'){
					_controller.transformSegment(_tempID,convertPointsPathToSegment(_tempPath,false));
				}else{
					_controller.transformRegion(_tempID,convertPointsPathToSegment(_tempPath,true));
				}

				_tempPath.remove();
				_tempPath = null;
			}
			//hide grid
			_grid.vertical.visible = false;
			_grid.horizontal.visible = false;

			document.body.style.cursor = "auto";
		}
	}

	this.startScalePath = function(pathID,type) {
		if(_this.isEditing === false){
			_editMode = 5;
			_this.isEditing = true;
			_tempPathType = type;

			// Create Copy of movable
			var boundaries = _this.getPath(pathID).bounds;
			_tempPath = new paper.Path.Rectangle(boundaries);
			_tempID = pathID;
			_tempPath.fillColor = 'grey';
			_tempPath.opacity = 0.3;
			_tempPath.closed = true;
			_tempPath.strokeColor = 'black';
			_tempPath.dashArray = [5, 3];

			var tool = new paper.Tool();
			tool.activate();
			tool.onMouseMove = function(event) {
				if(_this.isEditing === true){
					if(_tempPath){
						var mouseregion = _this.getMouseRegion(_tempPath.bounds,event.point,0.1);
						_tempMouseregion = mouseregion;

						switch(mouseregion){
						case _this.mouseregions.LEFT:
						case _this.mouseregions.RIGHT:
							document.body.style.cursor = "col-resize";
							break;
						case _this.mouseregions.TOP:
						case _this.mouseregions.BOTTOM:
							document.body.style.cursor = "row-resize";
							break;
						case _this.mouseregions.MIDDLE:
						default:
							document.body.style.cursor = "auto";
							break;
						}
					}
				}else{
					this.remove();
				}
			}
			tool.onMouseDown = function(event) {
				if(_this.isEditing === true){
					scalePath(_tempPath,_tempMouseregion);
				}
				this.remove();
			}
		}
	}

	var createResponsiveRectangle = function(endFunction){
			var imageCanvas = _this.getImageCanvas();
			document.body.style.cursor = "copy";

			var tool = new paper.Tool();
			tool.activate();
			tool.onMouseDown = function(event) {
				if(_this.isEditing === true){
					var canvasPoint = _this.getPointInBounds(event.point, _this.getBoundaries());

					if (!_tempPath) {
						// Start path
						_tempPoint = new paper.Point(canvasPoint);
						_tempPath = new paper.Path();
						_tempPath.add(_tempPoint); //Add Point for mouse movement
						_tempPath.fillColor = 'grey';
						_tempPath.opacity = 0.3;
						_tempPath.closed = true;
						_tempPath.selected = true;

						tool.onMouseMove = function(event) {
							if(_this.isEditing === true){
								if (_tempPath) {
									var point = _this.getPointInBounds(event.point, _this.getBoundaries());
									var rectangle = new paper.Path.Rectangle(_tempPoint, point);

									_tempPath.segments = rectangle.segments;
								}
							}
						}
						_this.getImageCanvas().addChild(_tempPath);
					}else{
						endFunction();
						this.remove();
					}
				}else{
					this.remove();
				}
			}
	}

	var scalePath = function(path,mouseregion){
		var tool = new paper.Tool();
		tool.activate();
		tool.onMouseMove = function(event) {
			if(_this.isEditing === true){
				if(_tempPath){
					var mouseinbound = _this.getPointInBounds(event.point,_viewer.getBoundaries());

					switch(mouseregion){
					case _this.mouseregions.LEFT:
						if(mouseinbound.x < path.bounds.right){
							path.bounds.left = mouseinbound.x;
							document.body.style.cursor = "col-resize";
						}
						break;
					case _this.mouseregions.RIGHT:
						if(mouseinbound.x > path.bounds.left){
							path.bounds.right = mouseinbound.x;
							document.body.style.cursor = "col-resize";
						}
						break;
					case _this.mouseregions.TOP:
						if(mouseinbound.y < path.bounds.bottom){
							path.bounds.top = mouseinbound.y;
							document.body.style.cursor = "row-resize";
						}
						break;
					case _this.mouseregions.BOTTOM:
						if(mouseinbound.y > path.bounds.top){
							path.bounds.bottom = mouseinbound.y;
							document.body.style.cursor = "row-resize";
						}
						break;
					case _this.mouseregions.MIDDLE:
					default:
						document.body.style.cursor = "auto";
						this.remove();
						break;
					}
				}
			}else{
				this.remove();
			}
		}
		tool.onMouseUp = function(event) {
			if(_this.isEditing === true){
				_this.endScalePath();
			}
			this.remove();
		}
	}

	this.endScalePath = function() {
		if(_this.isEditing){
			_this.isEditing = false;

			if(_tempPath != null){
				var path = new paper.Path(_this.getPath(_tempID).segments);
				path.bounds = _tempPath.bounds;

				if(_tempPathType === 'segment'){
					_controller.transformSegment(_tempID,convertPointsPathToSegment(path,false));
				}else{
					_controller.transformRegion(_tempID,convertPointsPathToSegment(path,true));
				}

				_tempPath.remove();
				_tempPath = null;
			}

			document.body.style.cursor = "auto";
		}
	}

	this.getMouseRegion = function(bounds,mousepos,percentarea){
		var width = bounds.width;
		var height = bounds.height;
		if(percentarea == null){
			percentarea = 0.4;
		}

		var leftmin = bounds.left;
		var leftmax = leftmin + (width*percentarea);

		var rightmax = bounds.right;
		var rightmin = rightmax- (width*percentarea);

		var topmin = bounds.top;
		var topmax = topmin + (height*percentarea);

		var bottommax = bounds.bottom;
		var bottommin = bottommax - (height*percentarea);
		if(mousepos.x < leftmin || mousepos.x > rightmax || mousepos.y < topmin || mousepos.y > bottommax){
			return this.mouseregions.OUTSIDE;
		}else{
			//Get Mouse position/region
			if(mousepos.x > leftmin && mousepos.x < leftmax){
				return this.mouseregions.LEFT;
			}else if(mousepos.x > rightmin && mousepos.x < rightmax){
				return this.mouseregions.RIGHT;
			}else if(mousepos.y > topmin && mousepos.y < topmax){
				return this.mouseregions.TOP;
			}else if(mousepos.y > bottommin && mousepos.y < bottommax){
				return this.mouseregions.BOTTOM;
			}else{
				return this.mouseregions.MIDDLE;
			}
		}
	}

	this.endEditing = function(doAbbord){
		if(!doAbbord){
			if(_this.isEditing){
				switch(_editMode){
					case 0:
						_this.endCreatePolygon();
						break;
					case 1:
						_this.endCreateRectangle();
						break;
					case 2:
						_this.endCreateBorder();
						break;
					case 3:
						_this.endCreateLine();
						break;
					case 4:
						_this.endMovePath();
						break;
					case 5:
						_this.endMovePath();
						break;
					default:
						break;
				}
			}
		}else{
			_this.isEditing = false;

			_tempID = null;
			if(_tempPath != null){
				_tempPath.remove();
				_tempPath = null;
			}
			_tempPoint = null;

			if(_tempEndCircle){
				_tempEndCircle.remove();
				_tempEndCircle = null;
			}

			document.body.style.cursor = "auto";
		}
	}

	this.getPointInBounds = function(point, bounds){
		if(!bounds.contains(point)){
			var boundPoint = point;
			if(point.x < bounds.left){
				boundPoint.x = bounds.left;
			}else if(point.x > bounds.right){
				boundPoint.x = bounds.right;
			}
			if(point.y < bounds.top){
				boundPoint.y = bounds.top;
			}else if(point.y > bounds.bottom){
				boundPoint.y = bounds.bottom;
			}

			return boundPoint;
		}else{
			return point;
		}
	}

	this.addGrid = function(){
		_grid.isActive = true;
	}

	this.setGrid = function(point){
		if(_grid.vertical ==  null || _grid.horizontal == null){
			_grid.vertical = new paper.Path.Line();
			_grid.vertical.strokeColor = 'black';
			_grid.vertical.dashArray = [3, 3];

			_grid.horizontal = new paper.Path.Line();
			_grid.horizontal.strokeColor = 'black';
			_grid.horizontal.dashArray = [3, 3];
		}
		var bounds = paper.view.bounds;
		_grid.vertical.removeSegments();
		_grid.vertical.add(new paper.Point(point.x,bounds.top));
		_grid.vertical.add(new paper.Point(point.x,bounds.bottom));

		_grid.horizontal.removeSegments();
		_grid.horizontal.add(new paper.Point(bounds.left,point.y));
		_grid.horizontal.add(new paper.Point(bounds.right,point.y));

		//visibility
		if(!_grid.isActive){
			_grid.vertical.visible = false;
			_grid.horizontal.visible = false;
		}else{
			_grid.vertical.visible = true;
			_grid.horizontal.visible = true;
		}
	}

	this.removeGrid = function(point){
		if(_grid.vertical !=  null && _grid.horizontal != null){
			_grid.vertical.visible = false;
			_grid.horizontal.visible = false;
		}
		_grid.isActive = false;
	}

	this.getPointFixedToGrid = function(point){
		if(_grid.isActive && _grid.vertical !=  null && _grid.horizontal != null){
			var verticalFixedPoint = new paper.Point(_grid.vertical.getPointAt(0).x,point.y);
			var horizontalFixedPoint = new paper.Point(point.x,_grid.horizontal.getPointAt(0).y);
			/*The following should have worked...
			var verticalFixedPoint = _grid.vertical.getNearestLocation(event.point).point;
			var horizontalFixedPoint = _grid.horizontal.getNearestLocation(event.point).point;*/
			if(verticalFixedPoint.getDistance(point) < horizontalFixedPoint.getDistance(point)){
				return verticalFixedPoint;
			}else{
				return horizontalFixedPoint;
			}
		}else{
			return point;
		}
	}

	// Private Helper methods
	var convertPointsPathToSegment = function(path,isRelative){
		var points = [];
		for(var pointItr = 0, pointMax = path.segments.length; pointItr < pointMax; pointItr++){
			var point = path.segments[pointItr].point;
			if(isRelative){
				points.push(getPercentPointFromCanvas(point.x, point.y));
			}else{
				points.push(getPointFromCanvas(point.x, point.y));
			}
		}

		return points;
	}

	var getPointFromCanvas = function(canvasX, canvasY){
		var canvasPoint = _this.getPointInBounds(new paper.Point(canvasX, canvasY), _this.getBoundaries());
		var imagePosition = _this.getBoundaries();
		var x = (canvasPoint.x - imagePosition.x) / _this.getZoom();
		var y = (canvasPoint.y - imagePosition.y) / _this.getZoom();

		return {"x":x,"y":y};
	}

	var getPercentPointFromCanvas = function(canvasX, canvasY){
		var canvasPoint = _this.getPointInBounds(new paper.Point(canvasX, canvasY), _this.getBoundaries());
		var imagePosition = _this.getBoundaries();
		var x = (canvasPoint.x - imagePosition.x) / imagePosition.width;
		var y = (canvasPoint.y - imagePosition.y) / imagePosition.height;

		return {"x":x,"y":y};
	}

	this.getPointInBounds = function(point, bounds){
		if(!bounds.contains(point)){
			var boundPoint = point;
			if(point.x < bounds.left){
				boundPoint.x = bounds.left;
			}else if(point.x > bounds.right){
				boundPoint.x = bounds.right;
			}
			if(point.y < bounds.top){
				boundPoint.y = bounds.top;
			}else if(point.y > bounds.bottom){
				boundPoint.y = bounds.bottom;
			}

			return boundPoint;
		}else{
			return point;
		}
	}

	//***Inherintent functions***
	this.setImage = function(id){
		_viewer.setImage(id);
	}
	this.addSegment = function(segment,isFixed){
		_viewer.addSegment(segment,isFixed);
	}
	this.clear = function() {
		_viewer.clear();
	}
	this.updateSegment = function(segment) {
		_viewer.updateSegment(segment);
	}
	this.removeSegment = function(id){
		_viewer.removeSegment(id);
	}
	this.highlightSegment = function(id, doHighlight) {
		_viewer.highlightSegment(id, doHighlight);
	}
	this.hideSegment = function(id,doHide){
		_viewer.hideSegment(id, doHide);
	}
	this.selectSegment = function(id, doSelect) {
		_viewer.selectSegment(id, doSelect);
	}
	this.getBoundaries = function(){
		return _viewer.getBoundaries();
	}
	this.center = function() {
		_viewer.center();
	}
	this.getZoom = function(){
		return _viewer.getZoom();
	}
	this.setZoom = function(zoomfactor,paper) {
		_viewer.setZoom(zoomfactor,paper);
	}
	this.zoomIn = function(zoomfactor,paper) {
		_viewer.zoomIn(zoomfactor,paper);
	}
	this.zoomOut = function(zoomfactor,paper) {
		_viewer.zoomOut(zoomfactor,paper);
	}
	this.zoomFit = function() {
		_viewer.zoomFit();
	}
	this.movePoint = function(delta) {
		if(!_this.isEditing){
			_viewer.movePoint(delta);
		}
	}
	this.move = function(x, y) {
		if(!_this.isEditing){
			_viewer.move(x, y);
		}
	}
	this.getColor = function(segmentType){
		return _viewer.getColor(segmentType);
	}
	//Protected functions
	this.getPath = function(id){
		return _viewer.getPath(id);
	}
	this.drawPath = function(segment, doFill, info){
		_viewer.drawPath(segment, doFill, info);
	}
	this.drawPathLine = function(segment){
		_viewer.drawPathLine(segment);
	}
	this.getImageCanvas = function(){
		return _viewer.getImageCanvas();
	}
}
