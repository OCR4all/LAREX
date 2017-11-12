function ActionController(controller){
	var _controller = controller;
	var _this = this;
	var _actions = {};
	var _actionpointers = {};

	this.redo = function(page) {
		var pageActions = _actions[page];
		var pageActionpointer = _actionpointers[page];
		if (pageActions && pageActionpointer < pageActions.length - 1) {
			_controller.unSelect();
			_actionpointers[page]++;
			pageActions[_actionpointers[page]].execute();
			
			// Reset Downloadable
			_controller.setPageDownloadable(page,false);
		}
	}
	this.undo = function(page) {
		var pageActions = _actions[page];
		var pageActionpointer = _actionpointers[page];
		if (pageActions && pageActionpointer >= 0) {
			_controller.unSelect();
			pageActions[pageActionpointer].undo();
			_actionpointers[page]--;

			// Reset Downloadable
			_controller.setPageDownloadable(page,false);
		}
	}
	this.resetActions = function(page){
		_actions[page] = [];
		_actionpointers[page] = -1;
	}
	this.addAndExecuteAction = function(action,page) {
		var pageActions = _actions[page];

		if(!pageActions){
			_this.resetActions(page);
			pageActions = _actions[page];
		}
		// Remove old undone actions
		if (pageActions.length > 0)
			pageActions = pageActions.slice(0, _actionpointers[page] + 1);

		// Execute and add new Action
		action.execute();
		pageActions.push(action);
		_actions[page] = pageActions;
		_actionpointers[page]++;
		
		// Reset Downloadable
		_controller.setPageDownloadable(page,false);
	}

}

//"Interface" for actions
function Action(){
	var _isExecuted = false;
	this.execute = function(){}
	this.undo = function(){}
}

function ActionMultiple(actions){
	var _isExecuted = false;

	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;
			for(var i = 0, actioncount = actions.length; i < actioncount; i++){
				actions[i].execute();
			}
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;
			for(var i = 0, actioncount = actions.length; i < actioncount; i++){
				actions[i].undo();
			}
		}
	}
}

function ActionChangeTypeRegionPolygon(regionPolygon,newType,viewer,settings,page, controller){
	var _isExecuted = false;
	var _oldType = regionPolygon.type;

	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;
			regionPolygon.type = newType;
			delete settings.regions[_oldType].polygons[regionPolygon.id];
			settings.regions[newType].polygons[regionPolygon.id] = regionPolygon;
			viewer.updateSegment(regionPolygon);
			if(controller != null){
				controller.hideRegion(newType,false);
			}
			console.log('Do - Change Type: {id:"'+regionPolygon.id+'",[..],type:"'+_oldType+'->'+newType+'"}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;

			regionPolygon.type = _oldType;
			delete settings.regions[newType].polygons[regionPolygon.id];
			settings.regions[_oldType].polygons[regionPolygon.id] = regionPolygon;
			viewer.updateSegment(regionPolygon);
			if(controller != null){
				controller.hideRegion(_oldType,false);
			}
			console.log('Undo - Change Type: {id:"'+regionPolygon.id+'",[..],type:"'+_oldType+'->'+newType+'"}');
		}
	}
}

