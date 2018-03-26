function Selector(_editor,_controller) {
	this.selectmultiple = false;
	this.selectpoints = false;
	this.isSelecting = false;
	let _selectedSegments = [];
	let _selectedPoints = [];
	let _selectType;

	this.select = function(segmentID, selectType, points = []){
		if(!this.selectpoints) {
			// Select segment of result or region
			if(_selectType !== selectType || !this.selectmultiple){
				this.unSelect();
			}	
			this._processSelectSegment(segmentID);
		}else{
			// Select points
			if($.inArray(_selectedSegments,segmentID) === -1 || _selectedSegments.length !== 1){
				this.unSelect();
				this._processSelectSegment(segmentID);
			}
			if(this.selectmultiple){
				points.forEach((point) => {
					this._processSelectPoint(point);
				});
			}else if(points.length > 0){
				// Take last point (multiple are not allowed)
				this._processSelectPoint(points[points.length-1]);
			}
		}
		_selectType = selectType;
	}
	
	this.unSelect = function(){
		for (let i = 0, selectedsize = _selectedSegments.length; i < selectedsize; i++) {
			_editor.selectSegment(_selectedSegments[i], false);
		}
		_selectedSegments = [];
		_selectedPoints = [];
	}

	this.hasSegmentsSelected = function(){
		if(_selectedSegments && _selected.length > 0){
			return true;
		}else{
			return false;
		}
	}
	this.isSegmentSelected = function(segmentID){
		if(_selectedSegments && $.inArray(segmentID, _selectedSegments) >= 0){
			return true;
		}else{
			return false;
		}
	}
	this.startRectangleSelect = function(){
		if(!_editor.isEditing){
			if(!this.isSelecting){
				_editor.startRectangleSelect();
			}

			this.isSelecting = true;
		}
	}

	this.rectangleSelect = function(pointA,pointB) {
		if ((!this.selectmultiple) || !(_selectType === 'segment' || _selectType === 'point')) {
			this.unSelect();
		}

		const inbetween = _editor.getSegmentIDsBetweenPoints(pointA,pointB);

		$.each(inbetween, (index, id) => {
			const idType = _controller.getIDType(id);
			if(idType === 'segment'){
				_selectedSegments.push(id);
				_editor.selectSegment(id, true);
			}
		});

		_selectType = 'segment';
		this.isSelecting = false;
	}

	this.getSelectedSegments = function(){
		return _selectedSegments;
	}

	this.getSelectedPoints = function(){
		return _selectedPoints;
	}

	this.getSelectedType = function(){
		return _selectType;
	}

	//***** private methods ****//
	// Handels if a point has to be selected or unselected
	this._processSelectPoint = function(point){
		const selectIndex = _selectedPoints.indexOf(point);
		if(selectIndex < 0){
			// Has net been selected before => select
			_selectedPoints.push(point);
		} else {
			// Has been selected before => unselect
			_selectedPoints.splice(selectIndex,1);
		}
	}

	// Handels if a segment has to be selected or unselected
	this._processSelectSegment = function(segmentID){
		const selectIndex = _selectedSegments.indexOf(segmentID);
		if(selectIndex < 0){
			// Has not been selected before => select
			_editor.selectSegment(segmentID, true);
			_selectedSegments.push(segmentID);
		} else {
			// Has been selected before => unselect
			_editor.selectSegment(segmentID, false);
			_selectedSegments.splice(selectIndex,1);
		}
	}
}
