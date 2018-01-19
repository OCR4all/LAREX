// Editor extends viewer
class Editor extends Viewer{
	constructor(segmenttypes, viewerInput, colors, specifiedColors, controller) {
		super(segmenttypes,viewerInput,constructor,specifiedColors);
		this.isEditing = false;
		this._controller = controller;
		this._editMode = -1; // -1 default, 0 Polygon, 1 Rectangle, 2 Border, 3 Line, 4 Move, 5 Scale
		this._tempPathType;
		this._tempPath;
		this._tempPoint;
		this._tempID;
		this._tempMouseregion;
		this._tempEndCircle;
		this._grid = {isActive:false};
		this._readingOrder;
		this._guiOverlay = new paper.Group();
		this.mouseregions = {TOP:0,BOTTOM:1,LEFT:2,RIGHT:3,MIDDLE:4,OUTSIDE:5};
	}
	startRectangleSelect() {
		if(this.isEditing === false){
			this._editMode = -1;
			this.isEditing = true;

			const tool = new paper.Tool();
			tool.activate();
			let isActive = false;
			const _editor = this;
			tool.onMouseMove = function(event) {
				if(!isActive){
					isActive = true;
					_editor.createResponsiveRectangle("endRectangleSelect",event.point,true);
				}
				this.remove();
			}
		}
	}

	endRectangleSelect() {
		if(this.isEditing){
			this.isEditing = false;
			if(this._tempPath != null){
				const selectBounds = this._tempPath.bounds;
				this._controller.rectangleSelect(selectBounds.topLeft,selectBounds.bottomRight);

				this._tempPath.remove();
				this._tempPath = null;
			}
			if(this._tempPoint != null){
				this._tempPoint.clear();
				this._tempPoint = null;
			}
		}
	}

	addRegion(region){
		this.drawPath(region, true, {type: 'region'});
	}

	addLine(line){
		this.drawPathLine(line);
	}

	removeLine(lineID){
		this.removeSegment(lineID);
	}

	removeRegion(regionID){
		this.removeSegment(regionID);
	}

	startCreatePolygon(type) {
		if(this.isEditing === false){
			this._editMode = 0;
			this.isEditing = true;
			this._tempPathType = type;
			document.body.style.cursor = "copy";

			const _editor = this;
			const tool = new paper.Tool();
			tool.activate();
			tool.onMouseMove = function(event) {
				if(_editor._tempPath){
					_editor._tempPath.removeSegment(_editor._tempPath.segments.length - 1);
					_editor._tempPath.add(_editor.getPointInBounds(event.point, _editor.getBoundaries()));
				}
			}

			tool.onMouseDown = function(event) {
				if(_editor.isEditing === true){
					const canvasPoint = _editor.getPointInBounds(event.point, _editor.getBoundaries());

					if (!_editor._tempPath) {
						// Start path
						_editor._tempPath = new paper.Path();
						_editor._tempPath.add(new paper.Point(canvasPoint)); //Add Point for mouse movement
						_editor._tempPath.fillColor = 'grey';
						_editor._tempPath.opacity = 0.3;
						_editor._tempPath.closed = false;
						_editor._tempPath.selected = true;

						// circle to end the path
						_editor._tempEndCircle = new paper.Path.Circle(canvasPoint, 5);
						_editor._tempEndCircle.strokeColor = 'black';
						_editor._tempEndCircle.fillColor = 'grey';
						_editor._tempEndCircle.opacity = 0.5;
						_editor._tempEndCircle.onMouseDown = function(event) {
							_editor.endCreatePolygon();
							this.remove();
						}

						let imageCanvas = _editor.getImageCanvas();
						imageCanvas.addChild(_editor._tempPath);
						imageCanvas.addChild(_editor._tempEndCircle);
					}
					_editor._tempPath.add(new paper.Point(canvasPoint));
				}else{
					this.remove();
				}
			}
		}
	}

	endCreatePolygon() {
		if(this.isEditing){
			this.isEditing = false;
			if(this._tempPath != null){
				this._tempPath.closed = true;
				this._tempPath.selected = false;
				if(this._tempPathType === 'segment'){
					this._controller.callbackNewFixedSegment(this._convertPointsPathToSegment(this._tempPath,false));
				}else{
					this._controller.callbackNewRegion(this._convertPointsPathToSegment(this._tempPath,true));
				}
				this._tempPath.remove();
				this._tempPath = null;
				this._tempEndCircle.remove();
			}
			document.body.style.cursor = "auto";
		}
	}

