class Viewer{
	constructor(segmenttypes, viewerInput, colors, specifiedColors) {
		this._segmenttypes = segmenttypes;
		this.thisInput = viewerInput;
		this._imageID;
		this._paths = {};
		this._imageCanvas = new paper.Group();
		this._background;
		this._currentZoom = 1;
		this._colors = colors;
		this._specifiedColors = specifiedColors;
		document.addEventListener('visibilitychange', () => {
			if(!document.hidden) this.forceUpdate();	
		});
	}

	setImage(id){
		this._imageCanvas = new paper.Group();
		this._imageID = id;
		this._drawImage();
		this._imageCanvas.onMouseDrag = (event) => this.thisInput.dragImage(event);
		this._imageCanvas.bringToFront();
	}

	addSegment(segment,isFixed){
		this.drawPath(segment, false, isFixed);
	}

	fixSegment(segmentID,doFix = true){
		if(doFix){
			this._paths[segmentID].dashArray = [5, 3];
		}else{
			this._paths[segmentID].dashArray = [];
		}
	}

	forceUpdate() {
		// highlight segments to force paperjs/canvas to redraw everything
		if(this._paths)	
			Object.keys(this._paths).forEach((id) => this.highlightSegment(id,false));
	}

	clear() {
		paper.project.activeLayer.removeChildren();
		this._paths = {};
		this._currentZoom = 1;
		paper.view.draw();
		this._background = null;
		this._updateBackground();
	}

	updateSegment(segment){
		const path = this._paths[segment.id];
		if(path === undefined || path === null){
			this.addSegment(segment);
		}else{
			path.removeSegments();

			//Update color
			const color = this.getColor(segment.type);
			//Save old alpha
			const alphaFill = path.fillColor.alpha;
			const alphaStroke = path.strokeColor.alpha;
			const dashArray = path.dashArray;
			const mainAlpha = path.fillColor.mainAlpha;
			path.fillColor = new paper.Color(color);//color;
			path.fillColor.alpha = alphaFill;
			path.fillColor.mainAlpha = mainAlpha;
			path.strokeColor = color;
			path.strokeColor.alpha = alphaStroke;
			path.defaultStrokeColor = new paper.Color(path.strokeColor);
			path.dashArray = dashArray;

			//Convert segment points to current canvas coordinates
			const imagePosition = this._imageCanvas.bounds;

			if(!segment.isRelative){
				for ( const key in segment.points) {
					const point = this._convertPointToCanvas(segment.points[key].x, segment.points[key].y);
					path.add(new paper.Point(point.x, point.y));
				}
			} else {
				for ( const key in segment.points) {
					const point = this._convertPercentPointToCanvas(segment.points[key].x, segment.points[key].y);
					path.add(new paper.Point(point.x, point.y));
				}
			}
		}
	}

	removeSegment(id){
		this._paths[id].remove();
		delete this._paths[id];
	}

	highlightSegment(id, doHighlight){
		const path = this._paths[id];
		if(path){
			if(path.fillColor != null){
				if(doHighlight){
					path.fillColor.alpha = 0.6;
				}else{
					path.fillColor.alpha = path.fillColor.mainAlpha;
				}
			}
		}
	}

	hideSegment(id, doHide){
		const path = this._paths[id];
		if(path !== null){
			path.visible = !doHide;
		}
	}

	selectSegment(id, doSelect, displayPoints, point){
		if(doSelect){
			const path = this._paths[id];
			path.strokeColor = new paper.Color('#1e88e5');
			path.strokeWidth = 2;
			if(displayPoints){
				this._paths[id].selected = true;
			}
			if(point){
				point.selected = true;
			}
		}else{
			const path = this._paths[id];
			path.strokeColor = new paper.Color(path.defaultStrokeColor);
			path.strokeWidth = 1;
			this._paths[id].selected = false;
			if(point){
				point.selected = false;
			}
		}
	}

