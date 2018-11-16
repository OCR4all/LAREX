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

	// Select an object via their identifier and points inside the object
	// Guess element type via id or if provided take the provided type
	select(id, points, elementType=this._controller.getIDType(id)) {
		if(elementType == ElementType.CONTOUR){			
			if(!this.selectMultiple || this._selectedType != elementType)
				this.unSelect()
			this._selectedElements.push(id);
			this._editor.highlightContours([id]);
		} else {
			const selectIndex = this._selectedElements.indexOf(id);
			const isSelected = selectIndex >= 0;

			// Select logic
			if(points && isSelected){
				//// Select point if possible
				if(elementType != "segment")
					throw Error("Tried to select points of a polygon that is not a segment.");

				// Unselect previous
				if(this._selectedElements.length != 1 || this._selectedElements[0] != id || !this.selectMultiple)
					this.unSelect()
				
				this._selectPolygon(id, true, true);
				this._selectAndAddPoints(id, points);
			} else {
				//// Select polygon
				// Unselect others if this is not of type segment or if not select multiple
				if(elementType != "segment" || !this.selectMultiple)
					this.unSelect();

				this._selectPolygon(id, !isSelected);
			}

			this.selectedType = elementType;

			this._postSelection();
		}
	}

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
	}

	_selectAndAddPoints(id, points){
		this._selectPolygon(id,true,true);
		points.forEach(point => {
			if(this._selectedPoints.indexOf(point) == -1)
				this._selectedPoints.push(point)
		});

		const notExistFallback = (i,p) => {
				this._controller.transformSegment(i,this._editor.addPointsOnLine(i,p));
				this._selectAndAddPoints(i, p);	
			};
		this._editor.selectSegmentPoints(id, this._selectedPoints, notExistFallback);
	}

	unSelect() {
		//// Unselecting everything visually
		if(this.selectedType == ElementType.CONTOUR)
			this._editor.highlightContours(this._selectedElements,false);
		else
			this._selectedElements.forEach(id => this._editor.selectSegment(id,false,false));
		// Get everything that could be overseen
		paper.project.selectedItems.forEach(i => {if(i.elementID) this._selectPolygon(i.elementID,false)});

		// Reset selected
		this._selectedElements = []
		this._selectedPoints = [];
		this._selectedContours = [];
		this.selectedType = null;

		this._postSelection();
	}

	unSelectSegment(segmentID){
		this._selectPolygon(segmentID, false, false);

		this._postSelection();
	}

	_postSelection(){
		// Additional after select behaviour
		if(this.selectedType === "segment" && this._selectedElements.length === 1){
			const id = this._selectedElements[0];
			this._selectPolygon(id,true,true);	
			this._editor.startPointSelect(id, (id,point) => this.select(id,[point]));
		} else {
			this._editor.endPointSelect();
			// Only display points if only one segment is selected
			this._selectedElements.forEach(id => this._editor.selectSegment(id,true,false));
		}

		if(this.selectedType === 'region')
			this._controller.scaleSelectedRegion();
	}

	isSegmentSelected(id) {
		if (this._selectedElements && $.inArray(id, this._selectedElements) >= 0) {
			return true;
		} else {
			return false;
		}
	}

	boxSelect() {
		if(this._editor.mode == ElementType.CONTOUR && this.selectedType != ElementType.CONTOUR)
			this.unSelect();
			this.selectedType = ElementType.CONTOUR;
		if (!this._editor.isEditing) {
			if (!this.isSelecting) {
				this._editor.boxSelect((tl,br) => {this._selectInBox(tl,br)});
			}

			this.isSelecting = true;
		}
	}

	_selectInBox(pointA, pointB) {
		if(this._selectedElements.length === 1 && this.selectedType === 'segment'){
			// Select points
			const id = this._selectedElements[0];

			const inbetween = this._editor.selectPointsInbetween(pointA, pointB, id);

			inbetween.forEach((point) => {
				if (this._selectedPoints.indexOf(point) < 0) {
					// Has not been selected before => select
					this._selectedPoints.push(point);
				}
			});
			this._editor.selectSegmentPoints(id,this._selectedPoints);
		} else if (this.selectedType == ElementType.CONTOUR) {
			const inbetween = this._editor.selectContoursInbetween(pointA, pointB);
			inbetween.forEach(contour => {
				this._selectedElements.push(contour);
			});
			
			this._editor.highlightContours(inbetween);
		} else {
			this.unSelect();
			// Select segments
			const inbetween = this._editor.getSegmentIDsBetweenPoints(pointA, pointB);

			inbetween.forEach((id) => {
				const idType = this._controller.getIDType(id);
				if (idType === 'segment') {
					this._selectedElements.push(id);
					this._editor.selectSegment(id, true);
				}
			});
		}

		this.selectedType = "segment";
		this.isSelecting = false;
	}

	getSelectedSegments() {
		return this._selectedElements;
	}

	getSelectedPoints() {
		return this._selectedPoints;
	}

	getSelectedContours() {
		return this._selectedContours;
	}

	getSelectedPolygonType() {
		return this.selectedType;
	}
}
