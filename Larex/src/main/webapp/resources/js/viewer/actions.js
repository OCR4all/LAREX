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
	var _viewer = viewer;
	var _settings = settings;
	var _page = page;
	var _regionPolygon = regionPolygon;
	var _oldType = regionPolygon.type;
	var _newType = newType;
	var _controller = controller;

	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;
			_regionPolygon.type = _newType;
			delete _settings.regions[_oldType].polygons[_regionPolygon.id];
			_settings.regions[_newType].polygons[_regionPolygon.id] = _regionPolygon;
			_viewer.updateSegment(_regionPolygon);
			if(_controller != null){
				_controller.hideRegion(_newType,false);
			}
			console.log('Do - Change Type: {"id":"'+_regionPolygon.id+'","points":[..],"type":"'+_oldType+'->'+_newType+'"}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;

			_regionPolygon.type = _oldType;
			delete _settings.regions[_newType].polygons[_regionPolygon.id];
			_settings.regions[_oldType].polygons[_regionPolygon.id] = _regionPolygon;
			_viewer.updateSegment(_regionPolygon);
			if(_controller != null){
				_controller.hideRegion(_oldType,false);
			}
			console.log('Undo - Change Type: {"id":"'+_regionPolygon.id+'","points":[..],"type":"'+_oldType+'->'+_newType+'"}');
		}
	}
}

function ActionChangeTypeSegment(segmentID,newType,viewer,segmentation,page){
	var _isExecuted = false;
	var _viewer = viewer;
	var _segmentation = segmentation;
	var _page = page;
	var _segmentID = segmentID;
	var _oldType = null;
	var _newType = newType;

	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;

			var segment = segmentation.pages[page].segments[segmentID];
			if(_oldType == null){
				_oldType = segment.type;
			}
			segment.type = _newType;
			_viewer.updateSegment(segment);
			console.log('Do - Change Type: {"id":"'+_segmentID+'","points":[..],"type":"'+_oldType+'->'+_newType+'"}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;

			var segment = segmentation.pages[page].segments[segmentID];
			segment.type = _oldType;
			_viewer.updateSegment(segment);
			console.log('Undo - Change Type: {"id":"'+_segmentID+'","points":[..],"type":"'+_oldType+'->'+_newType+'"}');
		}
	}
}

function ActionAddRegion(id,points,type,editor,settings,page, controller){
	var _isExecuted = false;
	var _region = {id:id, points:points, type:type, isRelative:true};
	var _editor = editor;
	var _settings = settings;
	var _page = page;
	var _controller = controller;

	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;
			_settings.regions[type].polygons[_region.id] = _region;
			_editor.addRegion(_region);
			if(_controller != null){
				_controller.hideRegion(_region.type,false);
			}
			console.log('Do - Add Region Polygon: {"id":"'+_region.id+'","points":'+_region.points+',"type":"'+_region.type+'"}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;
			delete _settings.regions[type].polygons[_region.id];
			_editor.removeRegion(_region.id);
			console.log('Undo - Add Region Polygon: {"id":"'+_region.id+'","points":'+_region.points+',"type":"'+_region.type+'"}');
		}
	}
}

function ActionRemoveRegion(regionPolygon,editor,settings,page,controller){
	var _isExecuted = false;
	var _editor = editor;
	var _settings = settings;
	var _page = page;
	var _region = regionPolygon;
	var _controller = controller;

	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;

			delete _settings.regions[_region.type].polygons[_region.id];
			_editor.removeRegion(_region.id);
			console.log('Do - Remove Region Polygon: {"id":"'+_region.id+'","points":'+_region.points+',"type":"'+_region.type+'"}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;
			_settings.regions[_region.type].polygons[_region.id] = _region;
			_editor.addRegion(_region);
			if(_controller != null){
				_controller.hideRegion(_region.type,false);
			}
			console.log('Undo - Remove Region Polygon: {"id":"'+_region.id+'","points":'+_region.points+',"type":"'+_region.type+'"}');
		}
	}
}

function ActionRemoveCompleteRegion(regionType,controller,editor,settings,controller){
	var _isExecuted = false;
	var _editor = editor;
	var _controller = controller;
	var _settings = settings;
	var _region = JSON.parse(JSON.stringify(settings.regions[regionType]));
	var _controller = controller;

	//TODO redo after undo does not work
	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;
			var region = _settings.regions[_region.type];
			_controller.removePresentRegions(_region.type);

			// Iterate over all Polygons in Region
			Object.keys(_region.polygons).forEach(function(polygonKey) {
				_editor.removeRegion(polygonKey);
			});

			delete _settings.regions[_region.type];

			console.log('Do - Remove Region "'+_region.type+'"}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;
			_settings.regions[_region.type] = JSON.parse(JSON.stringify(_region));
			_controller.addPresentRegions(_region.type);

			// Iterate over all Polygons in Region
			Object.keys(_region.polygons).forEach(function(polygonKey) {
				_editor.addRegion(_region.polygons[polygonKey]);
				_controller.hideRegion(_region.type,false);
			});

			console.log('Undo - Remove Region "'+_region.type+'"}');
		}
	}
}

