/* The viewer is a display for result segments, region segments and contours of any kind. 
 * It can handle inputs by forwarding it to a input manager (ViewerInput) 
 * All functionality about viewing elements in the viewer is handled here. 
 * It does not handle editing these elements. */
var ViewerMode = {POLYGON:'polygon',CONTOUR:'contour',TEXTLINE:'textline'}
class Viewer {
	constructor(viewerInput, colors) {
		this.thisInput = viewerInput;
		this._imageID;
		this._imageWidth;
		this._imageHeight;
		this._polygons = {};
		this._image;
		this._imageCanvas = new paper.Group();
		this._overlays = {};
		this._contourOverlayID = "overlay";
		this._contourOverlay;
		this._contour_context;
		this._background;
		this._currentZoom = 1;
		this._colors = colors;
		this._hitOptions = { segments: true, stroke: true, fill: true, tolerance: 10 };
		this._hitOptionsTextline = { segments: true, stroke: true, fill: true, tolerance: 5 };
		this._highlighted = null;
		this._listener = [];
		this._contours = [];
		this._contourBounds = []; // Sorted list (top->bottom->left->right) of object of contour + contour bound
		this._focus = null;
		this._focused = null;
		this._isDragging = false;
		this.mode = ViewerMode.POLYGON;

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
				// Click on X only if not previously dragged
				if(!this._isDragging){
					let hitResults = false;
					if(this.mode == ViewerMode.POLYGON){
						// Check regions first
						hitResults = this._overlays["regions"] && this._overlays["regions"].visible ?
										this._overlays["regions"].hitTestAll(event.point, this._hitOptions) : null;

						// Check textlines second
						if(!hitResults || hitResults.length == 0)
							hitResults = (this._overlays["lines"] && this._overlays["lines"].visible) ? this._overlays["lines"].hitTestAll(event.point,this._hitOptionsTextline) : null;

						// Check segments last
						if(!hitResults || hitResults.length == 0)
							hitResults = this._overlays["segments"] ? this._overlays["segments"].hitTestAll(event.point, this._hitOptions) : null;
					} else if(this.mode == ViewerMode.CONTOUR){
						hitResults = this._contourOverlay ? this.contourHitTest(event.point) : null;
					} else if(this.mode == ViewerMode.TEXTLINE){
						hitResults = (this._overlays["lines"] && this._overlays["lines"].visible) ? this._overlays["lines"].hitTestAll(event.point,this._hitOptionsTextline) : null;
					} else {
						throw new ValueError('Unkown selection mode: '+this.mode)
					}
					hitResults = hitResults.sort((a,b) => Math.abs(a.item.area) - Math.abs(b.item.area));
					if(hitResults && hitResults.length > 0){
						hitResults = hitResults.filter(hr => hr.item && hr.item.elementID);
						const hitResult = hitResults[0];
						if (hitResult) 
							this.thisInput.clickElement(hitResult.item.elementID, event, hitResult, this.mode);
						else
							this.thisInput.clickImage(event);
					} else {
						this.thisInput.clickBackground(event);
					}
				}
			}
			this._isDragging = false;
		};
		tool.onMouseDrag = (event) => {
			this._isDragging = true;
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
				const hitResult = this._imageCanvas ? this._imageCanvas.hitTest(event.point, this._hitOptions) : null;
				if(hitResult)
					this.thisInput.dragImage(event);
				else 
					this.thisInput.dragBackground(event);
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
				if(this.mode == ViewerMode.POLYGON){
					// Check regions first
					let hitResults = this._overlays["regions"] && this._overlays["regions"].visible ?
									this._overlays["regions"].hitTestAll(event.point, this._hitOptions) : null;

					// Check textlines second
					if(!hitResults || hitResults.length == 0)
						hitResults = (this._overlays["lines"] && this._overlays["lines"].visible) ? this._overlays["lines"].hitTestAll(event.point,this._hitOptionsTextline) : null;

					// Check segments last
					if(!hitResults || hitResults.length == 0)
						hitResults = this._overlays["segments"] ? this._overlays["segments"].hitTestAll(event.point, this._hitOptions) : null;

					hitResults = hitResults ?
									hitResults.filter(hr => hr.item && hr.item.elementID)
										.sort((a,b) => Math.abs(a.item.area) - Math.abs(b.item.area))
									: hitResults;
					if(hitResults && hitResults.length > 0){
						const hitResult = hitResults[0];
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
				} else if(this.mode == ViewerMode.CONTOUR){


				} else {
					throw new ValueError('Unkown selection mode: '+this.mode)
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

		// Focus Overlay
		this._createEmptyOverlay("focus");
		this._imageCanvas.addChild(this._overlays["focus"]);
		this._focus = new paper.CompoundPath({
			children:[new paper.Path.Rectangle(this._overlays["focus"].bounds)],
			fillColor: 'gray',
			fillRule: 'evenodd',
			opacity: 0.3,
			visible: false
		});
		this._overlays["focus"].addChild(this._focus);

		// Create region canvas
		this._createEmptyOverlay("segments");
		this._imageCanvas.addChild(this._overlays["segments"]);

		// Create region canvas
		this._createEmptyOverlay("regions");
		this._imageCanvas.addChild(this._overlays["regions"]);

		// Create line canvas
		this._createEmptyOverlay("lines");
		this._imageCanvas.addChild(this._overlays["lines"]);

		let overlayHTML = document.getElementById(this._contourOverlayID);
		if(!overlayHTML){
			overlayHTML = document.createElement('canvas');
			document.body.appendChild(overlayHTML);
			overlayHTML.id = "overlay";

		}
		overlayHTML.width = this.getImageWidth();
		overlayHTML.height = this.getImageHeight();
		this._contour_context = overlayHTML.getContext("2d");


		//Draw Overlay
		if(document.getElementById(this._contourOverlayID)){
			if(this._contourOverlay) this._contourOverlay.remove();

			this._contourOverlay = new paper.Raster(this._contourOverlayID);
			this._contourOverlay.visible = false;
			const imagePosition = this._imageCanvas.bounds;
			this._contourOverlay.position = new paper.Point(imagePosition.x,imagePosition.y);
			this._contourOverlay.position = this._contourOverlay.position.add([imagePosition.width * 0.5, imagePosition.height * 0.5]);
			this._contourOverlay.scale(this._currentZoom);

			this._imageCanvas.addChild(this._contourOverlay);
		}
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

	/**
	 * Focus a segment by graying out everything surounding it
	 * 
	 * @param {*} id 
	 * @param {*} doFocus 
	 */
	focusSegment(id, doFocus = true) {
		if(this._focused){
			this.resetFocus();
		}

		this.displayOverlay("focus",doFocus);
		const polygon = this._polygons[id];
		if(polygon){
			if (doFocus) {
				this._focus.removeChildren(1);
				this._focus.addChild(new paper.Path(polygon.segments));
				polygon.strokeWidth = 0;
			} else {
				polygon.strokeWidth = 1;
			}
			this._focused = id;
		}
		this._focus.visible = doFocus;
	}

	/**
	 * Reset the focus on any segment that might be focused
	 */
	resetFocus(){
		if(this._focused){
			const focused = this._focused;
			// Set to null before calling focusSegment, to not risk a infinite recursive loop
			this._focused = null; 
			this.focusSegment(focused,false);
		}
	}

	addRegion(region) {
		this.drawPolygon(region, true, false, this._overlays["regions"]);
	}

	addTextLine(textline){
		this.drawPolygon(textline, true, false, this._overlays["lines"]);
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
		return this._image.bounds;
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
		const imageSize = this.getBoundaries().size;

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

	//Protected Functions (are public but should bee seen as protected)
	drawPolygon(segment, doFill, isFixed, canvas = this._overlays["segments"]) {
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
		this._overlays["segments"].addChild(polygon);
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
		this._image = image;
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
		for(let id in contours){
			const contour = contours[id];
			let left = Number.POSITIVE_INFINITY;
			let right = Number.NEGATIVE_INFINITY;
			let top = Number.POSITIVE_INFINITY;
			let bottom = Number.NEGATIVE_INFINITY;
			contour.forEach(point => {
				if(point.x < left) left = point.x;
				if(right < point.x) right = point.x;
				if(point.y < top) top = point.y;
				if(bottom < point.y) bottom = point.y;
			});
			const width = right - left;
			const height = bottom - top;
			const area = width*height;
			this._contourBounds.push({id:id,bounds:{left:left,right:right,top:top,bottom:bottom,area:area}});
		}
		// Sort by top -> bottom -> left -> right
		this._contourBounds.sort((a,b) => {
			const boundA = a.bounds;
			const boundB = b.bounds;	
			let compare = boundA.top - boundB.top;
			if(compare != 0) return compare;
			compare = boundA.bottom - boundB.bottom;
			if(compare != 0) return compare;
			compare = boundA.left - boundB.left;
			if(compare != 0) return compare;
			compare = boundA.right - boundB.right;
			if(compare != 0) return compare;
		});
		this.displayContours(display);
	}

	displayContours(display=true){
		if(display){
			this._contourOverlay.visible = true;
			this._colorizeContours(this._contours);
		} else {
			if(this._contourOverlay) this._contourOverlay.visible = false;
		}
	}

	highlightContours(contourIDs,doHighlight=true){
		const contours = [];
		contourIDs.forEach(id => contours.push(this._contours[id]));
		if(doHighlight)
			this._colorizeContours(contours,'#FF00FF');
		else
			this._colorizeContours(contours); // Call colorize with default color
	}
	
	contourHitTest(point){
		//TODO replace with more performant
		const global_point = this._convertCanvasToGlobal(point);
		const hit_contours = [];
		this._contourBounds.forEach(contour => {
			if(contour.bounds.left <= global_point.x && global_point.x <= contour.bounds.right &&
				contour.bounds.top <= global_point.y && global_point.y <= contour.bounds.bottom)
				hit_contours.push(contour);
		});
		if(hit_contours.length > 0){
			hit_contours.sort((a,b) => {return a.bounds.area-b.bounds.area});
			const id = hit_contours[0].id;	
			return {type:'contour',item:{elementID:id,points:this._contours[id]}};
		} else{
			const image_bounds = this.getBoundaries();
			if(image_bounds.left <= point.x && point.x <= image_bounds.right && image_bounds.top <= point.y && point.y <= image_bounds.bottom)
				return {type:'image'}

			return null; // No contour nor the background was hit
		}
	}

	selectContoursInbetween(point_topleft,point_bottomright){
		const topleft = this._convertCanvasToGlobal(point_topleft);
		const bottomright = this._convertCanvasToGlobal(point_bottomright);
		//TODO replace with more performant
		const included_contours = [];
		this._contourBounds.forEach(contour => {
			if(topleft.x <= contour.bounds.left && contour.bounds.right <= bottomright.x &&
				topleft.y <= contour.bounds.top && contour.bounds.bottom <= bottomright.y)
				included_contours.push(contour.id);
		});
		return included_contours;
	}

	displayOverlay(name,doDisplay=true){
		if(this._overlays[name])
			this._overlays[name].visible = doDisplay;
	}

	_colorizeContours(contours,color='#0000FF'){
		let ctx = this._contour_context;

		contours.forEach((contour)=>{
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
		this.forceUpdate();
	}

	_createEmptyOverlay(name){
		// Rectangle dummy to force empty group size to image size
		const rect = new paper.Path.Rectangle(this.getBoundaries());

		// Create overlay canvas
		const overlay = new paper.Group();
		overlay.addChild(rect);
		this._overlays[name] = overlay;
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
		const imagePosition = this.getBoundaries();
		const canvasX = x * this._currentZoom + imagePosition.x;
		const canvasY = y * this._currentZoom + imagePosition.y;

		return { "x": canvasX, "y": canvasY };
	}

	_convertPercentToCanvas(x, y) {
		const imagePosition = this.getBoundaries();
		const canvasX = (x * imagePosition.width) + imagePosition.x;
		const canvasY = (y * imagePosition.height) + imagePosition.y;

		return { "x": canvasX, "y": canvasY };
	}
}
