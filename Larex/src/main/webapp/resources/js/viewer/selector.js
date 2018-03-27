function Selector(_editor,_controller) {
	this.selectmultiple = false;
	this.selectpoints = false;
	this.isSelecting = false;
	let _selectedSegments = [];
	let _selectedPoints = [];
	let _selectType;

	this.select = function(segmentID, points = []){
		const selectType = _controller.getIDType(segmentID);
		if(!this.selectpoints) {
			// Select segment of result or region
			if(_selectType !== selectType || !this.selectmultiple){
				this.unSelect();
			}	
			this._processSelectSegment(segmentID);
		}else{
			// Select points
			if($.inArray(segmentID,_selectedSegments) === -1 || _selectedSegments.length !== 1){
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
		if(this.selectpoints){
			if(!(_selectType === 'segment') || _selectedSegments.length !== 1){
				this.unSelect();
			}else{
				const segmentID = _selectedSegments[0];
				const inbetween = _editor.getPointsBetweenPoints(pointA,pointB,segmentID);

				inbetween.forEach((point) => this._processSelectPoint(point,segmentID,false));
			}
		}else{
			if ((!this.selectmultiple) || !(_selectType === 'segment')) {
				this.unSelect();
			}

			const inbetween = _editor.getSegmentIDsBetweenPoints(pointA,pointB);

			inbetween.forEach((id) => {
				const idType = _controller.getIDType(id);
				if(idType === 'segment'){
					_selectedSegments.push(id);
					_editor.selectSegment(id, true);
				}
			});
		}
		_selectType = 'segment';
		this.isSelecting = false;
	}

	this.getSelectedSegments = function(){
		return _selectedSegments;
	}

	this.getSelectedPoints = function(){
		return _selectedPoints;_selectedPoints
	}

	this.getSelectedType = function(){
		return _selectType;
	}

	//***** private methods ****//
	// Handels if a point has to be selected or unselected
	this._processSelectPoint = function(point,segmentID,toggle = true){
		const selectIndex = _selectedPoints.indexOf(point);
		if(selectIndex < 0 || !toggle){
			// Has not been selected before => select
			if(point){
				_selectedPoints.push(point);
				_editor.selectSegment(segmentID, true, this.selectpoints, point);
			}
		} else {
			// Has been selected before => unselect
			if(point){
				_selectedPoints.splice(selectIndex,1);
				_editor.selectSegment(segmentID, false, this.selectpoints, point);
			}
		}
	}

	// Handels if a segment has to be selected or unselected
	this._processSelectSegment = function(segmentID){
		const selectIndex = _selectedSegments.indexOf(segmentID);
		if(selectIndex < 0){
			// Has not been selected before => select
			_editor.selectSegment(segmentID, true, this.selectpoints);
			_selectedSegments.push(segmentID);
		} else {
			// Has been selected before => unselect
			_editor.selectSegment(segmentID, false, this.selectpoints);
			_selectedSegments.splice(selectIndex,1);
		}
	}
}