	getPointsBetweenPoints(pointA,pointB,segmentID){
		const points = [];
		const rectangleAB = new paper.Rectangle(pointA,pointB);

		this._paths[segmentID].segments.forEach(point => {
			if(rectangleAB.contains(point.point)){
				points.push(point);
			}
		});
		return points;
	}

	getSegmentIDsBetweenPoints(pointA,pointB){
		const segmentIDs = [];
		const rectangleAB = new paper.Rectangle(pointA,pointB);

		$.each(this._paths, (id, path) => {
			if(rectangleAB.contains(path.bounds)){
				segmentIDs.push(id);
			}
		});
		return segmentIDs;
	}

	getBoundaries(){
		return this._imageCanvas.bounds;
	}

	// Navigation
	center() {
		this._imageCanvas.position = paper.view.center;
	}

	getZoom(){
		return this._currentZoom;
	}

	setZoom(zoomfactor, point) {
		this._imageCanvas.scale(1 / this._currentZoom);
		if(point != null){
			this._imageCanvas.scale(zoomfactor, point);
		}else{
			this._imageCanvas.scale(zoomfactor);
		}
		this._currentZoom = zoomfactor;
	}

	zoomIn(zoomfactor, point) {
		const zoom = 1 + zoomfactor;
		if(point != null){
			this._imageCanvas.scale(zoom, point);
		}else{
			this._imageCanvas.scale(zoom);
		}
		this._currentZoom *= zoom;
	}

	zoomOut(zoomfactor, point) {
		const zoom = 1 - zoomfactor;
		if(point != null){
			this._imageCanvas.scale(zoom, point);
		}else{
			this._imageCanvas.scale(zoom);
		}
		this._currentZoom *= zoom;
	}

	zoomFit() {
		// reset zoom
		this._imageCanvas.scale(1 / this._currentZoom);

		const viewSize = paper.view.viewSize;
		const imageSize = this._imageCanvas.bounds.size;

		// calculate best ratios/scales
		const scaleWidth = viewSize.width / imageSize.width;
		const scaleHeight = viewSize.height / imageSize.height;
		let scaleFit = (scaleWidth < scaleHeight ? scaleWidth : scaleHeight);
		scaleFit *= 0.9;
		
		this._imageCanvas.scale(scaleFit);
		this._currentZoom = scaleFit;
	}

	movePoint(delta) {
		this._imageCanvas.position = this._imageCanvas.position.add(delta);
	}

	move(x, y) {
		const delta = new paper.Point(x, y);
		this.movePoint(delta);
	}

	getImageCanvas(){
		return imageCanvas;
	}

	//Protected Functions (are public but should bee seen as protected)
	drawPath(segment, doFill, isFixed){
		//Construct path from segment
		const path = new paper.Path();
		const color = this.getColor(segment.type);

		path.doFill = doFill;
		path.fillColor = new paper.Color(color);//color;
		path.closed = true;
		path.strokeColor = color;
		if(doFill){
			path.fillColor.alpha = 0.4;
			path.strokeColor.alpha = 0.4;
		}else{
			path.fillColor.alpha = 0.001;
			path.strokeColor.alpha = 1;
			path.strokeWidth = 2;
		}
		path.defaultStrokeColor = new paper.Color(path.strokeColor);
		if(isFixed){
			path.dashArray = [5, 3];u
		}
		path.fillColor.mainAlpha = path.fillColor.alpha;

		//Convert segment points to current canvas coordinates
		if(!segment.isRelative){
			for ( const key in segment.points) {
				const point = this._convertPointToCanvas(segment.points[key].x, segment.points[key].y);
				path.add(new paper.Point(point.x, point.y));
			}
		} else {
			for ( const key in segment.points) {
				const point = this._convertPercentPointToCanvas(segment.points[key].x, segment.points[key].y);
				path.add(new paper.Point(point.x, point.y));
			}
		}

		//Add listeners
		path.onMouseEnter = (event) => this.thisInput.enterSection(segment.id,event);
		path.onMouseLeave = (event) => this.thisInput.leaveSection(segment.id,event);
		path.onClick = (event) => {this.thisInput.selectSection(segment.id,event,path.hitTest(event.point,{segments: true,tolerance: 10}));};

		//Add to canvas
		this._imageCanvas.addChild(path);
		this._paths[segment.id] = path;

		return path;
	}

