/* The Editor is an extension of the viewer that is used for every functionality that is about creating or editing
 * elements that are to be displayed in the viewer.
 * It handles requests for creating or editing elements, but is not supposed to start those actions by itself. */
class Editor extends Viewer {
	constructor(viewerInput, colors, controller) {
		super(viewerInput, colors);
		this.isEditing = false;
		this._controller = controller;

		this._tempPolygonType;
		this._tempPolygon;
		this._tempPoint;
		this._tempID;
		this._tempMouseregion;
		this._tempEndCircle;
		
		this._grid = { isActive: false };
		this._readingOrder;
		
		this._guiOverlay;
		
		this.mouseregions = { TOP: 0, BOTTOM: 1, LEFT: 2, RIGHT: 3, MIDDLE: 4, OUTSIDE: 5 };
		this.DoubleClickListener = new DoubleClickListener();
		
		this._pointSelector;
		this._pointSelectorListener;
		this._centers = {}
	}

	updateSegment(segment) {
		super.updateSegment(segment);
		this._centers[segment.id] = null;
		if(this._readingOrder && this._readingOrder.visible){

		}
	}

	removeSegment(id) {
		super.removeSegment(id);
		this._centers[id] = null;
	}

	/* Start a rectangle, that is updated on mouse movement. Functions/Status updates at start, end and while updating the rectangles can be supplied.
		Parameter:
			startFunction: 	Is called before the rectangle is created. e.g. function to change the mouse pointer
			endFunction: 	Is called when the rectangle is finished. e.g. callback function with the finished rectangle
			updateFunction: Is called every time the rectangle is updated by moving the mouse.
			startPoint:		Use as start of the rectangle if set, otherwise wait for mouse down.
	*/
	_startRectangle(startFunction = () => {}, endFunction = (rectangle) => {}, updateFunction = (rectangle) => {}, borderStyle = 'none', startPoint){
		if (this.isEditing === false) {

			startFunction();

			const listener = {};
			this.addListener(listener);

			const start_rectangle = (event) => {
				if (this.isEditing === true) { 
					const startPoint = event.point; 

					const imageCanvas = this.getImageCanvas();

					const canvasPoint = this.getPointInBounds(startPoint, this.getBoundaries());
					// Start polygon
					this._tempPoint = new paper.Path(canvasPoint);
					imageCanvas.addChild(this._tempPoint);
					this._tempPolygon = new paper.Path();
					this._tempPolygon.add(this._tempPoint); //Add Point for mouse movement
					this._tempPolygon.fillColor = 'gray';
					this._tempPolygon.strokeColor = 'black';
					this._tempPolygon.opacity = 0.3;
					this._tempPolygon.closed = true;
					switch(borderStyle){
						case 'selected':
							this._tempPolygon.selected = true;
							break;
						case 'dashed':
							this._tempPolygon.dashArray = [5, 3];
							break;
						default:
							break;
					}

					listener.onMouseDrag = (event) => {
						if (this.isEditing === true) {
							if (this._tempPolygon) {
								const point = this.getPointInBounds(event.point, this.getBoundaries());
								let rectangle = new paper.Path.Rectangle(this._tempPoint.firstSegment.point, point);

								this._tempPolygon.segments = rectangle.segments;
								
								updateFunction(rectangle);
							}
						} else {
							throw new Error("Edit Mode is left while still creating a Rectangle");
						}
					}
					imageCanvas.addChild(this._tempPolygon);

					listener.onMouseUp = (event) => {
						this._endRectangle(endFunction,this._tempPolygon);
						this.removeListener(listener);
					}
				} else {
					this.removeListener(listener);
				}
			}

			if(startPoint)
				start_rectangle({point:startPoint});
			else
				listener.onMouseDown = start_rectangle;
		}
	}

	_endRectangle(endFunction = (rectangle) => {}, rectangle) {
		if (this.isEditing) {
			this.isEditing = false;
			if (this._tempPolygon != null) {
				endFunction(rectangle);
				this._tempPolygon.remove();
				this._tempPolygon = null;
			}
			if (this._tempPoint != null) {
				this._tempPoint.clear();
				this._tempPoint = null;
			}
			document.body.style.cursor = "auto";
		}
	}

