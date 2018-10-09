function ActionController(controller) {
	const _controller = controller;
	const _this = this;
	let _actions = {};
	let _actionpointers = {};

	this.redo = function (page) {
		_controller.setChanged(page);
		let pageActions = _actions[page];
		let pageActionpointer = _actionpointers[page];
		if (pageActions && pageActionpointer < pageActions.length - 1) {
			_controller.unSelect();
			_actionpointers[page]++;
			pageActions[_actionpointers[page]].execute();
		}
	}
	this.undo = function (page) {
		_controller.setChanged(page);
		let pageActions = _actions[page];
		let pageActionpointer = _actionpointers[page];
		if (pageActions && pageActionpointer >= 0) {
			_controller.unSelect();
			pageActions[pageActionpointer].undo();
			_actionpointers[page]--;
		}
	}
	this.resetActions = function (page) {
		_actions[page] = [];
		_actionpointers[page] = -1;
	}
	this.addAndExecuteAction = function (action, page) {
		_controller.setChanged(page);
		let pageActions = _actions[page];

		if (!pageActions) {
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
	}

}

//"Interface" for actions
function Action() {
	let _isExecuted = false;
	this.execute = function () { }
	this.undo = function () { }
}

function ActionMultiple(actions) {
	let _isExecuted = false;

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;
			for (let i = 0, actioncount = actions.length; i < actioncount; i++) {
				actions[i].execute();
			}
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;
			for (let i = 0, actioncount = actions.length; i < actioncount; i++) {
				actions[i].undo();
			}
		}
	}
}

function ActionChangeTypeRegionPolygon(regionPolygon, newType, viewer, settings, page, controller) {
	let _isExecuted = false;
	const _oldType = regionPolygon.type;

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;
			regionPolygon.type = newType;
			delete settings.regions[_oldType].polygons[regionPolygon.id];
			settings.regions[newType].polygons[regionPolygon.id] = regionPolygon;
			viewer.updateSegment(regionPolygon);
			if (controller != null) {
				controller.hideRegion(newType, false);
			}
			console.log('Do - Change Type: {id:"' + regionPolygon.id + '",[..],type:"' + _oldType + '->' + newType + '"}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;

			regionPolygon.type = _oldType;
			delete settings.regions[newType].polygons[regionPolygon.id];
			settings.regions[_oldType].polygons[regionPolygon.id] = regionPolygon;
			viewer.updateSegment(regionPolygon);
			if (controller != null) {
				controller.hideRegion(_oldType, false);
			}
			console.log('Undo - Change Type: {id:"' + regionPolygon.id + '",[..],type:"' + _oldType + '->' + newType + '"}');
		}
	}
}

function ActionChangeTypeSegment(segmentID, newType, viewer, controller, segmentation, page) {
	let _isExecuted = false;
	let _segment = segmentation[page].segments[segmentID];
	const _oldType = _segment.type;
	let _actionReadingOrder = null;
	if (newType === 'image')
		_actionReadingOrder = new ActionRemoveFromReadingOrder(segmentID, page, segmentation, controller);
	let _actionSetFixed = null;
	if (!controller.isSegmentFixed(segmentID))
		_actionSetFixed = new ActionFixSegment(segmentID, controller, true);

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;

			_segment.type = newType;
			viewer.updateSegment(_segment);
			controller.forceUpdateReadingOrder(true); //TODO try not to force to update all for changing type
			if (_actionReadingOrder)
				_actionReadingOrder.execute();
			if (_actionSetFixed)
				_actionSetFixed.execute();
			console.log('Do - Change Type: {id:"' + segmentID + '",[..],type:"' + _oldType + '->' + newType + '"}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;

			_segment.type = _oldType;
			viewer.updateSegment(_segment);
			controller.forceUpdateReadingOrder(true);
			if (_actionReadingOrder)
				_actionReadingOrder.undo();
			if (_actionSetFixed)
				_actionSetFixed.undo();
			console.log('Undo - Change Type: {id:"' + segmentID + '",[..],type:"' + _oldType + '->' + newType + '"}');
		}
	}
}

function ActionAddRegion(id, points, type, editor, settings, page, controller) {
	let _isExecuted = false;
	const _region = { id: id, points: points, type: type, isRelative: true };

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;
			settings.regions[type].polygons[_region.id] = _region;
			editor.addRegion(_region);
			if (controller != null)
				controller.hideRegion(_region.type, false);

			console.log('Do - Add Region Polygon: {id:"' + _region.id + '",[..],type:"' + _region.type + '"}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;
			delete settings.regions[type].polygons[_region.id];
			editor.removeRegion(_region.id);
			console.log('Undo - Add Region Polygon: {id:"' + _region.id + '",type:"' + _region.type + '"}');
		}
	}
}

