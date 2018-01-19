function Viewer(segmenttypes, viewerInput, colors, specifiedColors) {
	const _segmenttypes = segmenttypes;
	const _viewerInput = viewerInput;
	let _imageid;
	let _paths = {};
	let _imageCanvas = new paper.Group();
	let _background;
	let _currentZoom = 1;
	const _colors = colors;
	const _specifiedColors = specifiedColors;
	const _this = this;

	this.setImage = function(id){
		_imageCanvas = new paper.Group();
		_imageid = id;
		drawImage();
		_imageCanvas.onMouseDrag = function(event){
			_viewerInput.dragImage(event);
		}
		_imageCanvas.bringToFront();
	}

	this.addSegment = function(segment,isFixed){
		this.drawPath(segment, false, null,isFixed);
	}

	this.clear = function() {
		paper.project.activeLayer.removeChildren();
		paths = {};
		_currentZoom = 1;
		paper.view.draw();
		_background = null;
		updateBackground();
	}

	this.updateSegment = function(segment){
		const path = _paths[segment.id];
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
			const imagePosition = _imageCanvas.bounds;

			if(!segment.isRelative){
				for ( const key in segment.points) {
					const point = convetPointToCanvas(segment.points[key].x, segment.points[key].y);
					path.add(new paper.Point(point.x, point.y));
				}
			} else {
				for ( const key in segment.points) {
					const point = convetPercentPointToCanvas(segment.points[key].x, segment.points[key].y);
					path.add(new paper.Point(point.x, point.y));
				}
			}
		}
	}

	this.removeSegment = function(id){
		_paths[id].remove();
		delete _paths[id];
	}

	this.highlightSegment = function(id, doHighlight){
		const path = _paths[id];
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

	this.hideSegment = function(id, doHide){
		const path = _paths[id];
		if(path !== null){
			path.visible = !doHide;
		}
	}

	this.selectSegment = function(id, doSelect){
		if(doSelect){
			const path = _paths[id];
			path.strokeColor = new paper.Color('#1e88e5');
			path.strokeWidth = 2;
			//_paths[id].selected = true;
		}else{
			const path = _paths[id];
			path.strokeColor = new paper.Color(path.defaultStrokeColor);
			path.strokeWidth = 1;
			//_paths[id].selected = false;
		}
	}

	this.getSegmentIDsBetweenPoints = function(pointA,pointB){
		const segmentIDs = [];
		const rectangleAB = new paper.Rectangle(pointA,pointB);

		$.each(_paths, function( id, path ) {
			if(rectangleAB.contains(path.bounds)){
				segmentIDs.push(id);
			}
		});
		return segmentIDs;
	}

	this.getBoundaries = function(){
		return _imageCanvas.bounds;
	}

	// Navigation
	this.center = function() {
		_imageCanvas.position = paper.view.center;
	}

	this.getZoom = function(){
		return _currentZoom;
	}

	this.setZoom = function(zoomfactor, point) {
		_imageCanvas.scale(1 / _currentZoom);
		if(point != null){
			_imageCanvas.scale(zoomfactor, point);
		}else{
			_imageCanvas.scale(zoomfactor);
		}
		_currentZoom = zoomfactor;
	}

	this.zoomIn = function(zoomfactor, point) {
		const zoom = 1 + zoomfactor;
		if(point != null){
			_imageCanvas.scale(zoom, point);
		}else{
			_imageCanvas.scale(zoom);
		}
		_currentZoom *= zoom;
	}

	this.zoomOut = function(zoomfactor, point) {
		const zoom = 1 - zoomfactor;
		if(point != null){
			_imageCanvas.scale(zoom, point);
		}else{
			_imageCanvas.scale(zoom);
		}
		_currentZoom *= zoom;
	}

	this.zoomFit = function() {
		// reset zoom
		_imageCanvas.scale(1 / _currentZoom);

		const viewSize = paper.view.viewSize;
		const imageSize = _imageCanvas.bounds.size;

		// calculate best ratios/scales
		const scaleWidth = viewSize.width / imageSize.width;
		const scaleHeight = viewSize.height / imageSize.height;
		let scaleFit = (scaleWidth < scaleHeight ? scaleWidth : scaleHeight);
		scaleFit *= 0.9;
		
		_imageCanvas.scale(scaleFit);
		_currentZoom = scaleFit;
	}

	this.movePoint = function(delta) {
		_imageCanvas.position = _imageCanvas.position.add(delta);
	}

	this.move = function(x, y) {
		const delta = new paper.Point(x, y);
		this.movePoint(delta);
	}

	this.getImageCanvas = function(){
		return imageCanvas;
	}

	//Protected Functions (are public but should bee seen as protected)
	this.drawPath = function(segment, doFill, info, isFixed){
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
			path.dashArray = [5, 3];
		}
		path.fillColor.mainAlpha = path.fillColor.alpha;

		//Convert segment points to current canvas coordinates
		if(!segment.isRelative){
			for ( const key in segment.points) {
				const point = convetPointToCanvas(segment.points[key].x, segment.points[key].y);
				path.add(new paper.Point(point.x, point.y));
			}
		} else {
			for ( const key in segment.points) {
				const point = convetPercentPointToCanvas(segment.points[key].x, segment.points[key].y);
				path.add(new paper.Point(point.x, point.y));
			}
		}

		//Add listeners
		path.onMouseEnter = function(event) {
			_viewerInput.enterSection(segment.id,info,event);
		}
		path.onMouseLeave = function(event) {
			_viewerInput.leaveSection(segment.id,info,event);
		}
		path.onClick = function(event) {
			_viewerInput.selectSection(segment.id,info,event);
		}

		//Add to canvas
		_imageCanvas.addChild(path);
		_paths[segment.id] = path;

		return path;
	}

	//Protected Functions (are public but should bee seen as protected)
	this.getPath = function(id){
		return _paths[id];
	}
	this.drawPathLine = function(segment){
		//Construct path from segment
		const path = new paper.Path();
		const color = new paper.Color(1,0,1);

		path.doFill = false;
		path.closed = false;
		path.strokeColor = color;
		path.strokeWidth = 2;


		//Convert segment points to current canvas coordinates
		for ( const key in segment.points) {
			const point = convetPointToCanvas(segment.points[key].x, segment.points[key].y);
			path.add(new paper.Point(point.x, point.y));
		}

		//Add listeners
		path.onMouseEnter = function(event) {
			_viewerInput.enterSection(segment.id,{type:'line'},event);
		}
		path.onMouseLeave = function(event) {
			_viewerInput.leaveSection(segment.id,{type:'line'},event);
		}
		path.onMouseDown = function(event) {
			_viewerInput.selectSection(segment.id,{type:'line'},event);
		}

		//Add to canvas
		_imageCanvas.addChild(path);
		_paths[segment.id] = path;

		return path;
	}

	this.getImageCanvas = function(){
		return _imageCanvas;
	}

	this.getColor = function(segmentType){
		let color = _specifiedColors[segmentType];

		if(color){
			return color;
		}else{
			const id = _segmenttypes[segmentType]
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
	const drawImage = function() {
		const image = new paper.Raster(_imageid);
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
		image.onClick = function(event){
			_viewerInput.clickImage(event);
		}
		_imageCanvas.addChild(image);
		updateBackground();
		return image;
	}

	const updateBackground = function(){
		// background
		if(!_background){
			_background = new paper.Path.Rectangle({
		  	point: [0, 0],
				//setting dynamic while resizing caused errors -> set to high value TODO
		  	size: [1000000,1000000],
		  	strokeColor: '#757575',
				fillColor: '#757575'
			});
			_background.onClick = function(event){
				_viewerInput.clickBackground(event);
			}
			_background.onMouseDrag = function(event){
				_viewerInput.dragBackground(event);
			}
			_background.sendToBack();
		}
	}

	const convetPointToCanvas = function(x,y){
		const imagePosition = _imageCanvas.bounds;
		const canvasX = x * _currentZoom + imagePosition.x;
		const canvasY = y * _currentZoom + imagePosition.y;

		return {"x":canvasX,"y":canvasY};
	}
	const convetPercentPointToCanvas = function(x,y){
		const imagePosition = _imageCanvas.bounds;
		const canvasX = (x * imagePosition.width) + imagePosition.x;
		const canvasY = (y * imagePosition.height) + imagePosition.y;

		return {"x":canvasX,"y":canvasY};
	}
}
