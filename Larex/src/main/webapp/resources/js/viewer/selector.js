class Selector {
	constructor(editor,controller) {
		this._controller = controller;
		this._editor = editor;
		this.selectmultiple = false;
		this.selectpoints = false;
		this.isSelecting = false;
		this._selectedSegments = [];
		this._selectedPoints = [];
		this._selectType;
	}

	select(segmentID, points = []){
		const selectType = this._controller.getIDType(segmentID);
		if(!this.selectpoints) {
			// Select segment of result or region
			if(this._selectType !== selectType || !this.selectmultiple){
				this.unSelect();
			}	
			this._processSelectSegment(segmentID);
			this._selectType = selectType;
		}else if(selectType === 'segment'){
			// Select points
			if($.inArray(segmentID,this._selectedSegments) === -1 || this._selectedSegments.length !== 1){
				this.unSelect();
				this._processSelectSegment(segmentID);
			}
			if(this.selectmultiple){
				points.forEach((point) => {
					this._processSelectPoint(point,segmentID);
				});
			}else if(points.length > 0){
				this.unSelect();
				this._processSelectSegment(segmentID);
				// Select last segment (multiple are not allowed)
				this._processSelectPoint(points[points.length-1],segmentID);
			}
			this._selectType = selectType;
		}
	}
	
	unSelect(){
		for (let i = 0, selectedsize = this._selectedSegments.length; i < selectedsize; i++) {
			this._editor.selectSegment(this._selectedSegments[i], false);
		}
		this._selectedSegments = [];
		this._selectedPoints = [];
	}

	hasSegmentsSelected(){
		if(this._selectedSegments && _selected.length > 0){
			return true;
		}else{
			return false;
		}
	}
	isSegmentSelected(segmentID){
		if(this._selectedSegments && $.inArray(segmentID, this._selectedSegments) >= 0){
			return true;
		}else{
			return false;
		}
	}
	startRectangleSelect(){
		if(!this._editor.isEditing){
			if(!this.isSelecting){
				this._editor.startRectangleSelect();
			}

			this.isSelecting = true;
		}
	}

	rectangleSelect(pointA,pointB) {
		if(this.selectpoints){
			if(!(this._selectType === 'segment') || this._selectedSegments.length !== 1){
				this.unSelect();
			}else{
				const segmentID = this._selectedSegments[0];
				const inbetween = this._editor.getPointsBetweenPoints(pointA,pointB,segmentID);

				inbetween.forEach((point) => this._processSelectPoint(point,segmentID,false));
			}
		}else{
			if ((!this.selectmultiple) || !(this._selectType === 'segment')) {
				this.unSelect();
			}

			const inbetween = this._editor.getSegmentIDsBetweenPoints(pointA,pointB);

			inbetween.forEach((id) => {
				const idType = this._controller.getIDType(id);
				if(idType === 'segment'){
					this._selectedSegments.push(id);
					this._editor.selectSegment(id, true);
				}
			});
		}
		this._selectType = 'segment';
		this.isSelecting = false;
	}

	getSelectedSegments(){
		return this._selectedSegments;
	}

	getSelectedPoints(){
		return this._selectedPoints;
	}

	getSelectedType(){
		return this._selectType;
	}

	//***** private methods ****//
	// Handels if a point has to be selected or unselected
	_processSelectPoint(point,segmentID,toggle = true){
		const selectIndex = this._selectedPoints.indexOf(point);
		if(selectIndex < 0 || !toggle){
			// Has not been selected before => select
			if(point){
				this._selectedPoints.push(point);
				this._editor.selectSegment(segmentID, true, this.selectpoints, point);
			}
		} else {
			// Has been selected before => unselect
			if(point){
				this._selectedPoints.splice(selectIndex,1);
				this._editor.selectSegment(segmentID, false, this.selectpoints, point);
			}
		}
	}

	// Handels if a segment has to be selected or unselected
	_processSelectSegment(segmentID){
		const selectIndex = this._selectedSegments.indexOf(segmentID);
		if(selectIndex < 0){
			// Has not been selected before => select
			this._editor.selectSegment(segmentID, true, this.selectpoints);
			this._selectedSegments.push(segmentID);
		} else {
			// Has been selected before => unselect
			this._editor.selectSegment(segmentID, false, this.selectpoints);
			this._selectedSegments.splice(selectIndex,1);
		}
	}
}
