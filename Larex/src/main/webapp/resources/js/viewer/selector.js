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
		
		const notExistFallback = (id,points) => this._controller.transformSegment(id, this._editor.addPointsOnLine(id,points));
		
		if(!points){
			//// Select polygon
			// Unselect others if this is not of type segment or if not select multiple
			if(polygonType != "segment" || !this.selectMultiple)
				this.unSelect();

			console.log((polygonType != "segment" || !this.selectMultiple));
			// Unselect edit points
			this._selectedSegments.forEach(s =>{
				this._editor.selectSegmentPoints(s,[]);
				this._editor.setEditSegment(s,false);
			});

			// Check if already selected
			const selectIndex = this._selectedSegments.indexOf(id);
			const doSelect = selectIndex < 0;
			if (doSelect){ 
				// Has not been selected before => select
				this._selectedSegments.push(id);
			} else {
				// Has been selected before => unselect
				this._selectedSegments.splice(selectIndex, 1);
			}
			// Update Viewer display
			const displayPoints = this._selectedSegments.length == 1;
			this._editor.selectSegment(id, doSelect, displayPoints);
		} else {
			console.log(points);
			//// Select point if possible
			if(polygonType != "segment")
				throw Error("Tried to select points of a polygon that is not a segment.");

			// Unselect previous
			if(this._selectedSegments.length != 1 || this._selectedSegments[0] != id || !this.selectMultiple)
				this.unSelect()

			points.forEach(point => {
				if(this._selectedPoints.indexOf(point) == -1)
					this._selectedPoints.push(point)
			});
			
			// Update Viewer display
			this._editor.selectSegment(id, true, true);
			this._editor.selectSegmentPoints(id, points, notExistFallback);
		}

		this.selectedType = polygonType;

		// Additional after select behaviour
		if(this.selectedType === "segment" && this._selectedSegments.length === 1)
			this._editor.startPointSelect(id, (id,point) => this.select(id,[point]))
		else
			this._editor.endPointSelect();

		if(this.selectedType === 'region')
			this._controller.scaleSelected();

		console.log(this);
	}

	/**
	 * Unselect given segments. Default: all selected.
	 */
	unSelect(segments = this._selectedSegments) {
		segments.forEach(id => {
			this._editor.selectSegment(id, false);
			this._editor.setEditSegment(id,false);
		});

		this._selectedSegments = this._selectedSegments.filter(s => segments.indexOf(s) == -1);

		if(this._selectedSegments.length === 1){
			this._editor.setEditSegment(this._selectedSegments[0],true);

			if(this._selectedPoints.length > 0)
				this._editor.selectSegmentPoints(this._selectedSegments[0],this._selectedPoints)
		} else {
			this._selectedPoints = [];
		}

		this._selectedContours = [];
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