	/* Start creating a rectangle of a given type, that is updated on mouse movement.
		Parameter:
			type:			ElementType of the rectangle
			startPoint:		Use as start of the rectangle if set, otherwise wait for mouse down.
	*/
	createRectangle(type,startPoint) {
		if (this.isEditing === false) {
			this._startRectangle(
				()=>{
					this.isEditing = true;
					this._tempPolygonType = type;
					document.body.style.cursor = "copy";
				},
				(rectangle)=>{
					this._tempPolygon.closed = true;
					this._tempPolygon.selected = false;

					switch (this._tempPolygonType) {
						case 'segment':
							this._controller.callbackNewSegment(this._convertCanvasPolygonToGlobal(rectangle, false));
							break;
						case 'area':
							this._controller.callbackNewArea(this._convertCanvasPolygonToGlobal(rectangle, true));
							break;
						case 'textline':
							this._controller.callbackNewTextLine(this._convertCanvasPolygonToGlobal(rectangle, false));
							break;
						case 'ignore':
							this._controller.callbackNewArea(this._convertCanvasPolygonToGlobal(rectangle, true), 'ignore');
							break;
						case 'roi':
						default:
							this._controller.callbackNewRoI(this._convertCanvasPolygonToGlobal(rectangle, true));
							break;
					}
				},
				(rectangle) => {},
				type === ElementType.SEGMENT? 'selected' : 'default');
		}
	}

	/* Start creating a rectangle of a given type, that is updated on mouse movement.
		Parameter:
			type:			ElementType of the rectangle
			startPoint:		Use as start of the rectangle if set, otherwise wait for mouse down.
	*/
	boxSelect(callback = (tl,br) => {}, update = (tl,br) => {}, startPoint) {
		if (this.isEditing === false) {
			this._startRectangle(
				()=>{
					this.isEditing = true;
				},
				(rectangle)=>{
					const selectBounds = this._tempPolygon.bounds;
					callback(selectBounds.topLeft, selectBounds.bottomRight);
				},
				(rectangle) => {
					const selectBounds = this._tempPolygon.bounds;
					update(selectBounds.topLeft, selectBounds.bottomRight);
				},
				'dashed',
				startPoint
			);
		}
	}

	startCreatePolygon(type) {
		if (this.isEditing === false) {
			this.isEditing = true;
			this._tempPolygonType = type;
			document.body.style.cursor = "copy";

			const listener = {};
			this.addListener(listener);
			listener.onMouseMove = (event) => {
				if (this._tempPolygon) {
					this._tempPolygon.removeSegment(this._tempPolygon.segments.length - 1);
					this._tempPolygon.add(this.getPointInBounds(event.point, this.getBoundaries()));
				}
			}

			this.DoubleClickListener.setAction((pos)=> {
				this.endCreatePolygon();
				this.DoubleClickListener.setActive(false);
			});
			this.DoubleClickListener.setActive(true);

			listener.onMouseUp = (event) => {
				this.DoubleClickListener.update(event.point);
				if (this.isEditing === true) {
					const canvasPoint = this.getPointInBounds(event.point, this.getBoundaries());

					if (!this._tempPolygon) {
						// Start polygon
						this._tempPolygon = new paper.Path();
						this._tempPolygon.add(new paper.Point(canvasPoint)); //Add Point for mouse movement
						this._tempPolygon.fillColor = 'grey';
						this._tempPolygon.opacity = 0.3;
						this._tempPolygon.closed = false;
						this._tempPolygon.selected = true;

						// circle to end the polygon
						this._tempEndCircle = new paper.Path.Circle(canvasPoint, 5);
						this._tempEndCircle.strokeColor = 'black';
						this._tempEndCircle.fillColor = 'grey';
						this._tempEndCircle.opacity = 0.5;
						this._tempEndCircle.onMouseUp = (event) => this.endCreatePolygon();

						let imageCanvas = this.getImageCanvas();
						imageCanvas.addChild(this._tempPolygon);
						imageCanvas.addChild(this._tempEndCircle);
					}
					this._tempPolygon.add(new paper.Point(canvasPoint));
				} else {
					this.removeListener(listener);
				}
			}
		}
	}

