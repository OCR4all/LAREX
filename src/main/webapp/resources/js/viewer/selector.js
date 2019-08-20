class Selector {
	constructor(editor, textviewer, controller) {
		this._controller = controller;
		this._editor = editor;
		this._textviewer = textviewer;
		this.selectMultiple = false;
		this.isSelecting = false;
		this._selectedElements = [];
		this._selectedPoints = [];
		this._selectedContours = [];
		this.selectedType;
	}

	/**
	 * Select an object via their identifier and points inside the object.
	 * Guess element type via id or if provided take the provided type.
	 * 
	 * @param {string} id 
	 * @param {[[number,number]]} points 
	 * @param {string} elementType 
	 */
	select(id, points, elementType=this._controller.getIDType(id)) {
		const mode = this._controller.getMode();

		if(elementType == ElementType.CONTOUR){			
			if(!this.selectMultiple || this.selectedType != elementType)
				this.unSelect()
			if (!this.isSegmentSelected(id)){ 
				this._selectedElements.push(id);
				this._editor.highlightContours([id], true);
			} else {
				const selectIndex = this._selectedElements.indexOf(id);
				this._selectedElements.splice(selectIndex, 1);
				this._editor.highlightContours([id], false);
			}
			this.selectedType = elementType;
		} else {
			const selectIndex = this._selectedElements.indexOf(id);
			const isSelected = selectIndex >= 0;

			// Select logic
			if(points && isSelected){
				//// Select point if possible
				if(elementType != ElementType.SEGMENT && mode !== Mode.EDIT && elementType != ElementType.TEXTLINE)
					throw Error("Tried to select points of a polygon that is not a segment and not a textline. ["+elementType+"]");

				// Unselect previous
				if((mode !== Mode.SEGMENT && mode !== Mode.EDIT && mode !== Mode.LINES) || this._selectedElements.length != 1
					|| this._selectedElements[0] != id || !this.selectMultiple)
					this.unSelect()
				
				if(mode === Mode.SEGMENT || mode === Mode.EDIT){
					this._selectPolygon(id, true, true);
					this._selectAndAddPoints(id, points);
				}else if (mode === Mode.LINES){
					this._selectPolygon(id, true, true);
					this._selectAndAddPoints(id, points);
				}else if (mode === Mode.TEXT && elementType === ElementType.TEXTLINE){
					this._selectPolygon(id);
					this._controller.editLine(id);
				}
			} else {
				//// Select polygon
				// Unselect others if this is not of type segment or if not select multiple
				const currentParent = this._selectedElements.length > 0 ? this._controller.textlineRegister[this._selectedElements[0]] : undefined;
				const selectParent =  this._selectedElements.length > 0 ? this._controller.textlineRegister[id] : undefined;
				if( !this._textviewer.isOpen() && 
					(!this.selectMultiple || !(
						((mode === Mode.SEGMENT || mode === Mode.EDIT) && elementType === ElementType.SEGMENT) || 
						(mode === Mode.LINES && elementType === ElementType.TEXTLINE && currentParent === selectParent)))
						){
					this.unSelect();
				}

				if (mode === Mode.TEXT && elementType === ElementType.TEXTLINE){
					if(this._textviewer.isOpen()){
						this._textviewer.setFocus(id);
						if(!isSelected){
							this.unSelect();
							this._selectedElements = [id];
						}
					} else {
						this._selectPolygon(id);
						this._controller.editLine(id);
					}
				}else{
					this._selectPolygon(id, !isSelected);
				}
			}

			this.selectedType = elementType;

			this._postSelection();
		}
	}

	/**
	 * Select the next element in the selection order
	 * 
	 * @param {*} reverse Invert the selection order and thereby select the previous in the selection oder
	 */
	selectNext(reverse=false){
		const mode = this._controller.getMode();
		if(mode === Mode.SEGMENT || mode === Mode.EDIT){
			// Get complete select order
			let order = this.getSelectOrder(ElementType.SEGMENT,reverse=reverse);
			if(order.length > 0){
				if(this._selectedElements.length > 0){
					// Retrieve the position of the last element in the order that is currently selected
					const last = reverse ? this._selectedElements.map(s => order.indexOf(s)).sort()[0]:
											this._selectedElements.map(s => order.indexOf(s)).sort().reverse()[0];
					if(last > -1){
						// Reorder
						order = order.slice(last+1).concat(order.slice(0,last+1));
					}
				}
				/* Select the first element after the selected elements
					* or loop to the first element in the reading order */
				this.select(order[0]);
			}
		} else if (mode === Mode.LINES){
			if(this.selectedType == ElementType.SEGMENT){
				const hasTextLines = Object.entries(this._controller.textlineRegister).map(([_,p]) => p);
				let order = this.getSelectOrder(ElementType.SEGMENT,reverse=reverse).filter(s => hasTextLines.includes(s));

				if(order.length > 0){
					if(this._selectedElements.length > 0){
						const last = reverse ? this._selectedElements.map(s => order.indexOf(s)).sort()[0]:
											  this._selectedElements.map(s => order.indexOf(s)).sort().reverse()[0];
						if(last > -1){
							// Reorder
							order = order.slice(last+1).concat(order.slice(0,last+1));
						}
					}
					const segment = order[0];
					// Select first textline
					if(segment.readingOrder && segment.readingOrder.length > 0){
						this.select(segment.readingOrder[0]);
					} else {
						let subOrder = this.getSelectOrder(ElementType.TEXTLINE,reverse=reverse,segment);
						if(subOrder.length > 0){
							this.select(subOrder[0]);
						}
					}
				}
			} else {
				let order = this.getSelectOrder(ElementType.TEXTLINE,reverse=reverse);
				if(order.length > 0){
					if(this._selectedElements.length > 0){
						const last = reverse ? this._selectedElements.map(s => order.indexOf(s)).sort()[0]:
											  this._selectedElements.map(s => order.indexOf(s)).sort().reverse()[0];
						if(last > -1){
							// Reorder
							order = order.slice(last+1).concat(order.slice(0,last+1));
						}
					}
					this.select(order[0]);
				}
			}
		} else if (mode === Mode.TEXT){
			if(this.selectedType == ElementType.TEXTLINE){
				let order = this.getSelectOrder(ElementType.TEXTLINE,reverse=reverse);
				if(order.length > 0){
					if(this._selectedElements.length > 0){
						const last = reverse ? this._selectedElements.map(s => order.indexOf(s)).sort()[0]:
											  this._selectedElements.map(s => order.indexOf(s)).sort().reverse()[0];
						if(last > -1){
							// Reorder
							order = order.slice(last+1).concat(order.slice(0,last+1));
						}
					}
					this.select(order[0]);
				}
			} else {
				let order = this.getSelectOrder(ElementType.TEXTLINE,reverse=reverse);
				if(order.length > 0){
					this.select(order[0]);
				}
			}
		}
	}

	/**
	 * Retrieve the current order of selection for all objects of a given type
	 * 
	 * @param {*} type 
	 * @param {*} parentID 
	 */
	getSelectOrder(type=ElementType.SEGMENT,reverse=false,parentID=null){
		const anchors = {}; // Centers of all objects to compare
		const addCompare = (o) => {
			if(o.points && o.points.length > 0){
				let top = o.points.map((point) => point.y).sort()[0];
				let left = o.points.map((point) => point.x).sort()[0];
				anchors[o.id] = {x:left,y:top};
			}
		};
		const tlbr = (o1,o2) => { // TopLeft -> BottomRight comperator
			const anchorA = anchors[o1.id];
			const anchorB = anchors[o2.id];
			const delta = anchorA.y - anchorB.y;
			if (delta != 0) {
				return delta;
			} else {
				return anchorA.x - anchorB.x;
			}
		};

		const segmentation = this._controller.getCurrentSegmentation();
		let order = [];
		if(segmentation){
			if(type === ElementType.SEGMENT){
				order = segmentation.readingOrder ? JSON.parse(JSON.stringify(segmentation.readingOrder)) : [];
				order.filter(id => Object.keys(segmentation.segments).includes(id));
				let segments = Object.entries(segmentation.segments).map(([_,s]) => s)
												.filter(s => !order.includes(s.id));
				// Add segments anchors to compare
				for(const segment of segments){
					addCompare(segment);
				}
				// Add sorted segments that are not in readingOrder 
				for(const id of segments.sort(tlbr).map(s => s.id)){
					if(!order.includes(id)){
						order.push(id);
					}
				}
			} else if (type === ElementType.REGION){
				let regions = [];
				for(const [_,polygons] of Object.entries(this._controller.getCurrentSettings().regions)){
					regions = regions.concat(Object.keys(polygons));
				}
				// Add sorted regions
				for(const region of regions){
					addCompare(region);
				}
				order = order.concat(regions.sort(tlbr).map(s => s.id));
			} else if (type === ElementType.TEXTLINE) {
				const segments = parentID ? [parentID] : this.getSelectOrder(ElementType.SEGMENT);
				for(const id of segments){
					if(Object.keys(segmentation.segments).includes(id)){
						const textlinesRO = segmentation.segments[id].readingOrder;
						if(textlinesRO && textlinesRO.length > 0){
							order = order.concat(textlinesRO);
						}

						// Add sorted textlines
						if(segmentation.segments[id].textlines){
							let textlines = Object.entries(segmentation.segments[id].textlines).map(([_,t]) => t);
							if(textlinesRO){
								textlines = textlines.filter(t => !textlinesRO.includes(t.id));
							}
							if(textlines && textlines.length > 0){
								for(const textline of textlines){
									addCompare(textline);
								}
								order = order.concat(textlines.sort(tlbr).map(l => l.id));
							}
						}
					}
				}
			} else if (type === ElementType.CONTOUR) {
				// TODO, maybe?
			}

		}
		if(reverse){
			return order.reverse();
		} else {
			return order;
		}
	}

	/**
	 * Unselect everything currently selected
	 */
	unSelect() {
		// Unselecting everything visually
		if(this.selectedType == ElementType.CONTOUR) {
			this._editor.highlightContours(this._selectedElements,false);
		} else {
			for(const id of this._selectedElements){
				this._editor.selectSegment(id,false,false);
				this._editor.highlightSegment(id,false);
				if(this._textviewer.isOpen()){
					this._controller.updateTextLine(id);
				}
			}
		}
		// Get everything that could be overseen
		paper.project.selectedItems.forEach(i => {if(i.elementID) this._selectPolygon(i.elementID,false);});

		// Reset selected
		this._selectedElements = [];
		this._selectedPoints = [];
		this._selectedContours = [];
		this.selectedType = null;

		this._postSelection();

		if(this._editor.mode !== ElementType.CONTOUR){
			this._editor.resetFocus();
		}
	}

	/**
	 * Unselect a segment by its id
	 * 
	 * @param {string} segmentID 
	 */
	unSelectSegment(segmentID) {
		this._selectPolygon(segmentID, false, false);
		this._editor.resetFocus();

		this._postSelection();
		if(this._textviewer.isOpen()){
			this._controller.updateTextLine(segmentID);
		}
	}


	/**
	 * Check if a given segment is selected by its id
	 * 
	 * @param {string} id 
	 */
	isSegmentSelected(id) {
		if (this._selectedElements && $.inArray(id, this._selectedElements) >= 0) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Begin a box selection action, starting from a startPoint 
	 * 
	 * @param {[number,number]} startPoint GUI point to start the selection on (e.g. mouse event pos)
	 */
	boxSelect(startPoint) {
		if(this._editor.mode == ElementType.CONTOUR && this.selectedType != ElementType.CONTOUR){
			this.unSelect();
			this.selectedType = ElementType.CONTOUR;
		}
		if (!this._editor.isEditing) {
			if (!this.isSelecting) {
				this._editor.boxSelect((tl,br) => {this._selectInBox(tl,br)},(a,b)=>{},startPoint);
			}

			this.isSelecting = true;
		}
	}

	/**
	 * Get a copy of all currently selected segments
	 */
	getSelectedSegments() {
		return [...this._selectedElements];
	}

	/**
	 * Get a copy of all currently selected points
	 */
	getSelectedPoints() {
		return [...this._selectedPoints];
	}

	/**
	 * Get a copy of all currently selected contours
	 */
	getSelectedContours() {
		return [...this._selectedContours];
	}

	/**
	 * Get the type of the currently selected polygon
	 */
	getSelectedPolygonType() {
		return this.selectedType;
	}

	/**
	 * Check if points of a single element are currently selected
	 */
	hasElementPointsSelected() {
		return (this.getSelectedPolygonType() === ElementType.SEGMENT || this.getSelectedPolygonType() === ElementType.TEXTLINE)
				&& this.getSelectedSegments().length === 1 
				&& this.getSelectedPoints().length > 0;
	}

	/**
	 * Helper function for the selection/deselection of a polygon by its id.
	 * 
	 * @param {string} id 	Id of the poilygon that is to be selected
	 * @param {Boolean} doSelect 	True=Select polygon, False=Unselect polygon
	 * @param {Boolean} displayPoints 	True:Display the points of the polygon, False: Do not display the points of the polygon
	 */
	_selectPolygon(id, doSelect = true, displayPoints = false) {
		const selectIndex = this._selectedElements.indexOf(id);
		const isInList = selectIndex >= 0;
		if (doSelect){ 
			if(!isInList)
				this._selectedElements.push(id);
			this._editor.selectSegment(id, true, displayPoints);
		} else {
			if(isInList)
				this._selectedElements.splice(selectIndex, 1);
			this._editor.selectSegment(id, false, displayPoints);
		}
		if(this._controller.getMode() === Mode.LINES ){
			const idType = this._controller.getIDType(id);
			if(idType === ElementType.SEGMENT){
				this._editor.focusSegment(id,true);
			}else if(idType === ElementType.TEXTLINE){
				const parent = this._controller.textlineRegister[id];
				this._editor.focusSegment(parent,true);
			}
		}
	}

	/**
	 * Helper function for the selection of points inside a polygon.
	 * Will add new points if points do not already exist in polygon. 
	 * 
	 * @param {string} id 	Id of the polygon that is to be selected
	 * @param {[[number,number]]} points  	Image point coordinates to select/add
	 */
	_selectAndAddPoints(id, points){
		this._selectPolygon(id,true,true);
		for(const point of points) {
			if(this._selectedPoints.indexOf(point) == -1)
				this._selectedPoints.push(point)
		}

		const notExistFallback = (i,p) => {
				this._controller.transformSegment(i,this._editor.addPointsOnLine(i,p));
				this._selectAndAddPoints(i, p);	
			};
		this._editor.selectSegmentPoints(id, this._selectedPoints, notExistFallback);
	}

	/**
	 * Post selection helper to enable specific behaviour after a change in the selection
	 */
	_postSelection(){
		// Additional after select behaviour
		if((this.selectedType === ElementType.SEGMENT || this.selectedType === ElementType.TEXTLINE) && this._selectedElements.length === 1){
			const id = this._selectedElements[0];
			
			if(this._controller.getMode() === Mode.SEGMENT || this._controller.getMode() === Mode.EDIT){
				this._selectPolygon(id,true,true);	
				this._editor.startPointSelect(id, (id,point) => this.select(id,[point]));
			} else if(this._controller.getMode() === Mode.LINES){
				if(this.selectedType == ElementType.TEXTLINE){
					this._selectPolygon(id,true,true);	
					this._editor.startPointSelect(id, (id,point) => this.select(id,[point]));
				}
			} else {
				this._selectPolygon(id,true,false);	
			}
		} else {
			this._editor.endPointSelect();
			// Only display points if only one segment is selected
			this._selectedElements.forEach(id => this._editor.selectSegment(id,true,false));
		}

		if(this._controller.getMode() === Mode.LINES) {
			if((this.selectedType === ElementType.SEGMENT || this.selectedType === ElementType.TEXTLINE) && this._selectedElements.length > 0){
				this._controller.forceUpdateReadingOrder();
			}else{
				this._controller.displayReadingOrder(false);
				this._controller.forceUpdateReadingOrder();
			}
		}

		if(this._controller.getMode() === Mode.TEXT){
			if(this._selectedElements.length == 0){
				this._controller.closeEditLine();
			}
		}
		if(this.selectedType === ElementType.REGION)
			this._controller.scaleSelectedRegion();
	}

	/**
	 * Helper function to select objects inbetween two points from the canvas
	 * 
	 * @param {[number,number]} pointA
	 * @param {[number,number]} pointB 
	 */
	_selectInBox(pointA, pointB) {
		const mode = this._controller.getMode();

		if(this._selectedElements.length === 1 && 
			((this.selectedType === ElementType.SEGMENT && (mode === Mode.SEGMENT || mode === Mode.EDIT)) 
			|| (this.selectedType === ElementType.TEXTLINE && mode === Mode.LINES))){
			// Select points
			const id = this._selectedElements[0];
			const inbetween = this._editor.selectPointsInbetween(pointA, pointB, id);

			for (const point of inbetween) {
				if (this._selectedPoints.indexOf(point) < 0) {
					// Has not been selected before => select
					this._selectedPoints.push(point);
				}
			}
			this._editor.selectSegmentPoints(id,this._selectedPoints);
		} else if (this.selectedType == ElementType.CONTOUR) {
			const inbetween = this._editor.selectContoursInbetween(pointA, pointB);
			for(const contour of inbetween) {
				if(!this._selectedContours.includes(contour))
					this._selectedElements.push(contour);
			}
			
			this._editor.highlightContours(inbetween);
		} else {
			this.unSelect();
			// Select segments
			const inbetween = this._editor.getSegmentIDsBetweenPoints(pointA, pointB);

			if(mode === Mode.SEGMENT || mode === Mode.EDIT){
				for(const id of inbetween) {
					const idType = this._controller.getIDType(id);
					if (idType === ElementType.SEGMENT) {
						this._selectedElements.push(id);
						this._editor.selectSegment(id, true);
					}
				}
				this.selectedType = ElementType.SEGMENT;
			} else if(mode === Mode.LINES){
				const lines = inbetween.filter((id) => this._controller.getIDType(id) === ElementType.TEXTLINE);
				if(lines.length > 0){
					const parents = lines.map((id) => this._controller.textlineRegister[id]).sort();
					// Create counter object for parents accurences
					const parents_counter = {}; 
					parents.forEach(p => {parents_counter[p] = (parents_counter[p] || 0) + 1}); 
					// Sort and retrieve most represented/dominant parent
					const [dominant_parent,_] = [...Object.entries(parents_counter)].sort((a, b) => b[1] - a[1])[0];

					// Focus dominant parent
					this._editor.focusSegment(dominant_parent,true);

					// Filter and display all lines with the dominant parent 
					for (const id of lines){
						if(this._controller.textlineRegister[id] == dominant_parent){
							this._selectedElements.push(id);
							this._editor.selectSegment(id, true);
						}
					}

					this.selectedType = ElementType.TEXTLINE;
				}
			}
		}

		this.isSelecting = false;
	}
}
