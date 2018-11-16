/* The viewer is a display for result segments, region segments and contours of any kind. 
 * It can handle inputs by forwarding it to a input manager (ViewerInput) 
 * All functionality about viewing elements in the viewer is handled here. 
 * It does not handle editing these elements.
 * (Contours are not drawn in paperjs as of performance overhead reasons) */
var SelectMode = {POLYGON:'polygon',CONTOUR:'contour'}
class Viewer {
	constructor(viewerInput, colors) {
		this.thisInput = viewerInput;
		this._imageID;
		this._imageWidth;
		this._imageHeight;
		this._polygons = {};
		this._imageCanvas = new paper.Group();
		this._regionOverlay;
		this._contourOverlayID = "overlay";
		this._contourOverlay;
		this._background;
		this._currentZoom = 1;
		this._colors = colors;
		this._hitOptions = { segments: true, stroke: true, fill: true, tolerance: 10 };
		this._highlighted = null;
		this._listener = [];
		this._contours = [];
		this._contourBounds = []; // Sorted list (top->left->bottom->right) of object of contour id + contour bound
		this.selectMode = SelectMode.POLYGON;

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
					const doPropagate = listener.onMouseUp(event);
					if(!doPropagate)
						propagate = false;
				}
			});

			// Do not propagate unless all child listener say otherwise
			if(propagate){
				let hitResult = false;
				if(this.selectMode == SelectMode.POLYGON){
					// Check regions first
					hitResult = this._regionOverlay ? this._regionOverlay.hitTest(event.point, this._hitOptions) : null;

					// Check segments after
					if(!hitResult)
						hitResult = this._imageCanvas ? this._imageCanvas.hitTest(event.point, this._hitOptions) : null;
				} else if(this.selectMode == SelectMode.CONTOUR){
					hitResult = this._contourOverlay ? this.contourHitTest(event.point) : null;
				} else {
					throw new ValueError('Unkown selection mode: '+this.selectMode)
				}
				if(hitResult){
					if (hitResult.item && hitResult.item.elementID) 
						this.thisInput.selectElement(hitResult.item.elementID, event, hitResult, this.selectMode);
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
					const doPropagate = listener.onMouseDrag(event)
					if(!doPropagate)
						propagate = false;
				}
			});

			// Do not propagate unless all child listener say otherwise
			if(propagate){
				if(this.selectMode == SelectMode.POLYGON){
					const hitResult = this._imageCanvas ? this._imageCanvas.hitTest(event.point, this._hitOptions) : null;
					if(hitResult)
						this.thisInput.dragImage(event);
					else 
						this.thisInput.dragBackground(event);
				} else if(this.selectMode == SelectMode.CONTOUR){


				} else {
					throw new ValueError('Unkown selection mode: '+this.selectMode)
				}
			}
		}

		tool.onMouseMove = (event) => {
			let propagate = true;
			this._listener.forEach(listener => {
				 if (listener.onMouseMove) {
					const doPropagate = listener.onMouseMove(event);
					if(!doPropagate)
						propagate = false;
				 }
			});

			// Do not propagate unless all child listener say otherwise
			if(propagate){
				if(this.selectMode == SelectMode.POLYGON){
					// Check regions first
					let hitResult = this._regionOverlay ? this._regionOverlay.hitTest(event.point, this._hitOptions) : null;

					// Check segments after
					if(!hitResult)
						hitResult = this._imageCanvas ? this._imageCanvas.hitTest(event.point, this._hitOptions) : null;

					if(hitResult){
						const new_highlight = hitResult.item ? hitResult.item.elementID : null;

						if(this._highlighted && new_highlight !== this._highlighted)
							this.thisInput.leaveElement(this._highlighted);
						
						if(new_highlight)
							this.thisInput.enterElement(new_highlight);

						this._highlighted = new_highlight;

					} else if(this._highlighted) {
						this.thisInput.leaveElement(this._highlighted);
						this._highlighted = null;
					}
				} else if(this.selectMode == SelectMode.CONTOUR){


				} else {
					throw new ValueError('Unkown selection mode: '+this.selectMode)
				}
			}
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

	clearListener(){
		this._listener = [];
	}

	setImage(id) {
		this._imageCanvas = new paper.Group();
		this._imageID = id;
		this._drawImage();
		this._imageCanvas.bringToFront();

		// Create region canvas
		this._regionOverlay = this._createEmptyOverlay();
		this._imageCanvas.addChild(this._regionOverlay);
	}
	

	clear() {
		paper.project.activeLayer.removeChildren();
		this._polygons = {};
		this._currentZoom = 1;
		paper.view.draw();
		this._background = null;
		this._updateBackground();
	}

	forceUpdate() {
		// highlight segments to force paperjs/canvas to redraw everything
		if (this._polygons)
			Object.keys(this._polygons).forEach((id) => this.highlightSegment(id, false));
	}

	addSegment(segment, isFixed=false) {
		this.drawPolygon(segment, false, isFixed);
	}

	removeSegment(id) {
		this._polygons[id].remove();
		delete this._polygons[id];
	}

	fixSegment(elementID, doFix = true) {
		if (doFix) {
			this._polygons[elementID].dashArray = [5, 3];
		} else {
			this._polygons[elementID].dashArray = [];
		}
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

	highlightSegment(id, doHighlight = true) {
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

	addRegion(region) {
		this.drawPolygon(region, true, false, this._regionOverlay);
	}

	addLine(line) {
		this.drawLine(line);
	}

	removeLine(lineID) {
		this.removeSegment(lineID);
	}

	removeRegion(regionID) {
		this.endEditing();
		this.removeSegment(regionID);
	}

	hideSegment(id, doHide = true) {
		const polygon = this._polygons[id];
		if (polygon !== null) {
			polygon.visible = !doHide;
		}
	}

	selectSegment(id, doSelect = true, selectPoints = false) {
		const polygon = this._polygons[id];
		if(polygon){
			if (doSelect) {
				polygon.strokeColor = new paper.Color('#1e88e5');
				polygon.strokeWidth = 2;
				this._polygons[id].selected = selectPoints;
			} else {
				polygon.strokeColor = new paper.Color(polygon.defaultStrokeColor);
				polygon.strokeWidth = 1;
				this._polygons[id].selected = selectPoints;
			}
		}
	}

	selectSegmentPoints(id, points, fallback = (id,points) => {}){
		const polygon = this._polygons[id];
		if(!polygon)
			throw Error("Segment does not exist.")

		this._polygons[id].selected = true;

		const pointsToSelect = points.slice(0);
		polygon.segments.some(s => {
			if(pointsToSelect.length == 0)
				true; // End loop, since no points to select
			else{
				const globalPoint = this._convertCanvasToGlobal(s.point.x, s.point.y);
				
				// Select if in pointsToSelect and remove from pointsToSelect
				const pointsToSelectIndex = pointsToSelect.findIndex(point => {return (globalPoint.x == point.x && globalPoint.y == point.y);});	
				if(pointsToSelectIndex > -1){
					s.point.selected = true
					pointsToSelect.splice(pointsToSelect,1);
				}
			}
		});
	
		if(pointsToSelect.length > 0)
			fallback(id,pointsToSelect);
	}

	selectPointsInbetween(pointA, pointB, elementID) {
		const points = [];
		const rectangleAB = new paper.Rectangle(pointA, pointB);

		this._polygons[elementID].selected = true;
		
		this._polygons[elementID].segments.forEach(point => {
			if (rectangleAB.contains(point.point)) {
				point.point.selected = true;
				points.push(this._convertCanvasToGlobal(point.point.x, point.point.y));
			}
		});
		return points;
	}

	getSegmentIDsBetweenPoints(pointA, pointB) {
		const elementIDs = [];
		const rectangleAB = new paper.Rectangle(pointA, pointB);

		$.each(this._polygons, (id, polygon) => {
			if (rectangleAB.contains(polygon.bounds)) {
				elementIDs.push(id);
			}
		});
		return elementIDs;
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
	drawPolygon(segment, doFill, isFixed, canvas = this._imageCanvas) {
		//Construct polygon from segment
		const polygon = new paper.Path();
		polygon.elementID = segment.id;
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
			polygon.strokeWidth = 1;
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

		//Add to canvas
		canvas.addChild(polygon);
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
		polygon.elementID = line.id;

		//Convert segment points to current canvas coordinates
		for (const key in line.points) {
			const point = this._convertGlobalToCanvas(line.points[key].x, line.points[key].y);
			polygon.add(new paper.Point(point.x, point.y));
		}

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

	setContours(contours, display=false){
		this._contours = contours;
		this._contourBounds = [];
		for(let [id,contour] of contours.entries()){
			let left,top = Number.MAX_VALUE;
			let right,bottom = Number.MIN_VALUE;
			for(let point in contour){
				if(point.x < left) left = point.x;
				if(right < point.x) right = point.x;
				if(point.y < top) top = point.y;
				if(bottom < point.y) bottom = point.y;
				this._contourBounds.push({id:id,bounds:{left:left,right:right,top:top,bottom:bottom}});
			}
		}
		// Sort by top -> left -> bottom -> right
		this._contourBounds = this._contourBounds.sort((a,b) => {
			const boundA = a.bound;
			const boundB = b.bound;	
			let compate = boundA.top - boundB.top;
			if(compare != 0) return compare;
			compate = boundA.left - boundB.left;
			if(compare != 0) return compare;
			compate = boundA.bottom - boundB.bottom;
			if(compare != 0) return compare;
			compate = boundA.right - boundB.right;
			if(compare != 0) return compare;
		});
	}

	displayContours(display=true){
		if(display){
			this._colorizeContours(this._contours);
		} else {
			if(this._contourOverlay) this._contourOverlay.visible = false;
		}
	}

	highlightContours(contourIDs,doHighlight=true){
		if(doHighlight)
			this._colorizeContours(contourIDs,'#FF00FFCC');
		else
			this._colorizeContours(contourIDs); // Call colorize with default color
	}
	
	contourHitTest(point){
		const len_contours = this._contours.length;
		if(len_contours > 0){
			let search_bottom = 0;
			let search_top = len_contours;
			while(search_top != search_bottom){
				const half = Math.round((search_top-search_bottom)/2);
				const contour_bound = this._contourBounds[half];
				const bound = contour_bound.bound;
				const id = contour_bound.id;
				if(point.y < bound.top) search_top = half;
				else if(bound.bottom < point.y) search_bottom = half;
				else if(bottom.left < point.x) search_top = half;
				else if(point.x < bottom.right) search_bottom = half;
				else return {type:'contour',item:{elementID:id,points:this._contours[id]}};
			}
		}
		const image_bounds = this.getBoundaries();
		if(image_bounds.left <= point.x && point.x <= image_bounds.right && image_bounds.top <= point.y && point.y <= image_bounds.bottom)
			return {type:'image'}

		return null; // No contour nor the background was hit
	}


	_colorizeContours(contourIDs,color='#00FF00CC'){
		let overlayHTML = document.getElementById(this._contourOverlayID);
		if(!overlayHTML){
			overlayHTML = document.createElement('canvas');
			document.body.appendChild(overlayHTML);
			overlayHTML.id = "overlay";
		}
		overlayHTML.width = this.getImageWidth();
		overlayHTML.height = this.getImageHeight();
		let ctx = overlayHTML.getContext("2d");

		contourIDs.forEach((id)=>{
			contour = this._contours[id];
			ctx.fillStyle = color;
			ctx.beginPath();
			if(contour.length > 0){
				ctx.moveTo(contour[0].x, contour[0].y);
				contour.forEach((p) => {
					ctx.lineTo(p.x,p.y);
				});
				ctx.closePath();
				ctx.fill();
			}
		});

		//Draw Overlay
		if(document.getElementById(this._contourOverlayID)){
			if(this._contourOverlay) this._contourOverlay.remove();

			this._contourOverlay = new paper.Raster(this._contourOverlayID);
			this._contourOverlay.visible = true;
			const imagePosition = this._imageCanvas.bounds;
			this._contourOverlay.position = new paper.Point(imagePosition.x,imagePosition.y);
			this._contourOverlay.position = this._contourOverlay.position.add([imagePosition.width * 0.5, imagePosition.height * 0.5]);
			this._contourOverlay.scale(this._currentZoom);

			this._imageCanvas.addChild(this._contourOverlay);
		}
	}

	_createEmptyOverlay(){
		// Rectangle dummy to force empty group size to image size
		const rect = new paper.Path.Rectangle(this._imageCanvas.bounds);

		// Create overlay canvas
		const overlay = new paper.Group();
		overlay.addChild(rect);
		return overlay;
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