	endCreatePolygon() {
		if (this.isEditing) {
			this.isEditing = false;
			if (this._tempPolygon != null) {
				this._tempPolygon.closed = true;
				this._tempPolygon.selected = false;
				if (this._tempPolygonType === ElementType.SEGMENT) {
					this._controller.callbackNewSegment(this._convertCanvasPolygonToGlobal(this._tempPolygon, false));
				} else if(this._tempPolygonType === ElementType.AREA) {
					this._controller.callbackNewArea(this._convertCanvasPolygonToGlobal(this._tempPolygon, true));
				} else if(this._tempPolygonType === ElementType.TEXTLINE) {
					this._controller.callbackNewTextLine(this._convertCanvasPolygonToGlobal(this._tempPolygon, false));
				}
				this._tempPolygon.remove();
				this._tempPolygon = null;
				this._tempEndCircle.remove();
			}
			document.body.style.cursor = "auto";
		}
	}

	startCreateLine() {
		if (this.isEditing === false) {
			this.isEditing = true;
			document.body.style.cursor = "copy";

			const listener = {};
			this.addListener(listener);
			listener.onMouseMove = (event) => {
				if (this._tempPolygon) {
					this._tempPolygon.removeSegment(this._tempPolygon.segments.length - 1);
					this._tempPolygon.add(this.getPointInBounds(event.point, this.getBoundaries()));
				}
			}

			this.DoubleClickListener.setAction((pos)=> {
				this.endCreateLine();
				this.DoubleClickListener.setActive(false);
			});
			this.DoubleClickListener.setActive(true);

			listener.onMouseUp = (event) => {
				this.DoubleClickListener.update(event.point);
				if (this.isEditing === true) {
					const canvasPoint = this.getPointInBounds(event.point, this.getBoundaries());

					if (!this._tempPolygon) {
						// Start polygon
						this._tempPolygon = new paper.Path();
						this._tempPolygon.add(new paper.Point(canvasPoint)); //Add Point for mouse movement
						this._tempPolygon.strokeColor = new paper.Color(0, 0, 0);
						this._tempPolygon.closed = false;
						this._tempPolygon.selected = true;

						this.getImageCanvas().addChild(this._tempPolygon);
					}
					this._tempPolygon.add(new paper.Point(canvasPoint));
				} else {
					this.removeListener(listener);
				}
			}
		}
	}

	endCreateLine() {
		if (this.isEditing) {
			this.isEditing = false;

			if (this._tempPolygon != null) {
				this._tempPolygon.closed = false;
				this._tempPolygon.selected = false;
				this._controller.callbackNewCut(this._convertCanvasPolygonToGlobal(this._tempPolygon, false));

				this._tempPolygon.remove();
				this._tempPolygon = null;
			}
			document.body.style.cursor = "auto";
		}
	}

