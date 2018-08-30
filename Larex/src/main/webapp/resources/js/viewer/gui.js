function GUI(canvas, viewer, colors) {
	const _viewer = viewer;
	const _colors = colors;
	let _canvas = canvas;
	let _doMoveCanvas = false;
	let _mouse;
	let _hiddenStyles = [];

	$(document).mousemove((event) => _mouse = { x: event.pageX, y: event.pageY });

	this.setCanvas = function (id) {
		_canvas = id;
	}

	this.updateZoom = function () {
		let zoom = _viewer.getZoom();
		zoom = Math.round(zoom * 10000) / 100;

		$('.zoomvalue').text(zoom);
	}

	this.openContextMenu = function (doSelected, id) {
		const $contextmenu = $("#contextmenu");
		$contextmenu.removeClass("hide");
		const fitsInWindow = _mouse.y + $contextmenu.height() < $(window).height();

		if (fitsInWindow) {
			$contextmenu.css({ top: _mouse.y - 5, left: _mouse.x - 5 });
		} else {
			$contextmenu.css({ top: _mouse.y + 5 - $contextmenu.height(), left: _mouse.x - 5 });
		}
		$contextmenu.data('doSelected', doSelected);
		$contextmenu.data('polygonID', id);
	}

	this.closeContextMenu = function () {
		$("#contextmenu").addClass("hide");
	}

	this.resizeViewerHeight = function () {
		const $canvas = $("#" + _canvas);
		const $sidebars = $('.sidebar');
		const height = $(window).height() - $canvas.offset().top;

		$canvas.height(height);
		$sidebars.height(height);
		this.loadVisiblePreviewImages();
	}

	this.setParameters = function (parameters, imageMode, combineMode) {
		$("#binarythreash").val(parameters['binarythreash']);
		$("#textdilationX").val(parameters['textdilationX']);
		$("#textdilationY").val(parameters['textdilationY']);
		$("#imagedilationX").val(parameters['imagedilationX']);
		$("#imagedilationY").val(parameters['imagedilationY']);

		const $imageMode = $('.settings-image-mode');
		$imageMode.find('option').removeAttr('selected');
		$imageMode.find('option[value="' + imageMode + '"]').attr('selected', 'selected');
		//reinitialize dropdown
		$imageMode.find('select').material_select();

		$('.settings-combine-image').find('input').prop('checked', combineMode);
	}

	this.getParameters = function () {
		const parameters = {};
		parameters['binarythreash'] = $("#binarythreash").val();
		parameters['textdilationX'] = $("#textdilationX").val();
		parameters['textdilationY'] = $("#textdilationY").val();
		parameters['imagedilationX'] = $("#imagedilationX").val();
		parameters['imagedilationY'] = $("#imagedilationY").val();
		return parameters;
	}

	this.setRegionLegendColors = function (segmenttypes) {
		// Iterate over Segmenttype-"Map" (Object in JS)
		Object.keys(segmenttypes).forEach((key) => {
			const color = _colors.getColor(key);
			$(".legendicon." + key).css("background-color", color.toCSS());
		});
	}

	this.forceUpdateRegionHide = function (visibleRegions) {
		const $allSwitchBoxes = $('.regionlegend');
		const _visibleRegions = visibleRegions;
		$allSwitchBoxes.each(function () {
			const $this = $(this);
			const $switchBox = $($this.find('input'));
			const regionType = $this.data('type');

			if (_visibleRegions[regionType]) {
				$switchBox.prop('checked', true);
			} else {
				$switchBox.prop('checked', false);
			}
		});
	}
	this.showUsedRegionLegends = function (presentRegions) {
		$('.regionlegend,.contextregionlegend').each(function () {
			const $this = $(this);
			const legendType = $this.data('type');

			if ($.inArray(legendType, presentRegions) > -1) {
				$this.removeClass('hide');
			} else {
				$this.addClass('hide');
			}
		});
		$('.regioneditorSelectItem').each(function () {
			const $this = $(this);
			const legendType = $this.data('type');

			if ($.inArray(legendType, presentRegions) > -1) {
				$this.addClass('hide');
			} else {
				$this.removeClass('hide');
			}
		});
	}

	this.openRegionSettings = function (regionType, minSize, maxOccurances, priorityPosition, doCreate, regionColorID) {
		$('#regionType').text(regionType);
		$('#regionMinSize').val(minSize);
		$('#regionMaxOccurances').val(maxOccurances);
		if (doCreate != null && doCreate == true) {
			$('#regionType').addClass('hide');
			$('#regioneditorSelect').removeClass('hide');
			$('#regioneditorColorSelect').addClass('hide');
			$('.regionColorSettings').addClass('hide');
			$('.regionSetting').addClass('hide');
			$('#regioneditorSave').addClass('hide');
			$('.regionDelete').addClass('hide');
		} else {
			$('#regionType').removeClass('hide');
			$('#regioneditorSelect').addClass('hide');
			$('#regioneditorColorSelect').addClass('hide');
			$('.regionColorSettings').removeClass('hide');
			$('.regionSetting').removeClass('hide');
			$('#regioneditorSave').removeClass('hide');
			if (regionType != 'image' && regionType != 'paragraph') {
				$('.regionDelete').removeClass('hide');
			} else {
				$('.regionDelete').addClass('hide');
			}
			if (regionColorID) {
				this.setRegionColor(regionColorID);
			}
		}
		$settingsOffset = $('#sidebarRegions').offset();
		$regioneditor = $('#regioneditor');
		$regioneditor.removeClass('hide');
		$regioneditor.css({ top: $settingsOffset.top, left: $settingsOffset.left - $regioneditor.width() });
	}

	this.setRegionColor = function (colorID) {
		const $regioneditor = $('#regioneditor');
		const color = _colors.getPredefinedColor(colorID);
		$regioneditor.find('.regionColorIcon').css("background-color", color.toCSS());
		$regioneditor.find('#regionColor').data('colorID', colorID);
	}
	this.closeRegionSettings = function () {
		$('#regioneditor').addClass('hide');
	}

	this.displayReadingOrder = function (doDisplay) {
		if (doDisplay) {
			$('.readingOrderCategory').removeClass("hide");
		} else {
			$('.readingOrderCategory').addClass("hide");
		}
	}

	this.setReadingOrder = function (readingOrder, segments) {
		$readingOrderList = $('#reading-order-list');
		$readingOrderList.empty();
		for (let index = 0; index < readingOrder.length; index++) {
			const segment = segments[readingOrder[index]];
			if(segment){
				const $collectionItem = $('<li class="collection-item reading-order-segment" data-segmentID="' + segment.id + '" draggable="true"></li>');
				const $legendTypeIcon = $('<div class="legendicon ' + segment.type + '"></div>');
				const $deleteReadingOrderSegment = $('<i class="delete-reading-order-segment material-icons right" data-segmentID="' + segment.id + '">delete</i>');
				$collectionItem.append($legendTypeIcon);
				$collectionItem.append(segment.type + "-" + segment.id.substring(0, 4));
				$collectionItem.append($deleteReadingOrderSegment);
				$readingOrderList.append($collectionItem);
			}
		}
	}

	this.highlightSegment = function (segmentID, doHighlight) {
		if (doHighlight) {
			$(".reading-order-segment[data-segmentid='" + segmentID + "']").addClass('highlighted');
		} else {
			$(".reading-order-segment[data-segmentid='" + segmentID + "']").removeClass('highlighted');
		}
	}

	this.setBeforeInReadingOrder = function (segment1ID, segment2ID) {
		const $segment1 = $(".reading-order-segment[data-segmentid='" + segment1ID + "']");
		const $segment2 = $(".reading-order-segment[data-segmentid='" + segment2ID + "']");
		$($segment1).insertBefore($segment2);
	}

	this.forceUpdateReadingOrder = function (readingOrder, forceHard, segments) {
		if (forceHard) {
			this.setReadingOrder(readingOrder, segments);
		} else {
			$readingOrderListItems = $('#reading-order-list');

			for (let index = 0; index < readingOrder.length; index++) {
				$readingOrderListItems.append($(".reading-order-segment[data-segmentid='" + readingOrder[index] + "']"));
			}
		}
	}

	this.doEditReadingOrder = function (doEdit) {
		if (doEdit) {
			$('.createReadingOrder').addClass("hide");
			$('.saveReadingOrder').removeClass("hide");
		} else {
			$('.saveReadingOrder').addClass("hide");
			$('.createReadingOrder').removeClass("hide");
		}
	}

	this.selectToolBarButton = function (option, doSelect) {
		let $button = null;
		switch (option) {
			case 'regionRectangle':
				$button = $('.createRegionRectangle');
				break;
			case 'regionBorder':
				$button = $('.createRegionBorder');
				break;
			case 'segmentRectangle':
				$button = $('.createSegmentRectangle');
				break;
			case 'segmentPolygon':
				$button = $('.createSegmentPolygon');
				break;
			case 'cut':
				$button = $('.createCut');
				break;
			case 'roi':
				$button = $('.setRegionOfInterest');
				break;
			case 'ignore':
				$button = $('.createIgnore');
				break;
			case 'editPoints':
				$button = $('.editPoints');
				break;
			default:
				break;
		}
		if ($button) {
			if (doSelect) {
				$button.addClass('invert');
			} else {
				$button.removeClass('invert');
			}
		}
	}

	this.unselectAllToolBarButtons = function () {
		const $buttons = $('.menuIcon').not('.fixed');
		$buttons.removeClass('invert');
	}

	this.selectPage = function (page) {
		$('.pageImageContainer').removeClass('selected');
		$('.pageImageContainer[data-page~="' + page + '"]').addClass('selected');
		this.scrollToPage(page);
	}

	this.scrollToPage = function (page) {
		const $pagecontainer = $('#pagecontainer');

		//Stop any running animations on pagecontainer
		$pagecontainer.stop(true);
		//Start scroll animation
		$pagecontainer.animate({
			scrollTop: $('.pageImageContainer[data-page~="' + page + '"]').position().top - $pagecontainer.offset().top + $pagecontainer.scrollTop()
		}, 2000);
	}

	this.highlightSegmentedPages = function (segmentedPages) {
		$('.pageImageContainer').removeClass('segmented');
		segmentedPages.forEach((page) => {
			$('.pageImageContainer[data-page~="' + page + '"]').addClass('segmented');
		});

	}
	this.highlightPagesAsError = function (errorPages) {
		$('.pageIconError').addClass('hide');
		errorPages.forEach((page) => {
			const $errorPage = $('.pageImageContainer[data-page~="' + page + '"]');
			$errorPage.addClass('segmentError');
			$errorPage.find('.pageIconError').removeClass('hide');
		});
	}
	this.highlightSavedPage = function (savedPage) {
		const $savedPage = $('.pageImageContainer[data-page~="' + savedPage + '"]');
		$savedPage.addClass('saved');
		$savedPage.find(".pageIconSaved").removeClass('hide');
		if(_hiddenStyles.indexOf(".pageImageContainer.saved") !== -1)
			$savedPage.addClass('hide');
	}
	this.highlightExportedPage = function (exportedPage) {
		const $exportedPage = $('.pageImageContainer[data-page~="' + exportedPage + '"]');
		$exportedPage.addClass('exported');
		$exportedPage.find(".pageIconExported").removeClass('hide');
		if(_hiddenStyles.indexOf(".pageImageContainer.exported") !== -1)
			$savedPage.addClass('hide');
	}
	this.highlightLoadedPage = function (exportedPage, doHighlight = true) {
		const $loadedPage = $('.pageImageContainer[data-page~="' + exportedPage + '"]');
		if (doHighlight) {
			$loadedPage.addClass('loaded');
			$loadedPage.find(".pageIconLoaded").removeClass('hide');
		} else {
			$loadedPage.removeClass('loaded');
			$loadedPage.find(".pageIconLoaded").addClass('hide');
		}
	}
	this.hideSavedPages = function (doHide=true) {
		const indexOfStyle = _hiddenStyles.indexOf(".pageImageContainer.saved");
		if(indexOfStyle < 0 && doHide)
			_hiddenStyles.push(".pageImageContainer.saved");
		else if(indexOfStyle >= 0 && !doHide)
			_hiddenStyles.splice(indexOfStyle, 1);
		
		if(doHide)
			$('.pageImageContainer.saved').addClass('hide');
		else
			$('.pageImageContainer.saved').removeClass('hide');

	}

	this.hideExportedPages = function (doHide=true) {
		const indexOfStyle = _hiddenStyles.indexOf(".pageImageContainer.exported");
		if(indexOfStyle < 0 && doHide)
			_hiddenStyles.push(".pageImageContainer.exported");
		else if(indexOfStyle >= 0 && !doHide)
			_hiddenStyles.splice(indexOfStyle, 1);
		
		if(doHide)
			$('.pageImageContainer.export').addClass('hide');
		else
			$('.pageImageContainer.export').removeClass('hide');
	}

	this.setExportingInProgress = function (isInProgress) {
		if (isInProgress) {
			$('.exportPageXML').find('.progress').removeClass('hide');
		} else {
			$('.exportPageXML').find('.progress').addClass('hide');
		}
	}
	this.setPageXMLVersion = function (version) {
		$('#pageXMLVersion').text(version);
	}
	this.setSaveSettingsInProgress = function (isInProgress) {
		if (isInProgress) {
			$('.saveSettingsXML').find('.progress').removeClass('hide');
		} else {
			$('.saveSettingsXML').find('.progress').addClass('hide');
		}
	}
	this.setAllRegionColors = function () {
		const $collection = $('#regioneditorColorSelect .collection');
		_colors.getPredefinedColorIDs().forEach(id => {
			const color = _colors.getPredefinedColor(id);
			const $colorItem = $('<li class="collection-item regioneditorColorSelectItem color' + id + '"></li>');
			const $icon = $('<div class="legendicon" style="background-color:' + color.toCSS() + ';"></div>');
			$colorItem.data('colorID', id);
			$colorItem.append($icon);
			$collection.append($colorItem);
		});
	}
	this.updateAvailableColors = function () {
		$('.regioneditorColorSelectItem').addClass("hide");
		_colors.getAvailableColorIDs().forEach((id) => {
			$('.regioneditorColorSelectItem.color' + id).removeClass("hide");
		});
	}
	this.displayWarning = function (text) {
		Materialize.toast(text, 4000);
		console.warn(text);
	}

	this.addPreviewImageListener = function(){
		$('#pagecontainer').scroll(() => this.loadVisiblePreviewImages());
	}

	this.loadVisiblePreviewImages = function(){
		$previewImages = $('.emptyPreview');
		const pixelBuffer = 500;

		$previewImages.each(function(){
			const $p = $(this);
			const windowsHeight = $(window).height();
			if($p.offset().top < windowsHeight+pixelBuffer){
				const imageSrc = $p.data("image");
				const imageId = $p.data("page");
				const bookpath = $p.data("bookpath");

				const $image = $('<img class="pageImage" alt="'+imageSrc+'" title="'+imageSrc+'" src="'+bookpath+imageSrc+'?resize=true" id="'+imageId+'previewImage" />');
				const $status = $('<div class="pagestatus">'+
									'<i class="material-icons pagestatusIcon pageIconLoaded circle tooltipped hide"'+
										'data-position="bottom" data-delay="50" data-tooltip="This page has been loaded from an existing segmentation.">file_upload</i>'+
									'<i class="material-icons pagestatusIcon pageIconExported circle tooltipped hide"'+
										'data-position="bottom" data-delay="50" data-tooltip="This page has been exported.">file_download</i>'+
									'<i class="material-icons pagestatusIcon pageIconSaved circle tooltipped hide"'+
										'data-position="bottom" data-delay="50" data-tooltip="This page has been saved.">lock</i>'+
									'<i class="material-icons pagestatusIcon pageIconError circle tooltipped hide"'+
										'data-position="bottom" data-delay="50" data-tooltip="There has been an error with this page. Reload the webpage to reslove.">error</i>'+
								'</div>');
				$p.append($image);
				$p.append($status);
				$p.removeClass("emptyPreview");
				$image.on('load', () => $p.removeClass("emptyImage"));
			}
		});
		
	}
}
