function GuiInput(navigationController, controller, gui) {
	const _navigationController = navigationController;
	const _controller = controller;
	const _gui = gui;
	let _draggedObject = null;

	$(window).click((event) => {
		//Cancel viewer actions, if outside of viewer or a menu icon
		const $target = $(event.target);
		if (!$target.is('body') && !$target.is('#viewer') && !$target.parents("#viewer").is("#viewer")
			&& !$target.is('.infocus') && !$target.parents(".infocus").is(".infocus")) {
			_controller.escape();
		}
	});

	// button registration
	$("#viewer").contextmenu(() => {
		_controller.openContextMenu(true);
		return false; //prevents default contextmenu
	});
	$('.doSegment').click(() => _controller.doSegmentation());
	$('.exportPageXML').click(() => _controller.exportPageXML());
	$('.downloadPageXML').click(() => _controller.downloadPageXML());
	$('.pageXMLVersion').click(function () {
		const version = $(this).data('version');
		_gui.setPageXMLVersion(version);
		$('#dropDownPageXML').dropdown('close');
		_controller.setPageXMLVersion(version);
	});
	$('#dropDownPageXMLCorner').click((event) => {
		event.stopPropagation();
		$('#dropDownPageXML').dropdown('open');
	});
	$('.saveSettingsXML').click(() => _controller.saveSettingsXML());
	$('.downloadSettingsXML').click(() => _controller.downloadSettingsXML());
	$('#upload-input:file').on('change', function () {
		const file = this.files[0];
		$(this).val("");
		if (file) {
			if (file.size < 1024 * 1024) {
				_controller.uploadSettings(file);
			} else {
				alert('max upload size is 1MB')
			}
		}
	});
	$('#upload-segmentation-input:file').on('change', function () {
		const file = this.files[0];
		$(this).val("");
		if (file) {
			if (file.size < 1024 * 1024) {
				_controller.uploadExistingSegmentation(file);
			} else {
				alert('max upload size is 1MB')
			}
		}
	});
	$('.reload').click(() => location.reload());
	$('.settings-image-mode').on('change', function () {
		if (this.value) {
			_controller.changeImageMode(this.value);
		}
	})
	$('.settings-combine-image').on('change', function () {
		const doCombine = $(this).find('input').prop('checked');
		if (doCombine !== undefined) {
			_controller.changeImageCombine(doCombine);
		}
	});
	$('.createRegionRectangle').click(() => _controller.createRectangle('region'));
	$('.setRegionOfInterest').click(() => _controller.createRectangle('roi'));

	$('.createIgnore').click(() => _controller.createRectangle('ignore'));
	$('.createRegionBorder').click(() => _controller.createBorder(false));
	$('.createSegmentPolygon').click(() => _controller.createPolygon(true));
	$('.createSegmentRectangle').click(() => _controller.createRectangle('segment'));
	$('.createSegmentBorder').click(() => _controller.createBorder(true));
	$('.createCut').click(() => _controller.createCut());

	$('.combineSelected').click(() => _controller.mergeSelectedSegments());
	$('.deleteSelected').click(() => _controller.deleteSelected());
	$('.fixSelected').click(() => _controller.fixSelected());
	$('.editContours').click(() => _controller.selectContours());

	$('.editMode').click(() => _controller.editLastSelected());

	$('.zoomin').click(() => _navigationController.zoomIn(0.1));
	$('.zoomout').click(() => _navigationController.zoomOut(0.1));
	$('.zoomfit').click(() => _navigationController.zoomFit());

	$('.moveright').click(() => _navigationController.move(10, 0));
	$('.moveleft').click(() => _navigationController.move(-10, 0));
	$('.movedown').click(() => _navigationController.move(0, 10));
	$('.moveup').click(() => _navigationController.move(0, -10));
	$('.movecenter').click(() => _navigationController.center());

	$('.movefree').click(() => {
		if (_gui.doMoveCanvas) {
			_gui.moveCanvas(false);
		} else {
			_gui.moveCanvas(true);
		}
	});

	$('.undo').click(() => _controller.undo());
	$('.redo').click(() => _controller.redo());

	$('.deleteEdit').click(() => {
		_controller.deleteSelected();
		//TODO redirect to controller
		$("#segmentedit").addClass("hide");
	});
	$('.closeEdit').click(() => {
		//TODO redirect to controller
		$("#segmentedit").addClass("hide");
	});

	$('.chagePage').click(function () { _controller.displayPage($(this).data("page")) });
	$('#selectTypes').on('change', function () { _controller.changeTypeSelected(this.value) });
	$('.regionlegend').click(function () {
		const $this = $(this);
		const $switchBox = $this.find('input');
		_controller.hideRegion($this.data('type'), !$switchBox.prop('checked'));
	});
	$('.regionlegendAll').click(function () {
		const $switchBox = $(this).find('input');
		const $allSwitchBoxes = $('.regionlegend').find('input');
		$allSwitchBoxes.prop('checked', $switchBox.prop('checked'))

		_controller.hideAllRegions(!$switchBox.prop('checked'));
	});
	$('.regionSettings, #regioneditorSelect .collection-item').click(function () { _controller.openRegionSettings($(this).data("type"), false) });
	$('.regionCreate').click(function () { _controller.openRegionSettings($(this).data("type"), true) });
	$('.regionCancel').click(() => _gui.closeRegionSettings());
	$('.regionDelete').click(() => {
		const regionType = $('#regioneditor').find('#regionType').text();
		_controller.deleteRegionSettings(regionType);
		_gui.closeRegionSettings();
	});
	$('.contextTypeOption').click(function () {
		const $this = $(this);
		const $contextmenu = $("#contextmenu");
		const doSelected = $contextmenu.data('doSelected');
		const regionType = $this.data('type');

		if (doSelected) {
			_controller.changeTypeSelected(regionType);
		} else {
			const polygonID = $contextmenu.data('polygonID');
			_controller.changeRegionType(polygonID, regionType);
		}
		_gui.closeContextMenu();
	});

	$('#regioneditorSave').click(() => {
		const $regioneditor = $('#regioneditor');

		const regionType = $regioneditor.find('#regionType').text();
		const minSize = $regioneditor.find('#regionMinSize').val();
		const maxOccurances = $regioneditor.find('#regionMaxOccurances').val();
		let colorID = $regioneditor.find('#regionColor').data('colorID');
		_controller.changeRegionSettings(regionType, minSize, maxOccurances);
		_controller.setRegionColor(regionType, colorID);
		_gui.closeRegionSettings();
	});
	$('#regioneditorCancel').click(() => _gui.closeRegionSettings());
	$('#regioneditor #regionType').click(() => {
		const $regioneditor = $('#regioneditor');
		$('#regioneditorSelect').removeClass('hide');
	});
	$('#regioneditor #regionColor').click(() => {
		const $regioneditorColorSelect = $('#regioneditorColorSelect');
		if ($regioneditorColorSelect.hasClass('hide')) {
			_gui.updateAvailableColors();
			$('#regioneditorColorSelect').removeClass('hide');
		} else {
			$('#regioneditorColorSelect').addClass('hide');
		}
	});
	$('.regioneditorColorSelectItem').click(function () {
		_gui.setRegionColor($(this).data('colorID'));
	});

	$('.collapsible-header').click(function () {
		if ($(this).is('#reading-order-header')) {
			_controller.displayReadingOrder(true);
		} else {
			_controller.displayReadingOrder(false);
		}
	});

	$('.settings-load-existing-xml').click(function () {
		const $this = $(this);
		const $switchBox = $this.find('input');
		_controller.allowToLoadExistingSegmentation($switchBox.prop('checked'));
	});

	$('.loadExistingSegmentation').click(() => _controller.loadExistingSegmentation());

	$('.autoGenerateReadingOrder').click(() => _controller.autoGenerateReadingOrder());

	$('.createReadingOrder').click(() => _controller.createReadingOrder());

	$('.saveReadingOrder').click(() => _controller.endCreateReadingOrder());

	$('#pageLegend > .pageIconTodo').click(function(event) { 
		$this = $(this);
		const isChecked = !$this.hasClass('checked');
		_gui.hideTodoPages(!isChecked); 
		if(isChecked) $this.addClass('checked'); else $this.removeClass('checked');
	});
	$('#pageLegend > .pageIconSession').click(function(event) {
		$this = $(this);
		const isChecked = !$this.hasClass('checked');
		_gui.hideSessionPages(!isChecked); 
		if(isChecked) $this.addClass('checked'); else $this.removeClass('checked');
	});
	$('#pageLegend > .pageIconServer').click(function(event) {
		$this = $(this);
		const isChecked = !$this.hasClass('checked');
		_gui.hideServerPages(!isChecked); 
		if(isChecked) $this.addClass('checked'); else $this.removeClass('checked');
	});
	$('#pageLegend > .pageIconUnsaved').click(function(event) {
		$this = $(this);
		const isChecked = !$this.hasClass('checked');
		_gui.hideUnsavedPages(!isChecked); 
		if(isChecked) $this.addClass('checked'); else $this.removeClass('checked');
	});

	this.addDynamicListeners = () => {
		let _hasBeenDropped = false;

		$('.reading-order-segment').mouseover(function () {
			const $this = $(this);
			const segmentID = $this.data('segmentid');
			_controller.enterSegment(segmentID);
		});

		$('.reading-order-segment').mouseleave(function () {
			const $this = $(this);
			const segmentID = $this.data('segmentid');
			_controller.leaveSegment(segmentID);
		});

		$('.reading-order-segment').on('dragstart', function (event) {
			const $this = $(this);
			_draggedObject = $this;
			_hasBeenDropped = false;
		});

		$('.reading-order-segment').on('dragover', (event) => false);

		$('.reading-order-segment').on('dragleave', function (event) {
			$(this).removeClass('dragedOver');
			event.preventDefault();
			return false;
		});

		$('.reading-order-segment').on('dragenter', function (event) {
			const $this = $(this);
			$this.addClass('dragedOver');
			if (_draggedObject) {
				_controller.setBeforeInReadingOrder(_draggedObject.data('segmentid'), $(event.target).data('segmentid'), false);
			}
			return true;
		});

		$('.reading-order-segment').on('drop', function (event) {
			const $this = $(this);
			$this.removeClass('dragedOver');
			if (_draggedObject) {
				_controller.setBeforeInReadingOrder(_draggedObject.data('segmentid'), $(event.target).data('segmentid'), true);
			}
			_hasBeenDropped = true;
		});

		$('.reading-order-segment').on('dragend', (event) => {
			if (!_hasBeenDropped) {
				_controller.forceUpdateReadingOrder();
			}
		});
		$('.delete-reading-order-segment').click(function () {
			const $this = $(this);
			const segmentID = $this.data('segmentid');
			_controller.removeFromReadingOrder(segmentID);
		});
	}
}