function ActionRemoveRegion(regionPolygon, editor, settings, page, controller) {
	let _isExecuted = false;
	const _region = regionPolygon;

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;

			delete settings.regions[_region.type].polygons[_region.id];
			editor.removeRegion(_region.id);
			console.log('Do - Remove Region Polygon: {id:"' + _region.id + '",[..],type:"' + _region.type + '"}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;
			settings.regions[_region.type].polygons[_region.id] = _region;
			editor.addRegion(_region);
			if (controller != null) {
				controller.hideRegion(_region.type, false);
			}
			console.log('Undo - Remove Region Polygon: {id:"' + _region.id + '",[..],type:"' + _region.type + '"}');
		}
	}
}

function ActionRemoveCompleteRegion(regionType, controller, editor, settings, controller) {
	let _isExecuted = false;
	const _region = JSON.parse(JSON.stringify(settings.regions[regionType]));

	//TODO redo after undo does not work
	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;
			let region = settings.regions[_region.type];
			controller.removePresentRegions(_region.type);

			// Iterate over all Polygons in Region
			Object.keys(_region.polygons).forEach(function (polygonKey) {
				editor.removeRegion(polygonKey);
			});

			delete settings.regions[_region.type];

			console.log('Do - Remove Region "' + _region.type + '"}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;
			settings.regions[_region.type] = JSON.parse(JSON.stringify(_region));
			controller.addPresentRegions(_region.type);

			// Iterate over all Polygons in Region
			Object.keys(_region.polygons).forEach(function (polygonKey) {
				editor.addRegion(_region.polygons[polygonKey]);
				controller.hideRegion(_region.type, false);
			});

			console.log('Undo - Remove Region "' + _region.type + '"}');
		}
	}
}

function ActionAddSegment(id, points, type, editor, segmentation, page, controller) {
	let _isExecuted = false;
	const _segment = { id: id, points: points, type: type, isRelative: false };
	const _actionSetFixed = new ActionFixSegment(id, controller, true);

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;
			segmentation[page].segments[_segment.id] = _segment;
			editor.addSegment(_segment, false);
			_actionSetFixed.execute();
			console.log('Do - Add Region Polygon: {id:"' + _segment.id + '",[..],type:"' + _segment.type + '"}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;
			_actionSetFixed.undo();
			delete segmentation[page].segments[_segment.id];
			editor.removeSegment(_segment.id);
			console.log('Undo - Add Region Polygon: {id:"' + _segment.id + '",[..],type:"' + _segment.type + '"}');
		}
	}
}

function ActionRemoveSegment(segment, editor, segmentation, page, controller) {
	let _isExecuted = false;
	const _segment = JSON.parse(JSON.stringify(segment));
	const _actionRemoveFromReadingOrder = new ActionRemoveFromReadingOrder(segment.id, page, segmentation, controller);
	let _actionSetFixed = null;
	if (controller.isSegmentFixed(segment.id))
		_actionSetFixed = new ActionFixSegment(segment.id, controller, true);

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;
			if (_actionSetFixed)
				_actionSetFixed.undo();
			delete segmentation[page].segments[_segment.id];
			editor.removeSegment(_segment.id);
			controller.unSelect([_segment.id]);

			_actionRemoveFromReadingOrder.execute();
			console.log('Do - Remove: {id:"' + _segment.id + '",[..],type:"' + _segment.type + '"}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;
			segmentation[page].segments[_segment.id] = JSON.parse(JSON.stringify(_segment));
			editor.addSegment(_segment);
			_actionRemoveFromReadingOrder.undo();
			if (_actionSetFixed)
				_actionSetFixed.execute();
			console.log('Undo - Remove: {id:"' + _segment.id + '",[..],type:"' + _segment.type + '"}');
		}
	}
}

function ActionAddCut(id, points, editor, settings, page) {
	let _isExecuted = false;
	const _cut = { id: id, points: points, type: 'other', isRelative: false };

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;
			settings.pages[page].cuts[_cut.id] = _cut;
			editor.addLine(_cut);
			console.log('Do - Add Cut: {id:"' + _cut.id + '",[..]}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;
			delete settings.pages[page].cuts[_cut.id];
			editor.removeLine(_cut.id);
			console.log('Undo - Add Cut: {id:"' + _cut.id + '",[..]}');
		}
	}
}

function ActionRemoveCut(cut, editor, settings, page) {
	let _isExecuted = false;
	const _cut = JSON.parse(JSON.stringify(cut));

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;

			delete settings.pages[page].cuts[_cut.id];
			editor.removeSegment(_cut.id);
			console.log('Do - Remove Cut: {id:"' + _cut.id + '",[..],type:"' + _cut.type + '"}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;

			settings.pages[page].cuts[_cut.id] = JSON.parse(JSON.stringify(_cut));
			editor.addLine(_cut);
			console.log('Undo - Remove Cut: {id:"' + _cut.id + '",[..],type:"' + _cut.type + '"}');
		}
	}
}