function ActionChangeTypeSegment(segmentID,newType,viewer,controller,segmentation,page,exportSettings,isFixedSegment){
	var _isExecuted = false;
	var _segment;
	if(isFixedSegment){
		_segment = segmentation.pages[page].segments[segmentID];
	}else{
		_segment = segmentation[page].segments[segmentID];
	}
	var _oldType = _segment.type;
	var _actionReadingOrder = null;
	if(newType === 'image'){
		_actionReadingOrder = new ActionRemoveFromReadingOrder(segmentID,page,exportSettings,controller);
	}
	
	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;

			_segment.type = newType;
			viewer.updateSegment(_segment);
			controller.forceUpdateReadingOrder(true); //TODO try not to force to update all for changing type
			if(exportSettings){
				exportSettings[page].changedTypes[segmentID] = newType;
				if(_actionReadingOrder){
					_actionReadingOrder.execute();
				}
			}
			console.log('Do - Change Type: {id:"'+segmentID+'",[..],type:"'+_oldType+'->'+newType+'"}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;

			_segment.type = _oldType;
			viewer.updateSegment(_segment);
			controller.forceUpdateReadingOrder(true);
			if(exportSettings){
				exportSettings[page].changedTypes[segmentID] = _oldType;
				if(_actionReadingOrder){
					_actionReadingOrder.undo();
				}
			}
			console.log('Undo - Change Type: {id:"'+segmentID+'",[..],type:"'+_oldType+'->'+newType+'"}');
		}
	}
}

function ActionAddRegion(id,points,type,editor,settings,page, controller){
	var _isExecuted = false;
	var _region = {id:id, points:points, type:type, isRelative:true};

	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;
			settings.regions[type].polygons[_region.id] = _region;
			editor.addRegion(_region);
			if(controller != null){
				controller.hideRegion(_region.type,false);
			}
			console.log('Do - Add Region Polygon: {id:"'+_region.id+'",[..],type:"'+_region.type+'"}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;
			delete settings.regions[type].polygons[_region.id];
			editor.removeRegion(_region.id);
			console.log('Undo - Add Region Polygon: {id:"'+_region.id+'",type:"'+_region.type+'"}');
		}
	}
}

function ActionRemoveRegion(regionPolygon,editor,settings,page,controller){
	var _isExecuted = false;
	var _region = regionPolygon;

	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;

			delete settings.regions[_region.type].polygons[_region.id];
			editor.removeRegion(_region.id);
			console.log('Do - Remove Region Polygon: {id:"'+_region.id+'",[..],type:"'+_region.type+'"}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;
			settings.regions[_region.type].polygons[_region.id] = _region;
			editor.addRegion(_region);
			if(controller != null){
				controller.hideRegion(_region.type,false);
			}
			console.log('Undo - Remove Region Polygon: {id:"'+_region.id+'",[..],type:"'+_region.type+'"}');
		}
	}
}

function ActionRemoveCompleteRegion(regionType,controller,editor,settings,controller){
	var _isExecuted = false;
	var _region = JSON.parse(JSON.stringify(settings.regions[regionType]));

	//TODO redo after undo does not work
	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;
			var region = settings.regions[_region.type];
			controller.removePresentRegions(_region.type);

			// Iterate over all Polygons in Region
			Object.keys(_region.polygons).forEach(function(polygonKey) {
				editor.removeRegion(polygonKey);
			});

			delete settings.regions[_region.type];

			console.log('Do - Remove Region "'+_region.type+'"}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;
			settings.regions[_region.type] = JSON.parse(JSON.stringify(_region));
			controller.addPresentRegions(_region.type);

			// Iterate over all Polygons in Region
			Object.keys(_region.polygons).forEach(function(polygonKey) {
				editor.addRegion(_region.polygons[polygonKey]);
				controller.hideRegion(_region.type,false);
			});

			console.log('Undo - Remove Region "'+_region.type+'"}');
		}
	}
}

function ActionAddFixedSegment(id,points,type,editor,settings,page,exportSettings,controller){
	var _isExecuted = false;
	var _segment = {id:id, points:points, type:type, isRelative:false};

	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;
			settings.pages[page].segments[_segment.id] = _segment;
			editor.addSegment(_segment,true);
			if(exportSettings){
				exportSettings[page].segmentsToIgnore = jQuery.grep(exportSettings[page].segmentsToIgnore, function(value) {
					return value != _segment.id;
				});
			}
			console.log('Do - Add Region Polygon: {id:"'+_segment.id+'",[..],type:"'+_segment.type+'"}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;
			delete settings.pages[page].segments[_segment.id];
			editor.removeSegment(_segment.id);
			if(exportSettings){
				exportSettings[page].segmentsToIgnore.push(_segment.id);
			}
			console.log('Undo - Add Region Polygon: {id:"'+_segment.id+'",[..],type:"'+_segment.type+'"}');
		}
	}
}

