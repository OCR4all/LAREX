function ActionController(controller) {
	const _controller = controller;
	const _this = this;
	let selector;
	let _actions = {};
	let _actionpointers = {};

	this.redo = function (page) {
		_controller.setChanged(page);
		let pageActions = _actions[page];
		let pageActionpointer = _actionpointers[page];
		if (pageActions && pageActionpointer < pageActions.length - 1) {
			if(selector){
				selector.unSelect();
			}
			_actionpointers[page]++;
			pageActions[_actionpointers[page]].execute();
		}
	}
	this.undo = function (page) {
		_controller.setChanged(page);
		let pageActions = _actions[page];
		let pageActionpointer = _actionpointers[page];
		if (pageActions && pageActionpointer >= 0) {
			if(selector){
				selector.unSelect();
			}
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

	this.hasActions = function(page) {
		return _actions[page] && _actions[page].length > 0;
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
			for (let i = actions.length-1; i >= 0; i--) {
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

function ActionChangeTypeSegment(id, newType, viewer, controller, segmentation, page) {
	let _isExecuted = false;
	let _segment = segmentation[page].segments[id];
	const _oldType = _segment.type;
	let _actionReadingOrder = null;
	if (newType === 'ImageRegion' && segmentation[page].readingOrder && segmentation[page].readingOrder.includes(id)){
		_actionReadingOrder = new ActionRemoveFromReadingOrder(id, page, segmentation, controller);
	}
	let _actionSetFixed = null;
	if (!controller.isSegmentFixed(id))
		_actionSetFixed = new ActionFixSegment(id, controller, true);

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;

			_segment.type = newType;
			viewer.updateSegment(_segment);
			if(segmentation[page].readingOrder.includes(id))
				controller.forceUpdateReadingOrder(true); 
			if (_actionReadingOrder)
				_actionReadingOrder.execute();
			if (_actionSetFixed)
				_actionSetFixed.execute();
			console.log('Do - Change Type: {id:"' + id + '",[..],type:"' + _oldType + '->' + newType + '"}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;

			_segment.type = _oldType;
			viewer.updateSegment(_segment);
			if(segmentation[page].readingOrder.includes(id))
				controller.forceUpdateReadingOrder(true);
			if (_actionReadingOrder)
				_actionReadingOrder.undo();
			if (_actionSetFixed)
				_actionSetFixed.undo();
			console.log('Undo - Change Type: {id:"' + id + '",[..],type:"' + _oldType + '->' + newType + '"}');
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

	// TODO remove region segments?
	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;
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

function ActionRemoveSegment(segment, editor, textViewer, segmentation, page, controller, selector, doForceUpdate) {
	let _isExecuted = false;
	const _segment = JSON.parse(JSON.stringify(segment));
	let _actionRemoveFromReadingOrder = null;
	if(segmentation[page].readingOrder && segmentation[page].readingOrder.includes(_segment.id)){
		_actionRemoveFromReadingOrder = new ActionRemoveFromReadingOrder(segment.id, page, segmentation, controller, doForceUpdate);
	}
	const _actionRemoveTextLines = [];
	if(_segment.textlines != null){
		const ids = Object.keys(_segment.textlines);
		for(const [index,id] of ids.entries()){
			_actionRemoveTextLines.push(new ActionRemoveTextLine(_segment.textlines[id], editor, textViewer, segmentation,
				page, controller, selector,(index === ids.length-1 || index==0)));
		}	
	}
	const multiRemove = new ActionMultiple(_actionRemoveTextLines);
	let _actionSetFixed = null;
	if (controller.isSegmentFixed(segment.id))
		_actionSetFixed = new ActionFixSegment(segment.id, controller, true);

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;
			if (_actionSetFixed)
				_actionSetFixed.undo();

			multiRemove.execute();

			delete segmentation[page].segments[_segment.id];

			if(_actionRemoveFromReadingOrder)
				_actionRemoveFromReadingOrder.execute();

			const mode = controller.getMode();
			if(mode == Mode.EDIT || mode == Mode.SEGMENT)
				controller.forceUpdateReadingOrder(doForceUpdate);
			

			editor.removeSegment(_segment.id);
			selector.unSelectSegment(_segment.id);

			console.log('Do - Remove: {id:"' + _segment.id + '",[..],type:"' + _segment.type + '"}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;
			segmentation[page].segments[_segment.id] = JSON.parse(JSON.stringify(_segment));
			editor.addSegment(_segment);

			if(_actionRemoveFromReadingOrder)
				_actionRemoveFromReadingOrder.undo();

			if (_actionSetFixed)
				_actionSetFixed.execute();

			multiRemove.undo();
			console.log('Undo - Remove: {id:"' + _segment.id + '",[..],type:"' + _segment.type + '"}');
		}
	}
}

function ActionAddTextLine(id, segmentID, points, text, editor, textViewer, segmentation, page, controller) {
	let _isExecuted = false;
	const _textLine = { id: id, points: points,type:"TextLine", text: text, isRelative: false };
	let _oldTextLines = (segmentation[page].segments[segmentID].textlines) ? 
			JSON.parse(JSON.stringify(segmentation[page].segments[segmentID].textlines)) : {}

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;
			if(!segmentation[page].segments[segmentID].textlines){
				segmentation[page].segments[segmentID].textlines = {[id]: JSON.parse(JSON.stringify(_textLine))};
			}else{
				segmentation[page].segments[segmentID].textlines[id] = JSON.parse(JSON.stringify(_textLine));
			}
			editor.addTextLine(_textLine);
			textViewer.addTextline(_textLine);
			controller.textlineRegister[_textLine.id] = segmentID;
			console.log('Do - Add TextLine Polygon: {id:"' + _textLine.id + '",[..],text:"' + text + '"}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;
			segmentation[page].segments[segmentID].textlines = JSON.parse(JSON.stringify(_oldTextLines));
			editor.removeSegment(id);
			textViewer.removeTextline(id);
			delete controller.textlineRegister[_textLine.id]
			console.log('Undo - Add TextLine Polygon: {id:"' + _textLine.id + '",[..],text:"' + text + '"}');
		}
	}
}

function ActionRemoveTextLine(textline, editor, textViewer, segmentation, page, controller, selector, doForceUpdate=true) {
	let _isExecuted = false;
	const _segmentID = controller.textlineRegister[textline.id];
	const _oldTextLine = JSON.parse(JSON.stringify(textline));
	const _oldTextLines = JSON.parse(JSON.stringify(segmentation[page].segments[_segmentID].textlines));
	let removeROAction = null;
	if(segmentation[page].segments[_segmentID].readingOrder &&
		segmentation[page].segments[_segmentID].readingOrder.includes(textline.id)){
		removeROAction = new ActionRemoveTextLineFromReadingOrder(textline.id, _segmentID, page, segmentation, controller, selector, doForceUpdate);
	}

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;
			delete segmentation[page].segments[_segmentID].textlines[textline.id];
			editor.removeSegment(textline.id);
			textViewer.removeTextline(textline.id);
			selector.unSelectSegment(textline.id);
			if(removeROAction) removeROAction.execute();

			delete controller.textlineRegister[textline.id];
			console.log('Do - Remove: {id:"' + textline.id + '"}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;
			segmentation[page].segments[_segmentID].textlines = JSON.parse(JSON.stringify(_oldTextLines));
			editor.addTextLine(JSON.parse(JSON.stringify(_oldTextLine)));
			textViewer.addTextline(JSON.parse(JSON.stringify(_oldTextLine)));
			if(removeROAction) removeROAction.undo();

			controller.textlineRegister[textline.id] = _segmentID;
			console.log('Undo - Remove: {id:"' + textline.id + '"}');
		}
	}
}

function ActionAddCut(id, points, editor, fixedGeometry, page) {
	let _isExecuted = false;
	const _cut = { id: id, points: points, type: 'other', isRelative: false };
	if(!fixedGeometry[page]){
		fixedGeometry[page] = {};
	}

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;
			fixedGeometry[page].cuts[_cut.id] = _cut;
			editor.addLine(_cut);
			console.log('Do - Add Cut: {id:"' + _cut.id + '",[..]}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;
			delete fixedGeometry[page].cuts[_cut.id];
			editor.removeLine(_cut.id);
			console.log('Undo - Add Cut: {id:"' + _cut.id + '",[..]}');
		}
	}
}

function ActionRemoveCut(cut, editor, fixedGeometry, page) {
	let _isExecuted = false;
	const _cut = JSON.parse(JSON.stringify(cut));
	if(!fixedGeometry[page]){
		fixedGeometry[page] = {};
	}

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;

			delete fixedGeometry[page].cuts[_cut.id];
			editor.removeSegment(_cut.id);
			console.log('Do - Remove Cut: {id:"' + _cut.id + '",[..],type:"' + _cut.type + '"}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;

			fixedGeometry[page].cuts[_cut.id] = JSON.parse(JSON.stringify(_cut));
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
	const _orientation = segmentation[page].segments[_id].orientation;
	let _actionSetFixed = null;
	if (!controller.isSegmentFixed(id))
		_actionSetFixed = new ActionFixSegment(id, controller, true);

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;
			let segment = segmentation[page].segments[_id];
			segment.points = _newRegionPoints;
			segment.orientation = null;
			viewer.updateSegment(segment);
			if (_actionSetFixed)
				_actionSetFixed.execute();
			controller.forceUpdateReadingOrder();
			console.log('Do - Transform Segment: {id:"' + _id + ' [..]}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;
			let segment = segmentation[page].segments[_id];
			segment.points = _oldRegionPoints;
			segment.orientation = _orientation;
			if (_actionSetFixed)
				_actionSetFixed.undo();
			viewer.updateSegment(segment);
			controller.forceUpdateReadingOrder();
			console.log('Undo - Transform Segment: {id:"' + _id + ' [..]}');
		}
	}
}

function ActionTransformTextLine(id, segmentPoints, viewer, textViewer, segmentation, page, controller) {
	let _isExecuted = false;
	const _id = id;
	const _newRegionPoints = clone(segmentPoints);
	const _oldRegionPoints = clone(segmentation[page].segments[controller.textlineRegister[id]].textlines[_id].points);
	const _oldMinArea = clone(segmentation[page].segments[controller.textlineRegister[id]].textlines[_id].minArea);
	const _orientation = segmentation[page].segments[controller.textlineRegister[id]].textlines[_id].orientation;

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;
			let segment = segmentation[page].segments[controller.textlineRegister[id]].textlines[_id];
			segment.points = _newRegionPoints;
			segment.orientation = null;
			delete segment.minArea;
			viewer.updateSegment(segment);
			textViewer.updateTextline(segment);
			controller.forceUpdateReadingOrder();
			console.log('Do - Transform TextLine: {id:"' + _id + ' [..]}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;
			let segment = segmentation[page].segments[controller.textlineRegister[id]].textlines[_id];
			segment.points = _oldRegionPoints;
			segment.minArea = clone(_oldMinArea);
			segment.orientation = _orientation;
			viewer.updateSegment(segment);
			textViewer.updateTextline(segment);
			controller.forceUpdateReadingOrder();
			console.log('Undo - Transform TextLine: {id:"' + _id + ' [..]}');
		}
	}
}

function ActionChangeTextLineText(id, content, textViewer, gui, segmentation, page, controller) {
	let _isExecuted = false;
	const _id = id;
	const _oldContent = JSON.parse(JSON.stringify(segmentation[page].segments[controller.textlineRegister[id]].textlines[_id].text));
	if (!controller.isSegmentFixed(id))
		_actionSetFixed = new ActionFixSegment(id, controller, true);

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;

			const textline = segmentation[page].segments[controller.textlineRegister[id]].textlines[id];
			textline.text[0] = content;
			controller.updateTextLine(id);
			textViewer.updateTextline(textline);
			gui.saveTextLine(id);
			textViewer.saveTextLine(id);
			if(textViewer.isOpen()){
				controller.selectElement(id);
			}
			console.log('Do - Change TextLine text: {id:"' + _id + ' [..]}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;
			const textline = segmentation[page].segments[controller.textlineRegister[id]].textlines[_id];
			textline.text = JSON.parse(JSON.stringify(_oldContent));
			controller.updateTextLine(id);
			textViewer.updateTextline(textline);
			if(textViewer.isOpen()){
				controller.selectElement(id);
			}
			console.log('Undo - Change TextLine text: {id:"' + _id + ' [..]}');
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

function ActionAddToReadingOrder(segment, page, segmentation, controller) {
	let _isExecuted = false;
	let _oldReadingOrder;
	let _newReadingOrder;

	this.execute = function () {
		if (!_isExecuted && segment.type !== 'ImageRegion') {
			_isExecuted = true;

			if (!_oldReadingOrder) {
				_oldReadingOrder = JSON.parse(JSON.stringify(segmentation[page].readingOrder));
			}

			if (!_newReadingOrder) {
				_newReadingOrder = JSON.parse(JSON.stringify(_oldReadingOrder));
				_newReadingOrder.push(segment.id);
			}

			segmentation[page].readingOrder = JSON.parse(JSON.stringify(_newReadingOrder));
			controller.selectElement(segment);
			controller.forceUpdateReadingOrder(true);
			console.log('Do - Add to Reading Order: {id:"' + segment.id + '",[..],type:"' + segment.type + '"}');
		}
	}
	this.undo = function () {
		if (_isExecuted && segment.type !== 'ImageRegion') {
			_isExecuted = false;

			segmentation[page].readingOrder = JSON.parse(JSON.stringify(_oldReadingOrder));
			controller.selectElement(segment);
			controller.forceUpdateReadingOrder(true);
			console.log('Undo - Add to Reading Order: {id:"' + segment.id + '",[..],type:"' + segment.type + '"}');
		}
	}
}

function ActionRemoveFromReadingOrder(id, page, segmentation, controller, doForceUpdate=true) {
	let _isExecuted = false;
	const _oldReadingOrder = JSON.parse(JSON.stringify(segmentation[page].readingOrder));
	let _newReadingOrder;

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;

			if (!_newReadingOrder) {
				_newReadingOrder = JSON.parse(JSON.stringify(_oldReadingOrder));
				const readingOrderIndex = _newReadingOrder.indexOf(id);
				if(readingOrderIndex > -1){
					_newReadingOrder.splice(readingOrderIndex, 1);
				}
			}

			if(!(JSON.stringify(_newReadingOrder) == JSON.stringify(_oldReadingOrder))){
				segmentation[page].readingOrder = JSON.parse(JSON.stringify(_newReadingOrder));
				const mode = controller.getMode();
				if(doForceUpdate && (mode == Mode.EDIT || mode == Mode.SEGMENT))
					controller.forceUpdateReadingOrder(true);
				console.log('Do - Remove from Reading Order: {id:"' + id + '",[..]}');
			}
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;

			if(!(JSON.stringify(_newReadingOrder) == JSON.stringify(_oldReadingOrder))){
				segmentation[page].readingOrder = JSON.parse(JSON.stringify(_oldReadingOrder));
				if(doForceUpdate)
					controller.forceUpdateReadingOrder(true);
				console.log('Undo - Remove from Reading Order: {id:"' + id + '",[..]}');
			}
		}
	}
}

function ActionChangeTextLineReadingOrder(oldReadingOrder, newReadingOrder, parentID, controller, segmentation, page, selector) {
	let _isExecuted = false;
	const _oldReadingOrder = JSON.parse(JSON.stringify(oldReadingOrder));
	const _newReadingOrder = JSON.parse(JSON.stringify(newReadingOrder));

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;

			segmentation[page].segments[parentID].readingOrder = JSON.parse(JSON.stringify(_newReadingOrder));

			if(!selector.isSegmentSelected(parentID))
				selector.select(parentID);

			controller.forceUpdateReadingOrder(true);

			console.log('Do - Change Reading order');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;

			segmentation[page].segments[parentID].readingOrder = JSON.parse(JSON.stringify(_oldReadingOrder));

			if(!selector.isSegmentSelected(parentID))
				selector.select(parentID);

			controller.forceUpdateReadingOrder(true);

			console.log('Undo - Change Reading order');
		}
	}
}

