class Selector {
	constructor(editor, controller) {
		this._controller = controller;
		this._editor = editor;
		this.selectMultiple = false;
		this.isSelecting = false;
		this._selectedSegments = [];
		this._selectedPoints = [];
		this._selectedContours = [];
		this.selectedType;
	}

	select(id, points) {
		const polygonType = this._controller.getIDType(id);
		const selectIndex = this._selectedSegments.indexOf(id);
		const isSelected = selectIndex >= 0;

		// Select logic
		if(points && isSelected){
			//// Select point if possible
			if(polygonType != "segment")
				throw Error("Tried to select points of a polygon that is not a segment.");

			// Unselect previous
			if(this._selectedSegments.length != 1 || this._selectedSegments[0] != id || !this.selectMultiple)
				this.unSelect()
			
			this._selectPolygon(id, true, true);
			this._selectAndAddPoints(id, points);
		} else {
			//// Select polygon
			// Unselect others if this is not of type segment or if not select multiple
			if(polygonType != "segment" || !this.selectMultiple)
				this.unSelect();

			this._selectPolygon(id, !isSelected);
		}

		this.selectedType = polygonType;

		this._postSelection();
	}

	_selectPolygon(id, doSelect = true, displayPoints = false,){
		const selectIndex = this._selectedSegments.indexOf(id);
		const isInList = selectIndex >= 0;
		if (doSelect){ 
			if(!isInList)
				this._selectedSegments.push(id);
			this._editor.selectSegment(id, true, displayPoints);
		} else {
			if(isInList)
				this._selectedSegments.splice(selectIndex, 1);
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
		this._selectedSegments.forEach(id => this._editor.selectSegment(id,false,false));
		// Get everything that could be overseen
		paper.project.selectedItems.forEach(i => {if(i.polygonID) this._selectPolygon(i.polygonID,false)});

		// Reset selected
		this._selectedSegments = []
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
		if(this.selectedType === "segment" && this._selectedSegments.length === 1){
			const id = this._selectedSegments[0];
			this._selectPolygon(id,true,true);	
			this._editor.startPointSelect(id, (id,point) => this.select(id,[point]));
		} else {
			this._editor.endPointSelect();
			// Only display points if only one segment is selected
			this._selectedSegments.forEach(id => this._editor.selectSegment(id,true,false));
		}

		if(this.selectedType === 'region')
			this._controller.scaleSelectedRegion();
	}

	isSegmentSelected(id) {
		if (this._selectedSegments && $.inArray(id, this._selectedSegments) >= 0) {
			return true;
		} else {
			return false;
		}
	}
	boxSelect() {
		if (!this._editor.isEditing) {
			if (!this.isSelecting) {
				this._editor.boxSelect((x,y) => {this._selectInBox(x,y)});
			}

			this.isSelecting = true;
		}
	}


	_selectInBox(pointA, pointB) {
		if(this._selectedSegments.length === 1 && this.selectedType === 'segment'){
			// Select points
			const id = this._selectedSegments[0];

			const inbetween = this._editor.selectPointsInbetween(pointA, pointB, id);

			inbetween.forEach((point) => {
				if (this._selectedPoints.indexOf(point) < 0) {
					// Has not been selected before => select
					this._selectedPoints.push(point);
				}
			});
			this._editor.selectSegmentPoints(id,this._selectedPoints);
		} else {
			this.unSelect();
			// Select segments
			const inbetween = this._editor.getSegmentIDsBetweenPoints(pointA, pointB);

			inbetween.forEach((id) => {
				const idType = this._controller.getIDType(id);
				if (idType === 'segment') {
					this._selectedSegments.push(id);
					this._editor.selectSegment(id, true);
				}
			});
		}

		this.selectedType = "segment";
		this.isSelecting = false;
	}

	getSelectedSegments() {
		return this._selectedSegments;
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