function ActionRemoveSegment(segment,editor,segmentation,page,exportSettings,controller,isFixedSegment){
	var _isExecuted = false;
	var _segment = JSON.parse(JSON.stringify(segment));
	var _actionRemoveFromReadingOrder = new ActionRemoveFromReadingOrder(segment.id,page,exportSettings,controller);

	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;
			if(isFixedSegment){
				delete segmentation.pages[page].segments[_segment.id]; 
			}else{
				delete segmentation[page].segments[_segment.id]; 
			}
			editor.removeSegment(_segment.id);

			if(exportSettings){
				exportSettings[page].segmentsToIgnore.push(_segment.id);
				_actionRemoveFromReadingOrder.execute();
			}
			console.log('Do - Remove: {id:"'+_segment.id+'",[..],type:"'+_segment.type+'"}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;
			if(isFixedSegment){
				segmentation.pages[page].segments[_segment.id] = JSON.parse(JSON.stringify(_segment));
			}else{
				segmentation[page].segments[_segment.id] = JSON.parse(JSON.stringify(_segment));
			}
			editor.addSegment(_segment);
			if(exportSettings){
				exportSettings[page].segmentsToIgnore = jQuery.grep(exportSettings[page].segmentsToIgnore, function(value) {
					return value != _segment.id;
				});
				_actionRemoveFromReadingOrder.undo();
			}
			console.log('Undo - Remove: {id:"'+_segment.id+'",[..],type:"'+_segment.type+'"}');
		}
	}
}

function ActionAddCut(id,points,editor,settings,page){
	var _isExecuted = false;
	var _cut = {id:id, points:points, type:'other', isRelative:false};

	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;
			settings.pages[page].cuts[_cut.id] = _cut;
			editor.addLine(_cut);
			console.log('Do - Add Cut: {id:"'+_cut.id+'",[..]}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;
			delete settings.pages[page].cuts[_cut.id];
			editor.removeLine(_cut.id);
			console.log('Undo - Add Cut: {id:"'+_cut.id+'",[..]}');
		}
	}
}

function ActionRemoveCut(cut,editor,settings,page){
	var _isExecuted = false;
	var _cut = JSON.parse(JSON.stringify(cut));

	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;

			delete settings.pages[page].cuts[_cut.id];
			editor.removeSegment(_cut.id);
			console.log('Do - Remove Cut: {id:"'+_cut.id+'",[..],type:"'+_cut.type+'"}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;

			settings.pages[page].cuts[_cut.id] = JSON.parse(JSON.stringify(_cut));
			editor.addLine(_cut);
			console.log('Undo - Remove Cut: {id:"'+_cut.id+'",[..],type:"'+_cut.type+'"}');
		}
	}
}

function ActionTransformRegion(id,regionPolygon,regionType,viewer,settings,page,controller){
	var _isExecuted = false;
	var _id = id;
	var _regionType = regionType;
	var _newRegionPoints = JSON.parse(JSON.stringify(regionPolygon));
	var _oldRegionPoints = JSON.parse(JSON.stringify(settings.regions[_regionType].polygons[_id].points));

	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;
			var region = settings.regions[_regionType].polygons[_id];
			region.points = _newRegionPoints;
			viewer.updateSegment(region);
			if(controller != null){
				controller.hideRegion(_regionType,false);
			}
			console.log('Do - Transform Region: {id:"'+_id+' [..]}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;
			var region = settings.regions[_regionType].polygons[_id];
			region.points = _oldRegionPoints;
			viewer.updateSegment(region);
			if(controller != null){
				controller.hideRegion(_regionType,false);
			}
			console.log('Undo - Transform Region: {id:"'+_id+' [..]}');
		}
	}
}