	startCreateRectangle(type) {
		if(this.isEditing === false){
			this._editMode = 1;
			this.isEditing = true;
			this._tempPathType = type;
			document.body.style.cursor = "copy";

			const _editor = this;
			const tool = new paper.Tool();
			tool.activate();
			tool.onMouseDown = function(event) {
				if(_editor.isEditing === true){
					_editor.createResponsiveRectangle("endCreateRectangle",event.point);
				}else{
					this.remove();
				}
			}
		}
	}

	endCreateRectangle() {
		if(this.isEditing){
			this.isEditing = false;
			if(this._tempPath != null){
				this._tempPath.closed = true;
				this._tempPath.selected = false;
				switch(this._tempPathType){
					case 'segment':
						this._controller.callbackNewFixedSegment(this._convertPointsPathToSegment(this._tempPath,false));
						break;
					case 'region':
						this._controller.callbackNewRegion(this._convertPointsPathToSegment(this._tempPath,true));
						break;
					case 'ignore':
						this._controller.callbackNewRegion(this._convertPointsPathToSegment(this._tempPath,true),'ignore');
						break;
					case 'roi':
					default:
						this._controller.callbackNewRoI(this._convertPointsPathToSegment(this._tempPath,true));
						break;
				}
				this._tempPath.remove();
				this._tempPath = null;
			}
			if(this._tempPoint != null){
				this._tempPoint.clear();
				this._tempPoint = null;
			}
			document.body.style.cursor = "auto";
		}
	}

	startCreateLine() {
		if(this.isEditing === false){
			this._editMode = 3;
			this.isEditing = true;
			document.body.style.cursor = "copy";

			const tool = new paper.Tool();
			const _editor = this;
			tool.activate();
			tool.onMouseMove = function(event) {
				if(_editor._tempPath){
					_editor._tempPath.removeSegment(_editor._tempPath.segments.length - 1);
					_editor._tempPath.add(_editor.getPointInBounds(event.point, _editor.getBoundaries()));
				}
			}

			tool.onMouseDown = function(event) {
				if(_editor.isEditing === true){
					const canvasPoint = _editor.getPointInBounds(event.point, _editor.getBoundaries());

					if (!_editor._tempPath) {
						// Start path
						_editor._tempPath = new paper.Path();
						_editor._tempPath.add(new paper.Point(canvasPoint)); //Add Point for mouse movement
						_editor._tempPath.strokeColor = new paper.Color(0,0,0);
						_editor._tempPath.closed = false;
						_editor._tempPath.selected = true;

						_editor.getImageCanvas().addChild(_editor._tempPath);
					}
					_editor._tempPath.add(new paper.Point(canvasPoint));
				}else{
					this.remove();
				}
			}
		}
	}

	endCreateLine() {
		if(this.isEditing){
			this.isEditing = false;

			if(this._tempPath != null){
				this._tempPath.closed = false;
				this._tempPath.selected = false;
				this._controller.callbackNewCut(this._convertPointsPathToSegment(this._tempPath,false));

				this._tempPath.remove();
				this._tempPath = null;
			}
			document.body.style.cursor = "auto";
		}
	}