	getPath(id){
		return this._paths[id];
	}
	drawPathLine(segment){
		//Construct path from segment
		const path = new paper.Path();
		const color = new paper.Color(1,0,1);

		path.doFill = false;
		path.closed = false;
		path.strokeColor = color;
		path.strokeWidth = 2;


		//Convert segment points to current canvas coordinates
		for ( const key in segment.points) {
			const point = this._convertPointToCanvas(segment.points[key].x, segment.points[key].y);
			path.add(new paper.Point(point.x, point.y));
		}

		//Add listeners
		path.onMouseEnter = (event) => this.thisInput.enterSection(segment.id,event);
		path.onMouseLeave = (event) => this.thisInput.leaveSection(segment.id,event);
		path.onMouseDown = (event) => this.thisInput.selectSection(segment.id,event,path.hitTest(event.point,{segments: true,tolerance: 10}));

		//Add to canvas
		this._imageCanvas.addChild(path);
		this._paths[segment.id] = path;

		return path;
	}

	getImageCanvas(){
		return this._imageCanvas;
	}

	getColor(segmentType){
		let color = this._specifiedColors[segmentType];

		if(color){
			return color;
		}else{
			const id = this._segmenttypes[segmentType]
			const counter = 6;
			const modifier1 = (id +6) % counter;
			const modifier2 = Math.floor(((id-6)/counter));
			const c = modifier2 == 0? 1 : 1-(1/modifier2);

			switch(modifier1){
			case 0: color = new paper.Color(c,0,0);
				break;
			case 1: color = new paper.Color(0,c,0);
				break;
			case 2: color = new paper.Color(0,0,c);
				break;
			case 3: color = new paper.Color(c,c,0);
				break;
			case 4: color = new paper.Color(0,c,c);
				break;
			case 5: color = new paper.Color(c,0,c);
				break;
			}

			return color;
		}
	}

	// private helper functions
	_drawImage() {
		const image = new paper.Raster(this._imageID);
		image.style = {
			shadowColor : new paper.Color(0, 0, 0),
			// Set the shadow blur radius to 12:
			shadowBlur : 1200,
			// Offset the shadow by { x: 5, y: 5 }
			shadowOffset : new paper.Point(5, 5)
		};
		let position = new paper.Point(0, 0);
		position = position.add([ image.width * 0.5, image.height * 0.5 ]);
		image.position = position;
		image.onClick = (event) => this.thisInput.clickImage(event,image.hitTest(event.point,{tolerance: 10}));

		this._imageCanvas.addChild(image);
		this._updateBackground();
		return image;
	}

	_updateBackground(){
		// background
		if(!this._background){
			this._background = new paper.Path.Rectangle({
		  	point: [0, 0],
				//setting dynamic while resizing caused errors -> set to high value TODO
		  	size: [1000000,1000000],
		  	strokeColor: '#757575',
				fillColor: '#757575'
			});
			this._background.onClick = (event) => this.thisInput.clickBackground(event);
			this._background.onMouseDrag = (event) => this.thisInput.dragBackground(event);
			this._background.sendToBack();
		}
	}

	_convertPointToCanvas(x,y){
		const imagePosition = this._imageCanvas.bounds;
		const canvasX = x * this._currentZoom + imagePosition.x;
		const canvasY = y * this._currentZoom + imagePosition.y;

		return {"x":canvasX,"y":canvasY};
	}
	_convertPercentPointToCanvas(x,y){
		const imagePosition = this._imageCanvas.bounds;
		const canvasX = (x * imagePosition.width) + imagePosition.x;
		const canvasY = (y * imagePosition.height) + imagePosition.y;

		return {"x":canvasX,"y":canvasY};
	}
}