function ActionTransformSegment(id,segmentPoints,viewer,settings,page){
	var _isExecuted = false;
	var _id = id;
	var _newRegionPoints = JSON.parse(JSON.stringify(segmentPoints));
	var _oldRegionPoints = JSON.parse(JSON.stringify(settings.pages[page].segments[_id].points));

	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;
			var segment = settings.pages[page].segments[_id];
			segment.points = _newRegionPoints;
			viewer.updateSegment(segment);
			console.log('Do - Transform Segment: {id:"'+_id+' [..]}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;
			var segment = settings.pages[page].segments[_id];
			segment.points = _oldRegionPoints;
			viewer.updateSegment(segment);
			console.log('Undo - Transform Segment: {id:"'+_id+' [..]}');
		}
	}
}

function ActionChangeReadingOrder(oldReadingOrder,newReadingOrder,controller,settings,page){
	var _isExecuted = false;
	var _oldReadingOrder = JSON.parse(JSON.stringify(oldReadingOrder));
	var _newReadingOrder = JSON.parse(JSON.stringify(newReadingOrder));

	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;

			settings[page].readingOrder = JSON.parse(JSON.stringify(_newReadingOrder));
			controller.forceUpdateReadingOrder(true);

			console.log('Do - Change Reading order');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;

			settings[page].readingOrder = JSON.parse(JSON.stringify(_oldReadingOrder));
			controller.forceUpdateReadingOrder(true);

			console.log('Undo - Change Reading order');
		}
	}
}

function ActionRemoveFromReadingOrder(segmentID,page,exportSettings,controller){
	var _isExecuted = false;
	var _oldReadingOrder = JSON.parse(JSON.stringify(exportSettings[page].readingOrder));
	var _newReadingOrder;

	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;

			if(!_newReadingOrder){
				var _newReadingOrder = JSON.parse(JSON.stringify(_oldReadingOrder));
				for(var index = 0; index < _newReadingOrder.length; index++){
					if(_newReadingOrder[index].id === segmentID){
						_newReadingOrder.splice(index,1);
						break;
					}
				}
			}
			
			exportSettings[page].readingOrder = JSON.parse(JSON.stringify(_newReadingOrder));
			controller.forceUpdateReadingOrder(true);
			console.log('Do - Remove from Reading Order: {id:"'+segmentID+'",[..]}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;
			
			exportSettings[page].readingOrder = JSON.parse(JSON.stringify(_oldReadingOrder));
			controller.forceUpdateReadingOrder(true);
			console.log('Undo - Remove from Reading Order: {id:"'+segmentID+'",[..]}');
		}
	}
}

function ActionAddToReadingOrder(segment,page,exportSettings,controller){
	var _isExecuted = false;
	var _oldReadingOrder = JSON.parse(JSON.stringify(exportSettings[page].readingOrder));
	var _newReadingOrder;

	this.execute = function(){
		if(!_isExecuted && segment.type !== 'image'){
			_isExecuted = true;

			if(!_newReadingOrder){
				var _newReadingOrder = JSON.parse(JSON.stringify(_oldReadingOrder));
				_newReadingOrder.push(segment);
			}
			
			exportSettings[page].readingOrder = JSON.parse(JSON.stringify(_newReadingOrder));
			controller.forceUpdateReadingOrder(true);
			console.log('Do - Add to Reading Order: {id:"'+segment.id+'",[..],type:"'+segment.type+'"}');
		}
	}
	this.undo = function(){
		if(_isExecuted && segment.type !== 'image'){
			_isExecuted = false;
			
			exportSettings[page].readingOrder = JSON.parse(JSON.stringify(_oldReadingOrder));
			controller.forceUpdateReadingOrder(true);
			console.log('Undo - Add to Reading Order: {id:"'+segment.id+'",[..],type:"'+segment.type+'"}');
		}
	}
}