	startCreateBorder(type) {
		if(this.isEditing === false){
			this.isEditing = true;
			this._editMode = 2;
			this._tempPathType = type;

			const tool = new paper.Tool();
			tool.activate();

			if (!this._tempPath) {
				// Start path
				this._tempPath = new paper.Path();
				this._tempPath.fillColor = 'grey';
				this._tempPath.opacity = 0.5;
				this._tempPath.closed = true;
				//this._tempPath.selected = true;

				this.getImageCanvas().addChild(this._tempPath);
				const _editor = this;
				tool.onMouseMove = function(event) {
					if(_editor.isEditing === true){
						if (_editor._tempPath) {
							const boundaries = _editor.getBoundaries();
							const mouseregion = _editor.getMouseRegion(boundaries,event.point);
							_editor._tempMouseregion = mouseregion;

							let topleft, topright, rectangle, bottommouse, mouseright;
							switch(mouseregion){
							case _editor.mouseregions.LEFT:
								document.body.style.cursor = "col-resize";

								topleft = new paper.Point(boundaries.left,boundaries.top);
								bottommouse = new paper.Point(event.point.x, boundaries.bottom);
								rectangle = new paper.Path.Rectangle(topleft, bottommouse);

								_editor._tempPath.segments = rectangle.segments;
								break;
							case _editor.mouseregions.RIGHT:
								document.body.style.cursor = "col-resize";

								topright = new paper.Point(boundaries.right,boundaries.top);
								bottommouse = new paper.Point(event.point.x, boundaries.bottom);
								rectangle = new paper.Path.Rectangle(topright, bottommouse);

								_editor._tempPath.segments = rectangle.segments;
								break;
							case _editor.mouseregions.TOP:
								document.body.style.cursor = "row-resize";

								topleft = new paper.Point(boundaries.left,boundaries.top);
								mouseright = new paper.Point(boundaries.right, event.point.y);
								rectangle = new paper.Path.Rectangle(topleft, mouseright);

								_editor._tempPath.segments = rectangle.segments;
								break;
							case _editor.mouseregions.BOTTOM:
								document.body.style.cursor = "row-resize";

								bottomleft = new paper.Point(boundaries.left,boundaries.bottom);
								mouseright = new paper.Point(boundaries.right, event.point.y);
								rectangle = new paper.Path.Rectangle(bottomleft, mouseright);

								_editor._tempPath.segments = rectangle.segments;
								break;
							case _editor.mouseregions.MIDDLE:
							default:
								_editor._tempPath.removeSegments();
								document.body.style.cursor = "copy";
								break;
							}
						}
					}
				}
				tool.onMouseDown = function(event) {
					if(_editor._tempPath){
						_editor.endCreateBorder();
						this.remove();
					}
				}
			}
		}
	}

	endCreateBorder() {
		if(this.isEditing){
			this.isEditing = false;

			if(this._tempPath != null){
				if(this._tempPathType === 'segment'){
					this._controller.callbackNewFixedSegment(this._convertPointsPathToSegment(this._tempPath,false));
				}else{
					this._controller.callbackNewRegion(this._convertPointsPathToSegment(this._tempPath,true));
				}

				this._tempPath.remove();
				this._tempPath = null;
			}
			document.body.style.cursor = "auto";
		}
	}

	startMovePath(pathID,type) {
		if(this.isEditing === false){
			this._editMode = 4;
			this.isEditing = true;
			this._tempPathType = type;
			document.body.style.cursor = "copy";

			// Create Copy of movable
			this._tempPath = new paper.Path(this.getPath(pathID).segments);
			this._tempID = pathID;
			this._tempPath.fillColor = 'grey';
			this._tempPath.opacity = 0.3;
			this._tempPath.closed = true;
			this._tempPath.strokeColor = 'black';
			this._tempPath.dashArray = [5, 3];

			// Set Grid
			this.setGrid(this._tempPath.position);

			// Position letiables between old and new path position
			this._tempPoint = new paper.Point(0,0);
			const oldPosition = new paper.Point(this._tempPath.position);
			let oldMouse = null;

			const tool = new paper.Tool();
			const _editor = this;
			tool.activate();
			tool.onMouseMove = function(event) {
				if(_editor.isEditing === true){
					if(oldMouse === null){
						oldMouse = event.point;
					}
					_editor._tempPoint = oldPosition.add(event.point.subtract(oldMouse));
					if(!_editor._grid.isActive){
						_editor._grid.vertical.visible = false;
						_editor._grid.horizontal.visible = false;
					}else{
						_editor._tempPoint = _editor.getPointFixedToGrid(_editor._tempPoint);
						_editor._grid.vertical.visible = true;
						_editor._grid.horizontal.visible = true;
					}
					_editor._tempPath.position = _editor._tempPoint;

					// Correct to stay in viewer bounds
					const tempPathBounds = _editor._tempPath.bounds;
					const pictureBounds = _editor.getBoundaries();
					let correctionPoint = new paper.Point(0,0);
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
					_editor._tempPoint = _editor._tempPoint.add(correctionPoint);
					_editor._tempPath.position = _editor._tempPoint;
				}else{
					this.remove();
				}
			}
			tool.onMouseDown = function(event) {
				if(_editor.isEditing === true){
					_editor.endMovePath();
				}
				this.remove();
			}
		}
	}

	endMovePath() {
		if(this.isEditing){
			this.isEditing = false;

			if(this._tempPath != null){
				if(this._tempPathType === 'segment'){
					this._controller.transformSegment(this._tempID,this._convertPointsPathToSegment(this._tempPath,false));
				}else{
					this._controller.transformRegion(this._tempID,this._convertPointsPathToSegment(this._tempPath,true));
				}

				this._tempPath.remove();
				this._tempPath = null;
			}
			//hide grid
			this._grid.vertical.visible = false;
			this._grid.horizontal.visible = false;

			document.body.style.cursor = "auto";
		}
	}