function ActionAddFixedSegment(id,points,type,editor,settings,page){
	var _isExecuted = false;
	var _segment = {id:id, points:points, type:type, isRelative:false};
	var _editor = editor;
	var _settings = settings;
	var _page = page;

	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;
			_settings.pages[page].segments[_segment.id] = _segment;
			_editor.addSegment(_segment,true);
			console.log('Do - Add Region Polygon: {"id":"'+_segment.id+'","points":'+_segment.points+',"type":"'+_segment.type+'"}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;
			delete _settings.pages[page].segments[_segment.id];
			_editor.removeSegment(_segment.id);
			console.log('Undo - Add Region Polygon: {"id":"'+_segment.id+'","points":'+_segment.points+',"type":"'+_segment.type+'"}');
		}
	}
}

function ActionRemoveSegment(segment,editor,segmentation,page,exportSettings){
	var _isExecuted = false;
	var _editor = editor;
	var _segmentation = segmentation;
	var _page = page;
	var _segment = JSON.parse(JSON.stringify(segment));
	var _exportSettings = exportSettings;

	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;

			delete _segmentation.pages[_page].segments[_segment.id];
			_editor.removeSegment(_segment.id);

			if(_exportSettings[_page].segmentsToIgnore == null){
				_exportSettings[_page].segmentsToIgnore = [];
			}
			_exportSettings[_page].segmentsToIgnore.push(_segment.id);

			console.log('Do - Remove: {"id":"'+_segment.id+'","points":'+_segment.points+',"type":"'+_segment.type+'"}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;

			_segmentation.pages[_page].segments[_segment.id] = JSON.parse(JSON.stringify(_segment));
			_editor.addSegment(_segment);
			_exportSettings[_page].segmentsToIgnore = jQuery.grep(_exportSettings[_page].segmentsToIgnore, function(value) {
				return value != _segment.id;
			});
			console.log('Undo - Remove: {"id":"'+_segment.id+'","points":'+_segment.points+',"type":"'+_segment.type+'"}');
		}
	}
}

function ActionAddCut(id,points,editor,settings,page){
	var _isExecuted = false;
	var _cut = {id:id, points:points, type:'other', isRelative:false};
	var _editor = editor;
	var _settings = settings;
	var _page = page;

	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;
			_settings.pages[page].cuts[_cut.id] = _cut;
			_editor.addLine(_cut);
			console.log('Do - Add Cut: {"id":"'+_cut.id+'","points":'+_cut.points+'"}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;
			delete _settings.pages[page].cuts[_cut.id];
			_editor.removeLine(_cut.id);
			console.log('Undo - Add Cut: {"id":"'+_cut.id+'","points":'+_cut.points+'"}');
		}
	}
}

function ActionRemoveCut(cut,editor,settings,page){
	var _isExecuted = false;
	var _editor = editor;
	var _settings = settings;
	var _page = page;
	var _cut = JSON.parse(JSON.stringify(cut));

	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;

			delete _settings.pages[_page].cuts[_cut.id];
			_editor.removeSegment(_cut.id);
			console.log('Do - Remove Cut: {"id":"'+_cut.id+'","points":'+_cut.points+',"type":"'+_cut.type+'"}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;

			_settings.pages[_page].cuts[_cut.id] = JSON.parse(JSON.stringify(_cut));
			_editor.addLine(_cut);
			console.log('Undo - Remove Cut: {"id":"'+_cut.id+'","points":'+_cut.points+',"type":"'+_cut.type+'"}');
		}
	}
}

function ActionTransformRegion(id,regionPolygon,regionType,viewer,settings,page,controller){
	var _isExecuted = false;
	var _viewer = viewer;
	var _settings = settings;
	var _page = page;
	var _id = id;
	var _regionType = regionType;
	var _newRegionPoints = JSON.parse(JSON.stringify(regionPolygon));
	var _oldRegionPoints = JSON.parse(JSON.stringify(_settings.regions[_regionType].polygons[_id].points));
	var _controller = controller;

	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;
			var region = _settings.regions[_regionType].polygons[_id];
			region.points = _newRegionPoints;
			_viewer.updateSegment(region);
			if(_controller != null){
				_controller.hideRegion(_regionType,false);
			}
			console.log('Do - Transform Region: {"id":"'+_id+' [..]}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;
			var region = _settings.regions[_regionType].polygons[_id];
			region.points = _oldRegionPoints;
			_viewer.updateSegment(region);
			if(_controller != null){
				_controller.hideRegion(_regionType,false);
			}
			console.log('Undo - Transform Region: {"id":"'+_id+' [..]}');
		}
	}
}

function ActionTransformSegment(id,segmentPoints,viewer,settings,page){
	var _isExecuted = false;
	var _viewer = viewer;
	var _settings = settings;
	var _page = page;
	var _id = id;
	var _newRegionPoints = JSON.parse(JSON.stringify(segmentPoints));
	var _oldRegionPoints = JSON.parse(JSON.stringify(_settings.pages[_page].segments[_id].points));

	this.execute = function(){
		if(!_isExecuted){
			_isExecuted = true;
			var segment = _settings.pages[_page].segments[_id];
			segment.points = _newRegionPoints;
			_viewer.updateSegment(segment);
			console.log('Do - Transform Segment: {"id":"'+_id+' [..]}');
		}
	}
	this.undo = function(){
		if(_isExecuted){
			_isExecuted = false;
			var segment = _settings.pages[_page].segments[_id];
			segment.points = _oldRegionPoints;
			_viewer.updateSegment(segment);
			console.log('Undo - Transform Segment: {"id":"'+_id+' [..]}');
		}
	}
}
