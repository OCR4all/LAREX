class Selector {
	constructor(editor, controller) {
		this._controller = controller;
		this._editor = editor;
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
				if(elementType != ElementType.SEGMENT && elementType != ElementType.TEXTLINE)
					throw Error("Tried to select points of a polygon that is not a segment and not a textline. ["+elementType+"]");

				// Unselect previous
				if((mode !== Mode.SEGMENT && mode !== Mode.LINES) || this._selectedElements.length != 1
					|| this._selectedElements[0] != id || !this.selectMultiple)
					this.unSelect()
				
				if(mode === Mode.SEGMENT){
					this._selectPolygon(id, true, true);
					this._selectAndAddPoints(id, points);
				}else if (mode === Mode.LINES){
					this._selectPolygon(id, true, true);
					this._selectAndAddPoints(id, points);
				}else if (mode === Mode.TEXT && elementType === ElementType.TEXTLINE){
					this._controller.editLine(id);
				}
			} else {
				//// Select polygon
				// Unselect others if this is not of type segment or if not select multiple
				const currentParent = this._selectedElements.length > 0 ? this._controller.textlineRegister[this._selectedElements[0]] : undefined;
				const selectParent =  this._selectedElements.length > 0 ? this._controller.textlineRegister[id] : undefined;
				if(!this.selectMultiple ||
					!((mode === Mode.SEGMENT && elementType === ElementType.SEGMENT) || 
						(mode === Mode.LINES && elementType === ElementType.TEXTLINE && currentParent == selectParent))){
					this.unSelect();
				}

				if (mode === Mode.TEXT && elementType === ElementType.TEXTLINE){
					this._controller.editLine(id);
				}else{
					this._selectPolygon(id, !isSelected);
				}
			}

			this.selectedType = elementType;

			this._postSelection();
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
			this._selectedElements.forEach(id => this._editor.selectSegment(id,false,false));
			this._selectedElements.forEach(id => this._editor.highlightSegment(id,false));
		}
		// Get everything that could be overseen
		paper.project.selectedItems.forEach(i => {if(i.elementID) this._selectPolygon(i.elementID,false);});

		// Reset selected
		this._selectedElements = [];
		this._selectedPoints = [];
		this._selectedContours = [];
		this.selectedType = null;

		this._postSelection();

		this._editor.displayOverlay("focus",false);
	}

	/**
	 * Unselect a segment by its id
	 * 
	 * @param {string} segmentID 
	 */
	unSelectSegment(segmentID) {
		this._selectPolygon(segmentID, false, false);
		this._editor.displayOverlay("focus",false);

		this._postSelection();
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
	 * Helper function for the selection/deselection of a polygon by its id.
	 * 
	 * @param {string} id 	Id of the poilygon that is to be selected
	 * @param {Boolean} doSelect 	True=Select polygon, False=Unselect polygon
	 * @param {Boolean} displayPoints 	True:Display the points of the polygon, False: Do not display the points of the polygon
	 */
	_selectPolygon(id, doSelect = true, displayPoints = false){
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
			
			if(this._controller.getMode() === Mode.SEGMENT){
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
			((this.selectedType === ElementType.SEGMENT && mode === Mode.SEGMENT) 
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

			if(mode === Mode.SEGMENT){
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