	startScalePath(pathID,type) {
		if(this.isEditing === false){
			this._editMode = 5;
			this.isEditing = true;
			this._tempPathType = type;

			// Create Copy of movable
			const boundaries = this.getPath(pathID).bounds;
			this._tempPath = new paper.Path.Rectangle(boundaries);
			this._tempID = pathID;
			this._tempPath.fillColor = 'grey';
			this._tempPath.opacity = 0.3;
			this._tempPath.closed = true;
			this._tempPath.strokeColor = 'black';
			this._tempPath.dashArray = [5, 3];

			const _editor = this;
			const tool = new paper.Tool();
			tool.activate();
			tool.onMouseMove = function(event) {
				if(_editor.isEditing === true){
					if(_editor._tempPath){
						const mouseregion = _editor.getMouseRegion(_editor._tempPath.bounds,event.point,0.1,10);
						_editor._tempMouseregion = mouseregion;

						switch(mouseregion){
						case _editor.mouseregions.LEFT:
						case _editor.mouseregions.RIGHT:
							document.body.style.cursor = "col-resize";
							break;
						case _editor.mouseregions.TOP:
						case _editor.mouseregions.BOTTOM:
							document.body.style.cursor = "row-resize";
							break;
						case _editor.mouseregions.MIDDLE:
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
				if(_editor.isEditing === true){
					_editor.scalePath(_editor._tempPath,_editor._tempMouseregion);
				}
				this.remove();
			}
		}
	}

	createResponsiveRectangle(endFunction,startPoint,boundless){
			const imageCanvas = this.getImageCanvas();

			const _editor = this;
			const tool = new paper.Tool();
			tool.activate();

			const canvasPoint = boundless ? startPoint: this.getPointInBounds(startPoint, this.getBoundaries());
			// Start path
			this._tempPoint = new paper.Path(canvasPoint);
			imageCanvas.addChild(this._tempPoint);

			this._tempPath = new paper.Path();
			this._tempPath.add(this._tempPoint); //Add Point for mouse movement
			this._tempPath.fillColor = 'grey';
			this._tempPath.opacity = 0.3;
			this._tempPath.closed = true;
			this._tempPath.selected = true;

			tool.onMouseMove = function(event) {
				if(_editor.isEditing === true){
					if (_editor._tempPath) {
						const point = boundless ? event.point : _editor.getPointInBounds(event.point, _editor.getBoundaries());
						let rectangle = new paper.Path.Rectangle(_editor._tempPoint.firstSegment.point, point);

						_editor._tempPath.segments = rectangle.segments;
					}
				}else{
					switch(endFunction){
						case "endRectangleSelect":
							_editor.endRectangleSelect();
							break;
						case "endCreateRectangle":
							_editor.endCreateRectangle();
							break;
					}
					this.remove();
				}
			}
			imageCanvas.addChild(this._tempPath);

			tool.onMouseUp = function(event) {
				switch(endFunction){
					case "endRectangleSelect":
						_editor.endRectangleSelect();
						break;
					case "endCreateRectangle":
						_editor.endCreateRectangle();
						break;
				}
				this.remove();
			}
	}

	scalePath(path,mouseregion){
		const _editor = this;
		const tool = new paper.Tool();
		tool.activate();
		tool.onMouseMove = function(event) {
			if(_editor.isEditing === true){
				if(_editor._tempPath){
					const mouseinbound = _editor.getPointInBounds(event.point,_editor.getBoundaries());

					switch(mouseregion){
					case _editor.mouseregions.LEFT:
						if(mouseinbound.x < path.bounds.right){
							path.bounds.left = mouseinbound.x;
							document.body.style.cursor = "col-resize";
						}
						break;
					case _editor.mouseregions.RIGHT:
						if(mouseinbound.x > path.bounds.left){
							path.bounds.right = mouseinbound.x;
							document.body.style.cursor = "col-resize";
						}
						break;
					case _editor.mouseregions.TOP:
						if(mouseinbound.y < path.bounds.bottom){
							path.bounds.top = mouseinbound.y;
							document.body.style.cursor = "row-resize";
						}
						break;
					case _editor.mouseregions.BOTTOM:
						if(mouseinbound.y > path.bounds.top){
							path.bounds.bottom = mouseinbound.y;
							document.body.style.cursor = "row-resize";
						}
						break;
					case _editor.mouseregions.MIDDLE:
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
			if(_editor.isEditing === true){
				_editor.endScalePath();
			}
			this.remove();
		}
	}

	endScalePath() {
		if(this.isEditing){
			this.isEditing = false;

			if(this._tempPath != null){
				const path = new paper.Path(this.getPath(this._tempID).segments);
				path.bounds = this._tempPath.bounds;

				if(this._tempPathType === 'segment'){
					this._controller.transformSegment(this._tempID,this._convertPointsPathToSegment(path,false));
				}else{
					this._controller.transformRegion(this._tempID,this._convertPointsPathToSegment(path,true));
				}

				this._tempPath.remove();
				this._tempPath = null;
			}

			document.body.style.cursor = "auto";
		}
	}

	getMouseRegion(bounds,mousepos,percentarea,minarea){
		minarea = minarea? minarea : 0;

		const width = bounds.width;
		const height = bounds.height;
		if(percentarea == null){
			percentarea = 0.4;
		}
		//Calculate the height and width delta from the borders inwards to the center with minarea and percentarea 
		let widthDelta = width*percentarea;
		if(widthDelta < minarea){
			if(minarea < width*0.5){
				widthDelta = minarea;
			}else{
				widthDelta = width*0.5;
			}
		}
		let heightDelta = height*percentarea;
		if(heightDelta < minarea){
			if(minarea < height*0.5){
				heightDelta = minarea;
			}else{
				heightDelta = height*0.5;
			}
		}

		const leftmin = bounds.left;
		const leftmax = leftmin + widthDelta;

		const rightmax = bounds.right;
		const rightmin = rightmax - widthDelta;

		const topmin = bounds.top;
		const topmax = topmin + heightDelta;

		const bottommax = bounds.bottom;
		const bottommin = bottommax - heightDelta;
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

	endEditing(doAbbord){
		if(!doAbbord){
			if(this.isEditing){
				switch(this._editMode){
					case 0:
						this.endCreatePolygon();
						break;
					case 1:
						this.endCreateRectangle();
						break;
					case 2:
						this.endCreateBorder();
						break;
					case 3:
						this.endCreateLine();
						break;
					case 4:
						this.endMovePath();
						break;
					case 5:
						this.endMovePath();
						break;
					default:
						break;
				}
			}
		}else{
			this.isEditing = false;

			this._tempID = null;
			if(this._tempPath != null){
				this._tempPath.remove();
				this._tempPath = null;
			}
			this._tempPoint = null;

			if(this._tempEndCircle){
				this._tempEndCircle.remove();
				this._tempEndCircle = null;
			}

			document.body.style.cursor = "auto";
		}
	}

	getPointInBounds(point, bounds){
		if(!bounds.contains(point)){
			let boundPoint = point;
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

	addGrid(){
		this._grid.isActive = true;
	}

	setGrid(point){
		if(this._grid.vertical ==  null || this._grid.horizontal == null){
			this._grid.vertical = new paper.Path.Line();
			this._grid.vertical.strokeColor = 'black';
			this._grid.vertical.dashArray = [3, 3];

			this._grid.horizontal = new paper.Path.Line();
			this._grid.horizontal.strokeColor = 'black';
			this._grid.horizontal.dashArray = [3, 3];
		}
		const bounds = paper.view.bounds;
		this._grid.vertical.removeSegments();
		this._grid.vertical.add(new paper.Point(point.x,bounds.top));
		this._grid.vertical.add(new paper.Point(point.x,bounds.bottom));

		this._grid.horizontal.removeSegments();
		this._grid.horizontal.add(new paper.Point(bounds.left,point.y));
		this._grid.horizontal.add(new paper.Point(bounds.right,point.y));

		//visibility
		if(!this._grid.isActive){
			this._grid.vertical.visible = false;
			this._grid.horizontal.visible = false;
		}else{
			this._grid.vertical.visible = true;
			this._grid.horizontal.visible = true;
		}
	}

	removeGrid(point){
		if(this._grid.vertical !=  null && this._grid.horizontal != null){
			this._grid.vertical.visible = false;
			this._grid.horizontal.visible = false;
		}
		this._grid.isActive = false;
	}

	getPointFixedToGrid(point){
		if(this._grid.isActive && this._grid.vertical !=  null && this._grid.horizontal != null){
			const verticalFixedPoint = new paper.Point(this._grid.vertical.getPointAt(0).x,point.y);
			const horizontalFixedPoint = new paper.Point(point.x,this._grid.horizontal.getPointAt(0).y);
			if(verticalFixedPoint.getDistance(point) < horizontalFixedPoint.getDistance(point)){
				return verticalFixedPoint;
			}else{
				return horizontalFixedPoint;
			}
		}else{
			return point;
		}
	}

	displayReadingOrder(readingOrder){
		if(!this._readingOrder){
			this._readingOrder = new paper.Path();
			this._readingOrder.strokeColor = 'indigo';
			this._readingOrder.strokeWidth = 2;
		}
		this.getImageCanvas().addChild(this._readingOrder);
		this._readingOrder.visible = true;
		this._guiOverlay.visible = true;
		this._readingOrder.removeSegments();
		this._guiOverlay.removeChildren();

		for(let index = 0; index < readingOrder.length; index++){
			const segment = this.getPath(readingOrder[index].id);
			if(segment){
				this._readingOrder.add(new paper.Segment(segment.bounds.center));
				const text = new paper.PointText({
					point: segment.bounds.center,
					content: index,
					fillColor: 'white',
					strokeColor: 'black',
					fontFamily: 'Courier New',
					fontWeight: 'bold',
					fontSize: '16pt',
					strokeWidth: 1
				});

				this._guiOverlay.addChild(text);
			}
		}
	}

	hideReadingOrder(){
		if(this._readingOrder){
			this._readingOrder.visible = false;
			this._guiOverlay.visible = false;
		}
	}

	getSortedReadingOrder(readingOrder){
		const centers = {};
		for(let index = 0; index < readingOrder.length; index++){
			const id = readingOrder[index].id;
			centers[id] = this.getPath(id).bounds.center;
		}

		readingOrder.sort(function(a,b){
			const centerA = centers[a.id];
			const centerB = centers[b.id];
			const delta = centerA.y - centerB.y;
			if(delta != 0){
				return delta;
			}else{
				return centerA.x - centerB.x;
			}
		});

		return readingOrder;
	}

	// Private Helper methods
	_convertPointsPathToSegment(path,isRelative){
		const points = [];
		for(let pointItr = 0, pointMax = path.segments.length; pointItr < pointMax; pointItr++){
			const point = path.segments[pointItr].point;
			if(isRelative){
				points.push(this._getPercentPointFromCanvas(point.x, point.y));
			}else{
				points.push(this._getPointFromCanvas(point.x, point.y));
			}
		}

		return points;
	}

	_getPointFromCanvas(canvasX, canvasY){
		const canvasPoint = this.getPointInBounds(new paper.Point(canvasX, canvasY), this.getBoundaries());
		const imagePosition = this.getBoundaries();
		const x = (canvasPoint.x - imagePosition.x) / this.getZoom();
		const y = (canvasPoint.y - imagePosition.y) / this.getZoom();

		return {"x":x,"y":y};
	}

	_getPercentPointFromCanvas(canvasX, canvasY){
		const canvasPoint = this.getPointInBounds(new paper.Point(canvasX, canvasY), this.getBoundaries());
		const imagePosition = this.getBoundaries();
		const x = (canvasPoint.x - imagePosition.x) / imagePosition.width;
		const y = (canvasPoint.y - imagePosition.y) / imagePosition.height;

		return {"x":x,"y":y};
	}

	getPointInBounds(point, bounds){
		if(!bounds.contains(point)){
			const boundPoint = point;
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

	_fixGuiTextSize(){
		this._guiOverlay.children.forEach(function(element) {
			element.scaling = new paper.Point(1,1);
		}, this);
		this._guiOverlay.bringToFront();
	}

	//***Inherintent functions***
	setImage(id){
		super.setImage(id);
		this.getImageCanvas().addChild(this._guiOverlay);
	}
	setZoom(zoomfactor,point) {
		super.setZoom(zoomfactor,point);
		this._fixGuiTextSize();
	}
	zoomIn(zoomfactor,point) {
		super.zoomIn(zoomfactor,point);
		this._fixGuiTextSize();
	}
	zoomOut(zoomfactor,point) {
		super.zoomOut(zoomfactor,point);
		this._fixGuiTextSize();
	}
	zoomFit() {
		super.zoomFit();
		this._fixGuiTextSize();
	}
}