function ActionAddTextLineToReadingOrder(id, parentID, page, segmentation, controller, selector) {
	let _isExecuted = false;
	let _oldReadingOrder;
	let _newReadingOrder;

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;

			if (!_oldReadingOrder) {
				_oldReadingOrder = JSON.parse(JSON.stringify(segmentation[page].segments[parentID].readingOrder));
			}

			if (!_newReadingOrder) {
				_newReadingOrder = JSON.parse(JSON.stringify(_oldReadingOrder));
				_newReadingOrder.push(id);
			}

			segmentation[page].segments[parentID].readingOrder = JSON.parse(JSON.stringify(_newReadingOrder));

			if(!selector.isSegmentSelected(id))
				selector.select(id);

			controller.forceUpdateReadingOrder(true);
			console.log('Do - Add to Reading Order: {id:"' + id + '",[..]"}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;

			segmentation[page].segments[parentID].readingOrder = JSON.parse(JSON.stringify(_oldReadingOrder));

			if(!selector.isSegmentSelected(parentID))
				selector.select(parentID);

			controller.forceUpdateReadingOrder(true);
			console.log('Undo - Add to Reading Order: {id:"' + id + '",[..]}');
		}
	}
}

function ActionRemoveTextLineFromReadingOrder(id, parentID, page, segmentation, controller, selector, forceUpdate=true) {
	let _isExecuted = false;
	let _oldReadingOrder;
	let _newReadingOrder;

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;

			if (!_oldReadingOrder){
				_oldReadingOrder  = JSON.parse(JSON.stringify(segmentation[page].segments[parentID].readingOrder));
			}

			if (!_newReadingOrder) {
				_newReadingOrder = JSON.parse(JSON.stringify(_oldReadingOrder)).filter(i => i !== id);
			}

			segmentation[page].segments[parentID].readingOrder = JSON.parse(JSON.stringify(_newReadingOrder));

			if(!selector.isSegmentSelected(parentID))
				selector.select(parentID);

			if(forceUpdate)
				controller.forceUpdateReadingOrder(true);

			console.log('Do - Remove from Reading Order: {id:"' + id + '",[..]}');
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;

			segmentation[page].segments[parentID].readingOrder = JSON.parse(JSON.stringify(_oldReadingOrder));

			if(!selector.isSegmentSelected(parentID))
				selector.select(parentID);

			selector.isSegmentSelected(parentID);

			if(forceUpdate)
				controller.forceUpdateReadingOrder(true);
			console.log('Undo - Remove from Reading Order: {id:"' + id + '",[..]}');
		}
	}
}

function ActionFixSegment(id, controller, doFix = true) {
	let _isExecuted = false;

	this.execute = function () {
		if (!_isExecuted) {
			_isExecuted = true;
			controller.fixSegment(id, doFix);
			console.log('Do - Fix Segment ' + id);
		}
	}
	this.undo = function () {
		if (_isExecuted) {
			_isExecuted = false;

			controller.fixSegment(id, !doFix);
			console.log('Undo - Fix Segment ' + id);
		}
	}
}

function clone(object) {
	if(object){
		return JSON.parse(JSON.stringify(object));
	} else {
		return undefined;
	}
}