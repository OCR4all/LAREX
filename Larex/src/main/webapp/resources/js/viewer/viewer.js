class Viewer {
	constructor(regionTypes, viewerInput, colors) {
		this._regionTypes = regionTypes;
		this.thisInput = viewerInput;
		this._imageID;
		this._imageWidth;
		this._imageHeight;
		this._overlayID = "overlay";
		this._overlay;
		this._polygons = {};
		this._imageCanvas = new paper.Group();
		this._background;
		this._currentZoom = 1;
		this._colors = colors;
		this._hitOptions = { segments: true, stroke: true, fill: true, tolerance: 10 };
		this._listener = [];

		document.addEventListener('visibilitychange', () => {
			if (!document.hidden) this.forceUpdate();
		});
		paper.settings.handleSize = 5;

		// Mouse Listener
		const tool = new paper.Tool();
		tool.onMouseUp = (event) => {
			let propagate = true;
			this._listener.forEach(listener => {
				if (listener.onMouseUp) {
					propagate = false;
					listener.onMouseUp(event)
				}
			});

			if(propagate){
				const hitTest = this._imageCanvas.hitTest(event.point, this._hitOptions);
				if(hitTest){
					if (hitTest.item && hitTest.item.polygonID) 
						this.thisInput.selectSection(hitTest.item.polygonID, event, hitTest);
					else
						this.thisInput.clickImage(event);
				} else {
					this.thisInput.clickBackground(event);
				}
			}
		};
		tool.onMouseDrag = (event) => {
			let propagate = true;
			this._listener.forEach(listener => {
				if (listener.onMouseDrag) {
					propagate = false;
					listener.onMouseDrag(event)
				}
			});

			if(propagate){
				const hitTest = this._imageCanvas.hitTest(event.point, this._hitOptions);
				if(hitTest)
					this.thisInput.dragImage(event);
				else 
					this.thisInput.dragBackground(event);
			}
		}

		tool.onMouseMove = (event) => {
			this._listener.forEach(listener => { if (listener.onMouseMove) listener.onMouseMove(event);});
		}
		tool.onMouseDown = (event) => {
			this._listener.forEach(listener => { if (listener.onMouseDown) listener.onMouseDown(event);});
		}
		tool.activate();
	}

	addListener(tool){
		this._listener.push(tool);
	}

	removeListener(tool){
		var index = this._listener.indexOf(tool);
		if (index > -1) this._listener.splice(index, 1);
	}

	setImage(id) {
		this._imageCanvas = new paper.Group();
		this._imageID = id;
		this._drawImage();
		this._imageCanvas.bringToFront();
	}

	addSegment(segment, isFixed=false, isStatic=false) {
		this.drawPolygon(segment, false, isFixed, isStatic);
	}

	fixSegment(polygonID, doFix = true) {
		if (doFix) {
			this._polygons[polygonID].dashArray = [5, 3];
		} else {
			this._polygons[polygonID].dashArray = [];
		}
	}

	forceUpdate() {
		// highlight segments to force paperjs/canvas to redraw everything
		if (this._polygons)
			Object.keys(this._polygons).forEach((id) => this.highlightSegment(id, false));
	}

	clear() {
		paper.project.activeLayer.removeChildren();
		this._polygons = {};
		this._currentZoom = 1;
		paper.view.draw();
		this._background = null;
		this._updateBackground();
	}

	updateSegment(segment) {
		const polygon = this._polygons[segment.id];
		if (polygon === undefined || polygon === null) {
			this.addSegment(segment);
		} else {
			polygon.removeSegments();

			//Update color
			const color = this._colors.getColor(segment.type);
			//Save old alpha
			const alphaFill = polygon.fillColor.alpha;
			const alphaStroke = polygon.strokeColor.alpha;
			const dashArray = polygon.dashArray;
			const mainAlpha = polygon.fillColor.mainAlpha;
			polygon.fillColor = new paper.Color(color);//color;
			polygon.fillColor.alpha = alphaFill;
			polygon.fillColor.mainAlpha = mainAlpha;
			polygon.strokeColor = color;
			polygon.strokeColor.alpha = alphaStroke;
			polygon.defaultStrokeColor = new paper.Color(polygon.strokeColor);
			polygon.dashArray = dashArray;

			for (const key in segment.points) {
				const sPoint = segment.points[key];
				const point = segment.isRelative ? this._convertPercentToCanvas(sPoint.x, sPoint.y)
												: this._convertGlobalToCanvas(sPoint.x, sPoint.y);
				polygon.add(new paper.Point(point.x, point.y));
			}
		}
	}

	removeSegment(id) {
		this._polygons[id].remove();
		delete this._polygons[id];
	}

	highlightSegment(id, doHighlight) {
		const polygon = this._polygons[id];
		if (polygon) {
			if (polygon.fillColor != null) {
				if (doHighlight) {
					polygon.fillColor.alpha = 0.6;
				} else {
					polygon.fillColor.alpha = polygon.fillColor.mainAlpha;
				}
			}
		}
	}

	hideSegment(id, doHide) {
		const polygon = this._polygons[id];
		if (polygon !== null) {
			polygon.visible = !doHide;
		}
	}

	selectSegment(id, doSelect, displayPoints, point, fallback = (id,point) => {}) {
		if (doSelect) {
			const polygon = this._polygons[id];
			if(!polygon)
				fallback(id)
			else{
				polygon.strokeColor = new paper.Color('#1e88e5');
				polygon.strokeWidth = 2;
				if (displayPoints) {
					this._polygons[id].selected = true;
				}
				if (point) {
					const canvasSegment = polygon.segments.find(s => {
						const realPoint = this._convertCanvasToGlobal(s.point.x, s.point.y);
						return realPoint.x === point.x && realPoint.y === point.y;
					});
					if(canvasSegment)
						canvasSegment.point.selected = true;
					else
						fallback(id,point)
				}
			}
		} else {
			const polygon = this._polygons[id];
			if(polygon){
				polygon.strokeColor = new paper.Color(polygon.defaultStrokeColor);
				polygon.strokeWidth = 1;
				this._polygons[id].selected = false;
				if (point) {
					const canvasSegment = polygon.segments.find(s => {
						const realPoint = this._convertCanvasToGlobal(s.point.x, s.point.y);
						return realPoint.x === point.x && realPoint.y === point.y;
					});
					if(canvasSegment)
						canvasSegment.point.selected = false;
					else
						fallback(id,point)
				}
			}
		}
	}

	selectPointsInbetween(pointA, pointB, polygonID) {
		const points = [];
		const rectangleAB = new paper.Rectangle(pointA, pointB);

		this._polygons[polygonID].selected = true;
		
		this._polygons[polygonID].segments.forEach(point => {
			if (rectangleAB.contains(point.point)) {
				point.point.selected = true;
				points.push(this._convertCanvasToGlobal(point.point.x, point.point.y));
			}
		});
		return points;
	}

	getSegmentIDsBetweenPoints(pointA, pointB) {
		const polygonIDs = [];
		const rectangleAB = new paper.Rectangle(pointA, pointB);

		$.each(this._polygons, (id, polygon) => {
			if (rectangleAB.contains(polygon.bounds)) {
				polygonIDs.push(id);
			}
		});
		return polygonIDs;
	}

	getBoundaries() {
		return this._imageCanvas.bounds;
	}

	// Navigation
	center() {
		this._imageCanvas.position = paper.view.center;
	}

	getZoom() {
		return this._currentZoom;
	}

	setZoom(zoomfactor, point) {
		this._imageCanvas.scale(1 / this._currentZoom);
		if (point != null) {
			this._imageCanvas.scale(zoomfactor, point);
		} else {
			this._imageCanvas.scale(zoomfactor);
		}
		this._currentZoom = zoomfactor;
	}

	zoomIn(zoomfactor, point) {
		const zoom = 1 + zoomfactor;
		if (point != null) {
			this._imageCanvas.scale(zoom, point);
		} else {
			this._imageCanvas.scale(zoom);
		}
		this._currentZoom *= zoom;
	}

	zoomOut(zoomfactor, point) {
		const zoom = 1 - zoomfactor;
		if (point != null) {
			this._imageCanvas.scale(zoom, point);
		} else {
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

	getImageCanvas() {
		return imageCanvas;
	}

	//Protected Functions (are public but should bee seen as protected)
	drawPolygon(segment, doFill, isFixed, isStatic = false) {
		//Construct polygon from segment
		const polygon = new paper.Path();
		polygon.polygonID = segment.id;
		const color = this._colors.getColor(segment.type);

		polygon.doFill = doFill;
		polygon.fillColor = new paper.Color(color);//color;
		polygon.closed = true;
		polygon.strokeColor = color;
		if (doFill) {
			polygon.fillColor.alpha = 0.4;
			polygon.strokeColor.alpha = 0.4;
		} else {
			polygon.fillColor.alpha = 0.001;
			polygon.strokeColor.alpha = 1;
			polygon.strokeWidth = 2;
		}
		polygon.defaultStrokeColor = new paper.Color(polygon.strokeColor);
		if (isFixed) {
			polygon.dashArray = [5, 3];
		}
		polygon.fillColor.mainAlpha = polygon.fillColor.alpha;

		//Convert segment points to current canvas coordinates
		if (!segment.isRelative) {
			for (const key in segment.points) {
				const point = this._convertGlobalToCanvas(segment.points[key].x, segment.points[key].y);
				polygon.add(new paper.Point(point.x, point.y));
			}
		} else {
			for (const key in segment.points) {
				const point = this._convertPercentToCanvas(segment.points[key].x, segment.points[key].y);
				polygon.add(new paper.Point(point.x, point.y));
			}
		}

		if(!isStatic){
			//Add listeners
			polygon.onMouseEnter = (event) => this.thisInput.enterSection(segment.id, event);
			polygon.onMouseLeave = (event) => this.thisInput.leaveSection(segment.id, event);
		}

		//Add to canvas
		this._imageCanvas.addChild(polygon);
		this._polygons[segment.id] = polygon;

		return polygon;
	}

	getPolygon(id) {
		return this._polygons[id];
	}

	drawLine(line) {
		//Construct polygon from segment
		const polygon = new paper.Path();
		const color = new paper.Color(1, 0, 1);

		polygon.doFill = false;
		polygon.closed = false;
		polygon.strokeColor = color;
		polygon.strokeWidth = 2;
		polygon.polygonID = line.id;

		//Convert segment points to current canvas coordinates
		for (const key in line.points) {
			const point = this._convertGlobalToCanvas(line.points[key].x, line.points[key].y);
			polygon.add(new paper.Point(point.x, point.y));
		}

		//Add listeners
		polygon.onMouseEnter = (event) => this.thisInput.enterSection(line.id, event);
		polygon.onMouseLeave = (event) => this.thisInput.leaveSection(line.id, event);

		//Add to canvas
		this._imageCanvas.addChild(polygon);
		this._polygons[line.id] = polygon;

		return polygon;
	}

	getImageCanvas() {
		return this._imageCanvas;
	}

	getImageWidth(){
		return this._imageWidth
	}

	getImageHeight(){
		return this._imageHeight;
	}

	// private helper functions
	_drawImage() {
		const image = new paper.Raster(this._imageID);
		this._imageWidth = image.width;
		this._imageHeight = image.height;
		image.style = {
			shadowColor: new paper.Color(0, 0, 0),
			// Set the shadow blur radius to 12:
			shadowBlur: 1200,
			// Offset the shadow by { x: 5, y: 5 }
			shadowOffset: new paper.Point(5, 5)
		};
		let position = new paper.Point(0, 0);
		position = position.add([image.width * 0.5, image.height * 0.5]);
		image.position = position;

		this._imageCanvas.addChild(image);

		this._updateBackground();
		return image;
	}

	showContours(contours){
		let overlayHTML = document.getElementById(this._overlayID);
		if(!overlayHTML){
			overlayHTML = document.createElement('canvas');
			document.body.appendChild(overlayHTML);
			overlayHTML.id = "overlay";
		}
		overlayHTML.width = this.getImageWidth();
		overlayHTML.height = this.getImageHeight();
		let ctx = overlayHTML.getContext("2d");

		contours.forEach((c) => {
			ctx.fillStyle = '#FF0000CC';
			ctx.beginPath();
			if(c.length > 0){
				ctx.moveTo(c[0].x, c[0].y);
				c.forEach((p) => {
					ctx.lineTo(p.x,p.y);
				});
				ctx.closePath();
				ctx.fill();
			}
		});

		//Draw Overlay
		if(document.getElementById(this._overlayID)){
			if(this._overlay) this._overlay.remove();

			this._overlay = new paper.Raster(this._overlayID);
			this._overlay.visible = true;
			const imagePosition = this._imageCanvas.bounds;
			this._overlay.position = new paper.Point(imagePosition.x,imagePosition.y);
			this._overlay.position = this._overlay.position.add([imagePosition.width * 0.5, imagePosition.height * 0.5]);
			this._overlay.scale(this._currentZoom);

			this._imageCanvas.addChild(this._overlay);
		}
	}

	hideContours(){
		if(this._overlay) this._overlay.visible = false;
	}

	_updateBackground() {
		// background
		if (!this._background) {
			this._background = new paper.Path.Rectangle({
				point: [0, 0],
				//setting dynamic while resizing caused errors -> set to high value TODO
				size: [1000000, 1000000],
				strokeColor: '#757575',
				fillColor: '#757575'
			});
			this._background.sendToBack();
		}
	}

	_convertCanvasPolygonToGlobal(polygon, isRelative) {
		const points = [];
		for (let pointItr = 0, pointMax = polygon.segments.length; pointItr < pointMax; pointItr++) {
			const point = polygon.segments[pointItr].point;
			if (isRelative) {
				points.push(this._convertCanvasToPercent(point.x, point.y));
			} else {
				points.push(this._convertCanvasToGlobal(point.x, point.y));
			}
		}

		return points;
	}

	_convertCanvasToGlobal(canvasX, canvasY) {
		const canvasPoint = this.getPointInBounds(new paper.Point(canvasX, canvasY), this.getBoundaries());
		const imagePosition = this.getBoundaries();
		const x = Math.round((canvasPoint.x - imagePosition.x) / this.getZoom());
		const y = Math.round((canvasPoint.y - imagePosition.y) / this.getZoom());

		return { "x": x, "y": y };
	}

	_convertCanvasToPercent(canvasX, canvasY) {
		const canvasPoint = this.getPointInBounds(new paper.Point(canvasX, canvasY), this.getBoundaries());
		const imagePosition = this.getBoundaries();
		const x = (canvasPoint.x - imagePosition.x) / imagePosition.width;
		const y = (canvasPoint.y - imagePosition.y) / imagePosition.height;

		return { "x": x, "y": y };
	}

	_convertGlobalToCanvas(x, y) {
		const imagePosition = this._imageCanvas.bounds;
		const canvasX = x * this._currentZoom + imagePosition.x;
		const canvasY = y * this._currentZoom + imagePosition.y;

		return { "x": canvasX, "y": canvasY };
	}

	_convertPercentToCanvas(x, y) {
		const imagePosition = this._imageCanvas.bounds;
		const canvasX = (x * imagePosition.width) + imagePosition.x;
		const canvasY = (y * imagePosition.height) + imagePosition.y;

		return { "x": canvasX, "y": canvasY };
	}
}