	startCreateBorder(type) {
		if (this.isEditing === false) {
			this.isEditing = true;
			this._tempPolygonType = type;

		const listener = {};
		this.addListener(listener);

		if (!this._tempPolygon) {
			// Start polygon
			this._tempPolygon = new paper.Path();
			this._tempPolygon.fillColor = 'grey';
			this._tempPolygon.opacity = 0.5;
			this._tempPolygon.closed = true;
			//this._tempPolygon.selected = true;

			this.getImageCanvas().addChild(this._tempPolygon);
			listener.onMouseMove = (event) => {
				if (this.isEditing === true) {
					if (this._tempPolygon) {
						const boundaries = this.getBoundaries();
						const mouseregion = this.getMouseRegion(boundaries, event.point);
						this._tempMouseregion = mouseregion;

						let topleft, topright, rectangle, bottommouse, mouseright;
						switch (mouseregion) {
							case this.mouseregions.LEFT:
								document.body.style.cursor = "col-resize";

								topleft = new paper.Point(boundaries.left, boundaries.top);
								bottommouse = new paper.Point(event.point.x, boundaries.bottom);
								rectangle = new paper.Path.Rectangle(topleft, bottommouse);

								this._tempPolygon.segments = rectangle.segments;
								break;
							case this.mouseregions.RIGHT:
								document.body.style.cursor = "col-resize";

								topright = new paper.Point(boundaries.right, boundaries.top);
								bottommouse = new paper.Point(event.point.x, boundaries.bottom);
								rectangle = new paper.Path.Rectangle(topright, bottommouse);

								this._tempPolygon.segments = rectangle.segments;
								break;
							case this.mouseregions.TOP:
								document.body.style.cursor = "row-resize";

								topleft = new paper.Point(boundaries.left, boundaries.top);
								mouseright = new paper.Point(boundaries.right, event.point.y);
								rectangle = new paper.Path.Rectangle(topleft, mouseright);

								this._tempPolygon.segments = rectangle.segments;
								break;
							case this.mouseregions.BOTTOM:
								document.body.style.cursor = "row-resize";

								bottommouse = new paper.Point(boundaries.left, boundaries.bottom);
								mouseright = new paper.Point(boundaries.right, event.point.y);
								rectangle = new paper.Path.Rectangle(bottommouse, mouseright);

								this._tempPolygon.segments = rectangle.segments;
								break;
							case this.mouseregions.MIDDLE:
							default:
								this._tempPolygon.removeSegments();
								document.body.style.cursor = "copy";
								break;
						}
					}
				}
			}
			listener.onMouseUp = (event) => {
				if (this._tempPolygon) {
					this.endCreateBorder();
					this.removeListener(listener);
				}
			}
		}
	}
}

endCreateBorder() {
	if (this.isEditing) {
		this.isEditing = false;

		if (this._tempPolygon != null) {
			if (this._tempPolygonType === ElementType.AREA) {
				this._controller.callbackNewArea(this._convertCanvasPolygonToGlobal(this._tempPolygon, true));
			}

			this._tempPolygon.remove();
			this._tempPolygon = null;
		}
		document.body.style.cursor = "auto";
	}
}

startMovePolygonPoints(elementID, type, points) {
	if (this.isEditing === false) {
		this.isEditing = true;
		this._tempPolygonType = type;
		document.body.style.cursor = "copy";

		// Create Copy of movable
		this._tempPolygon = new paper.Path(this.getPolygon(elementID).segments);
		this._tempID = elementID;
		this._tempPolygon.fillColor = 'grey';
		this._tempPolygon.opacity = 0.3;
		this._tempPolygon.closed = true;
		this._tempPolygon.strokeColor = 'black';
		this._tempPolygon.dashArray = [5, 3];

		// Set Grid
		this.setGrid(this._tempPolygon.position);

		// Position letiables between old and new polygon position
		this._tempPoint = new paper.Point(0, 0);
		const oldPosition = new paper.Point(this._tempPolygon.position);
		let oldMouse = null;

		const listener = {};
		this.addListener(listener);
		listener.onMouseDrag = (event) => {
			if (this.isEditing === true) {
				if (oldMouse === null) {
					oldMouse = event.point;
				}
				this._tempPoint = oldPosition.add(event.point.subtract(oldMouse));
				if (this._grid.isActive) 
					this._tempPoint = this.getPointFixedToGrid(this._tempPoint);

					const delta = oldPosition.subtract(this._tempPoint);
					this._tempPolygon.removeSegments();
					const segments = this.getPolygon(this._tempID).segments.map(p => {
						let newPoint = p.point;
						const realPoint = this._convertCanvasToGlobal(newPoint.x, newPoint.y);
						points.forEach(pp => {
							// Contains can not be trusted (TODO: propably?)
							if (realPoint.x === pp.x && realPoint.y === pp.y) {
								newPoint = new paper.Point(p.point.x - delta.x, p.point.y - delta.y);
							}
						});
						return new paper.Segment(newPoint);
					});
					this._tempPolygon.addSegments(segments);
			} else {
				this.removeListener(listener);
			}
		}
		listener.onMouseUp = (event) => {
			if (this.isEditing === true) {
				this.endMovePolygonPoints();
			}
			this.removeListener(listener);
		}
	}
}

endMovePolygonPoints() {
	if (this.isEditing) {
		this.isEditing = false;

		if (this._tempPolygon !== null) {
			if (this._tempPolygonType === ElementType.SEGMENT || this._tempPolygonType === ElementType.TEXTLINE) {
				this._controller.movePolygonPoints(this._tempID, this._convertCanvasPolygonToGlobal(this._tempPolygon, false), this._tempPolygonType);
			}

			if(this._tempPolygon) this._tempPolygon.remove();
			this._tempPolygon = null;
		}

		document.body.style.cursor = "auto";
	}
}

startScalePolygon(elementID, type) {
	if (this.isEditing === false) {
		this.isEditing = true;
		this._tempPolygonType = type;

		// Create Copy of movable
		const boundaries = this.getPolygon(elementID).bounds;
		this._tempPolygon = new paper.Path.Rectangle(boundaries);
		this.getImageCanvas().addChild(this._tempPolygon);
		this._tempID = elementID;
		this._tempPolygon.fillColor = 'grey';
		this._tempPolygon.opacity = 0.3;
		this._tempPolygon.closed = true;
		this._tempPolygon.strokeColor = 'black';
		this._tempPolygon.dashArray = [5, 3];

		const listener = {};
		this.addListener(listener);
		listener.onMouseMove = (event) => {
			if (this.isEditing === true) {
				if (this._tempPolygon) {
					const mouseregion = this.getMouseRegion(this._tempPolygon.bounds, event.point, 0.1, 10);
					this._tempMouseregion = mouseregion;

					switch (mouseregion) {
						case this.mouseregions.LEFT:
						case this.mouseregions.RIGHT:
							document.body.style.cursor = "col-resize";
							break;
						case this.mouseregions.TOP:
						case this.mouseregions.BOTTOM:
							document.body.style.cursor = "row-resize";
							break;
						case this.mouseregions.MIDDLE:
						default:
							document.body.style.cursor = "auto";
							break;
					}
				}
			} else {
				this.removeListener(listener);
			}
		}
		listener.onMouseDown = (event) => {
			if (this.isEditing === true) {
				this.scalePolygon(this._tempPolygon, this._tempMouseregion);
			}
			this.removeListener(listener);
		}
	}
}

scalePolygon(polygon, mouseregion) {
	const listener = {};
	this.addListener(listener);
	listener.onMouseDrag = (event) => {
		if (this.isEditing === true) {
			if (this._tempPolygon) {
				const mouseinbound = this.getPointInBounds(event.point, this.getBoundaries());

				switch (mouseregion) {
					case this.mouseregions.LEFT:
						if (mouseinbound.x < polygon.bounds.right) {
							polygon.bounds.left = mouseinbound.x;
							document.body.style.cursor = "col-resize";
						}
						break;
					case this.mouseregions.RIGHT:
						if (mouseinbound.x > polygon.bounds.left) {
							polygon.bounds.right = mouseinbound.x;
							document.body.style.cursor = "col-resize";
						}
						break;
					case this.mouseregions.TOP:
						if (mouseinbound.y < polygon.bounds.bottom) {
							polygon.bounds.top = mouseinbound.y;
							document.body.style.cursor = "row-resize";
						}
						break;
					case this.mouseregions.BOTTOM:
						if (mouseinbound.y > polygon.bounds.top) {
							polygon.bounds.bottom = mouseinbound.y;
							document.body.style.cursor = "row-resize";
						}
						break;
					case this.mouseregions.MIDDLE:
					default:
						document.body.style.cursor = "auto";
						this.removeListener(listener);
						break;
				}
			}
		} else {
			this.removeListener(listener);
		}
	}
	listener.onMouseUp = (event) => {
		if (this.isEditing === true) {
			this.endScalePolygon();
		}
		this.removeListener(listener);
	}
}

endScalePolygon() {
	if (this.isEditing) {
		this.isEditing = false;

		if (this._tempPolygon != null) {
			const polygon = new paper.Path(this.getPolygon(this._tempID).segments);
			polygon.bounds = this._tempPolygon.bounds;

			this._tempPolygon.remove();

			if (this._tempPolygonType !== ElementType.SEGMENT && this._tempPolygonType != ElementType.TEXTLINE) 
				this._controller.transformRegion(this._tempID, this._convertCanvasPolygonToGlobal(polygon, true));

			this._tempPolygon = null;
		}

		document.body.style.cursor = "auto";
	}
}

getMouseRegion(bounds, mousepos, percentarea, minarea) {
	minarea = minarea ? minarea : 0;

	const width = bounds.width;
	const height = bounds.height;
	if (percentarea == null) {
		percentarea = 0.4;
	}
	//Calculate the height and width delta from the borders inwards to the center with minarea and percentarea 
	let widthDelta = width * percentarea;
	if (widthDelta < minarea) {
		if (minarea < width * 0.5) {
			widthDelta = minarea;
		} else {
			widthDelta = width * 0.5;
		}
	}
	let heightDelta = height * percentarea;
	if (heightDelta < minarea) {
		if (minarea < height * 0.5) {
			heightDelta = minarea;
		} else {
			heightDelta = height * 0.5;
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
	if (mousepos.x < leftmin || mousepos.x > rightmax || mousepos.y < topmin || mousepos.y > bottommax) {
		return this.mouseregions.OUTSIDE;
	} else {
		//Get Mouse position/region
		if (mousepos.x > leftmin && mousepos.x < leftmax) {
			return this.mouseregions.LEFT;
		} else if (mousepos.x > rightmin && mousepos.x < rightmax) {
			return this.mouseregions.RIGHT;
		} else if (mousepos.y > topmin && mousepos.y < topmax) {
			return this.mouseregions.TOP;
		} else if (mousepos.y > bottommin && mousepos.y < bottommax) {
			return this.mouseregions.BOTTOM;
		} else {
			return this.mouseregions.MIDDLE;
		}
	}
}

startEditing() {
	this.isEditing = true;
}

endEditing() {
	this.clearListener();
	this.isEditing = false;

	this._tempID = null;
	if (this._tempPolygon != null) {
		this._tempPolygon.remove();
		this._tempPolygon = null;
	}
	this._tempPoint = null;

	if (this._tempEndCircle) {
		this._tempEndCircle.remove();
		this._tempEndCircle = null;
	}

	document.body.style.cursor = "auto";
}

getPointInBounds(point, bounds) {
	if (!bounds.contains(point)) {
		let boundPoint = point;
		if (point.x < bounds.left) {
			boundPoint.x = bounds.left;
		} else if (point.x > bounds.right) {
			boundPoint.x = bounds.right;
		}
		if (point.y < bounds.top) {
			boundPoint.y = bounds.top;
		} else if (point.y > bounds.bottom) {
			boundPoint.y = bounds.bottom;
		}

		return boundPoint;
	} else {
		return point;
	}
}

setGrid(point) {
	if (this._grid.vertical == null || this._grid.horizontal == null) {
		this._grid.vertical = new paper.Path.Line();
		this._grid.horizontal = new paper.Path.Line();
		this._grid.vertical.visible = false;
		this._grid.horizontal.visible = false;
	}
	const bounds = paper.view.bounds;
	this._grid.vertical.removeSegments();
	this._grid.vertical.add(new paper.Point(point.x, bounds.top));
	this._grid.vertical.add(new paper.Point(point.x, bounds.bottom));

	this._grid.horizontal.removeSegments();
	this._grid.horizontal.add(new paper.Point(bounds.left, point.y));
	this._grid.horizontal.add(new paper.Point(bounds.right, point.y));
}

addGrid() {
	this._grid.isActive = true;
}

removeGrid() {
	this._grid.isActive = false;
}

getPointFixedToGrid(point) {
	if (this._grid.isActive && this._grid.vertical != null && this._grid.horizontal != null) {
		const verticalFixedPoint = new paper.Point(this._grid.vertical.getPointAt(0).x, point.y);
		const horizontalFixedPoint = new paper.Point(point.x, this._grid.horizontal.getPointAt(0).y);
		if (verticalFixedPoint.getDistance(point) < horizontalFixedPoint.getDistance(point)) {
			return verticalFixedPoint;
		} else {
			return horizontalFixedPoint;
		}
	} else {
		return point;
	}
}

displayReadingOrder(readingOrder) {
	if(this._guiOverlay){
		if (!this._readingOrder) {
			this._readingOrder = new paper.Path();
			this._readingOrder.strokeColor = 'indigo';
			this._readingOrder.strokeWidth = 2;
		}
		this.getImageCanvas().addChild(this._readingOrder);
		this._readingOrder.visible = true;
		this._guiOverlay.visible = true;
		this._guiOverlay.bringToFront();
		this._readingOrder.removeSegments();
		this._guiOverlay.removeChildren();

		for (let index = 0; index < readingOrder.length; index++) {
			const id = readingOrder[index];
			const segment = this.getPolygon(id);
			if (segment) {
				const center = this.calculateVisualPolygonCenter(id,segment);
				this._readingOrder.add(new paper.Segment(center));
				const label = new paper.Group();
				const text = new paper.PointText({
					content: index,
					fillColor: 'indigo',
					fontFamily: 'Courier New',
					fontWeight: 'bold',
					fontSize: '18pt',
				});
				text.bounds.center = center;

				const background = new paper.Path.Circle(text.bounds.center,text.bounds.height/2);
				background.fillColor = new paper.Color(1, 1, 1, 0.8);
				background.strokeColor = 'indigo';
				background.strokeWidth = 3;

				label.addChild(background)
				label.addChild(text)
				label.lastZoom = this._currentZoom;

				this._guiOverlay.addChild(label);
			}
		}
	}
}

/**
 * Find a visual center of a polygon, inside the polygon
 * 
 * @param {*} polygon 
 */
calculateVisualPolygonCenter(id, polygon, maxIterations=10, simplify=false){
	if(this._centers[id]) {
		const c = this._centers[id];
		return this._convertGlobalToCanvas(c.x,c.y);
	} else {
		let workPolygon = new paper.Path(polygon.segments);
		workPolygon.closed = true;
		// Simplify polygon to reduce performance hit.
		if(simplify){
			workPolygon.simplify();
		}
		let workRect = workPolygon.bounds;

		while(maxIterations--){
			if(workPolygon.contains(workRect.center)){
				this._centers[id] = this._convertCanvasToGlobal(workRect.center.x, workRect.center.y, false);
				return workRect.center;
			} else {
				const workingGroup = [
					new paper.Rectangle(new paper.Point(workRect.left, workRect.top), workRect.center),
					new paper.Rectangle(new paper.Point(workRect.right, workRect.top), workRect.center),
					new paper.Rectangle(new paper.Point(workRect.left, workRect.bottom), workRect.center),
					new paper.Rectangle(new paper.Point(workRect.right, workRect.bottom), workRect.center)
				];

				let maxArea = 0;
				for(const rect of workingGroup){
					const overlapArea = new paper.Path.Rectangle(rect).intersect(workPolygon).area;
					if(overlapArea > maxArea){
						maxArea = overlapArea;
						workRect = rect;
					}
				}
			}
		}
		this._centers[id] = this._convertCanvasToGlobal(workRect.center.x,workRect.center.y);
		return workRect.center; 
	}
}

startPointSelect(targetID, callback = (targetID,point) => {}, init = () => {}, cleanup = () => {}, update = (targetID,point) => {}){
	if(!this._pointSelector){
		// Terminate potential running point select
		this.endPointSelect();

		// Run 
		init();

		const polygon = this._polygons[targetID];

		// Init selector rectangle
		this._pointSelector = new paper.Path.Rectangle(new paper.Rectangle(0,0,6,6));
		this._pointSelector.strokeColor = '#0699ea';
		this._pointSelector.fillColor = '#0699ea';

		const hitOptions = { segments: true, stroke: true, tolerance: 10 };

		if(this._pointSelectorListener)
			this.removeListener(this._pointSelectorListener);
		this._pointSelectorListener = {};
		this._pointSelectorListener.onMouseMove = (event) => {
			if(!this.isEditing && this._pointSelector){
				this._pointSelector.visible = true;
				const hitResult = polygon.hitTest(event.point, hitOptions);
				if (hitResult) {
					if (hitResult.type == 'segment') 
						this._pointSelector.position = new paper.Point(hitResult.segment.point);
					else if (hitResult.type == 'stroke') 
						this._pointSelector.position = new paper.Point(hitResult.location.point);

					update(targetID, this._convertCanvasToGlobal(this._pointSelector.position.x,this._pointSelector.position.y));
				} else { 
					// Mouse is not near the polygon
					this._pointSelector.visible = false;
				}
			} else {
				this.endPointSelect();
				cleanup();
				this.removeListener(this._pointSelectorListener);
			}
		} 
		this._pointSelectorListener.onMouseDown = (event) => {
			if(!this.isEditing && this._pointSelector){
				const hitResult = polygon.hitTest(event.point, hitOptions);
				if (hitResult) {
					// Update last time if hit is not on point selector
					if (hitResult.type == 'segment') 
						this._pointSelector.position = new paper.Point(hitResult.segment.point);
					else if (hitResult.type == 'stroke') 
						this._pointSelector.position = new paper.Point(hitResult.location.point);
					this.endPointSelect(callback, targetID, this._pointSelector.position);
					return false; // do not propagate
				} else {
					this.endPointSelect()
					return true; // do propagate
				}
			}

			cleanup();
			this.removeListener(this._pointSelectorListener);
		}
		this.addListener(this._pointSelectorListener);
	}
}

endPointSelect(callback = (targetID, point) => {}, targetID, point){
	if(this._pointSelectorListener)
		this.removeListener(this._pointSelectorListener);
	if(this._pointSelector)
		this._pointSelector.remove();
	this._pointSelector = null;

	if(point && point.x && point.y){
		callback(targetID, this._convertCanvasToGlobal(point.x,point.y));
	}else{
		callback(targetID)
	}
}

addPointsOnLine(elementID,points){
	const polygon = this._polygons[elementID];
	if(polygon){
		points.forEach(point => {
			const canvasPoint = this._convertGlobalToCanvas(point.x,point.y);
			const hitResult = polygon.hitTest(canvasPoint, { stroke: true, tolerance: 1 });

			if (hitResult && hitResult.type == 'stroke') {
				polygon.insert(hitResult.location.index+1,canvasPoint);
			}
		});
	}
	return this._convertCanvasPolygonToGlobal(polygon);
}

_resetPointSelector(){
	if(this._pointSelector)
		this._pointSelector.position = new paper.Point(-10,-10);
}

hideReadingOrder() {
	if (this._readingOrder) {
		this._readingOrder.visible = false;
		this._guiOverlay.visible = false;
	}
}

getSortedReadingOrder(readingOrder) {
	const centers = {};
	for (let index = 0; index < readingOrder.length; index++) {
		const id = readingOrder[index];
		centers[id] = this.getPolygon(id).bounds.center;
	}

	readingOrder.sort(function (a, b) {
		const centerA = centers[a];
		const centerB = centers[b];
		const delta = centerA.y - centerB.y;
		if (delta != 0) {
			return delta;
		} else {
			return centerA.x - centerB.x;
		}
	});

	return readingOrder;
}

getPointInBounds(point, bounds) {
	if (!bounds.contains(point)) {
		const boundPoint = point;
		if (point.x < bounds.left) {
			boundPoint.x = bounds.left;
		} else if (point.x > bounds.right) {
			boundPoint.x = bounds.right;
		}
		if (point.y < bounds.top) {
			boundPoint.y = bounds.top;
		} else if (point.y > bounds.bottom) {
			boundPoint.y = bounds.bottom;
		}

		return boundPoint;
	} else {
		return point;
	}
}

_resetOverlay() {
	this._guiOverlay.children.forEach(function (element) {
		if(element.hasOwnProperty("lastZoom")){
			// Reset current zoom
			element.scale(element.lastZoom/this._currentZoom);
			element.lastZoom = this._currentZoom;
		}
	}, this);
	this._resetPointSelector();
}

movePoint(point){
	super.movePoint(point);
	this._resetOverlay();
}
setImage(id) {
	super.setImage(id);

	// Create gui overlay
	this._guiOverlay = this._createEmptyOverlay();
	this._imageCanvas.addChild(this._guiOverlay);
		this._resetOverlay();
	}
	setZoom(zoomfactor, point) {
		super.setZoom(zoomfactor, point);
		this._resetOverlay();
	}
	zoomIn(zoomfactor, point) {
		super.zoomIn(zoomfactor, point);
		this._resetOverlay();
	}
	zoomOut(zoomfactor, point) {
		super.zoomOut(zoomfactor, point);
		this._resetOverlay();
	}
	zoomFit() {
		super.zoomFit();
		this._resetOverlay();
	}
}

/* Adds double click functionality for paperjs canvas */
class DoubleClickListener{
	constructor(action = (pos) => {}, maxTime = 500, maxDistance = 20) {
		this._lastClickedTime = undefined;
		this._lastClickedPosition = undefined;
		this._maxTime = maxTime;
		this._maxDistance = maxDistance;
		this._action = action;
		this._isActive = false;
		this._date = new Date();
	}

	update(curMousePos,curTime = this._date.getTime()){
		if(this._isActive && this._lastClickedTime && this._lastClickedPosition &&
			this._lastClickedPosition.getDistance(curMousePos) <= this._maxDistance &&
			curTime - this._lastClickedTime <= this._maxTime)
		{
			this._action(curMousePos);
		}

		this._lastClickedPosition = curMousePos;
		this._lastClickedTime = curTime;
		
	}

	setActive(isActive = true){
		this._isActive = isActive;
	}

	setAction(action = (pos) => {}){
		this._action = action;
	}
}