function ActionTransformRegion(id, regionPolygon, regionType, viewer, settings, page, controller) {
	let _isExecuted = false;
	const _id = id;
	const _regionType = regionType;
	const _newRegionPoints = JSON.parse(JSON.stringify(regionPolygon));
	const _oldRegionPoints = JSON.parse(JSON.stringify(settings.regions[_regionType].polygons[_id].points));

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;
			let region = settings.regions[_regionType].polygons[_id];
			region.points = _newRegionPoints;
			viewer.updateSegment(region);
			if (controller != null) {
				controller.hideRegion(_regionType, false);
			}
			console.log('Do - Transform Region: {id:"' + _id + ' [..]}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;
			let region = settings.regions[_regionType].polygons[_id];
			region.points = _oldRegionPoints;
			viewer.updateSegment(region);
			if (controller != null) {
				controller.hideRegion(_regionType, false);
			}
			console.log('Undo - Transform Region: {id:"' + _id + ' [..]}');
		}
	}
}

function ActionTransformSegment(id, segmentPoints, viewer, segmentation, page, controller) {
	let _isExecuted = false;
	const _id = id;
	const _newRegionPoints = JSON.parse(JSON.stringify(segmentPoints));
	const _oldRegionPoints = JSON.parse(JSON.stringify(segmentation[page].segments[_id].points));
	let _actionSetFixed = null;
	if (!controller.isSegmentFixed(id))
		_actionSetFixed = new ActionFixSegment(id, controller, true);

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;
			let segment = segmentation[page].segments[_id];
			segment.points = _newRegionPoints;
			viewer.updateSegment(segment);
			if (_actionSetFixed)
				_actionSetFixed.execute();
			console.log('Do - Transform Segment: {id:"' + _id + ' [..]}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;
			let segment = segmentation[page].segments[_id];
			segment.points = _oldRegionPoints;
			if (_actionSetFixed)
				_actionSetFixed.undo();
			viewer.updateSegment(segment);
			console.log('Undo - Transform Segment: {id:"' + _id + ' [..]}');
		}
	}
}

function ActionChangeReadingOrder(oldReadingOrder, newReadingOrder, controller, segmentation, page) {
	let _isExecuted = false;
	const _oldReadingOrder = JSON.parse(JSON.stringify(oldReadingOrder));
	const _newReadingOrder = JSON.parse(JSON.stringify(newReadingOrder));

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;

			segmentation[page].readingOrder = JSON.parse(JSON.stringify(_newReadingOrder));
			controller.forceUpdateReadingOrder(true);

			console.log('Do - Change Reading order');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;

			segmentation[page].readingOrder = JSON.parse(JSON.stringify(_oldReadingOrder));
			controller.forceUpdateReadingOrder(true);

			console.log('Undo - Change Reading order');
		}
	}
}

function ActionRemoveFromReadingOrder(segmentID, page, segmentation, controller) {
	let _isExecuted = false;
	const _oldReadingOrder = JSON.parse(JSON.stringify(segmentation[page].readingOrder));
	let _newReadingOrder;

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;

			if (!_newReadingOrder) {
				_newReadingOrder = JSON.parse(JSON.stringify(_oldReadingOrder));
				for (let index = 0; index < _newReadingOrder.length; index++) {
					if (_newReadingOrder[index] === segmentID) {
						_newReadingOrder.splice(index, 1);
						break;
					}
				}
			}

			segmentation[page].readingOrder = JSON.parse(JSON.stringify(_newReadingOrder));
			controller.forceUpdateReadingOrder(true);
			console.log('Do - Remove from Reading Order: {id:"' + segmentID + '",[..]}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;

			segmentation[page].readingOrder = JSON.parse(JSON.stringify(_oldReadingOrder));
			controller.forceUpdateReadingOrder(true);
			console.log('Undo - Remove from Reading Order: {id:"' + segmentID + '",[..]}');
		}
	}
}

function ActionAddToReadingOrder(segment, page, segmentation, controller) {
	let _isExecuted = false;
	const _oldReadingOrder = JSON.parse(JSON.stringify(segmentation[page].readingOrder));
	let _newReadingOrder;

	this.execute = function () {
		if (!_isExecuted && segment.type !== 'image') {
			_isExecuted = true;

			if (!_newReadingOrder) {
				_newReadingOrder = JSON.parse(JSON.stringify(_oldReadingOrder));
				_newReadingOrder.push(segment.id);
			}

			segmentation[page].readingOrder = JSON.parse(JSON.stringify(_newReadingOrder));
			controller.forceUpdateReadingOrder(true);
			console.log('Do - Add to Reading Order: {id:"' + segment.id + '",[..],type:"' + segment.type + '"}');
		}
	}
	this.undo = function () {
		if (_isExecuted && segment.type !== 'image') {
			_isExecuted = false;

			segmentation[page].readingOrder = JSON.parse(JSON.stringify(_oldReadingOrder));
			controller.forceUpdateReadingOrder(true);
			console.log('Undo - Add to Reading Order: {id:"' + segment.id + '",[..],type:"' + segment.type + '"}');
		}
	}
}
function ActionFixSegment(segmentID, controller, doFix = true) {
	let _isExecuted = false;

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;
			controller.fixSegment(segmentID, doFix);
			console.log('Do - Fix Segment ' + segmentID);
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;

			controller.fixSegment(segmentID, !doFix);
			console.log('Undo - Fix Segment ' + segmentID);
		}
	}
}