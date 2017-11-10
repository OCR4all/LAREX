function Communicator() {

	this.load = function() {
		// Deferred object for function status
		var status = $.Deferred();
		var dataloader = this;

		this.loadSegmentTypes().done(function() {
			dataloader.loadSegments().done(function() {
				status.resolve();
			});
		});
		return status;
	}

	this.loadBook = function(bookID,pageID) {
		// Deferred object for function status
		var status = $.Deferred();

		$.ajax({
			type : "POST",
			url : "book",
			dataType : "json",
			data : {
				bookid : bookID,
				pageid : pageID
			},
			beforeSend : function() {
				console.log("Book load: start");
			},
			success : function(data) {
				console.log('Book load: successful');
				status.resolve(data);
			},
			error : function(jqXHR, textStatus, errorThrown) {
				console.log("Book load: failed" + textStatus);
				status.resolve();
			}
		});
		return status;
	}

	this.segmentBook = function(settings,page,allowLoadLocal) {
		// Deferred object for function status
		var status = $.Deferred();

		var segmentationRequest = {settings: settings,page:page,allowLoadLocal:allowLoadLocal}

		$.ajax({
			type : "POST",
			url : "segment",
			dataType : "json",
			contentType: "application/json",
			data : JSON.stringify(segmentationRequest)/*{
				settings : JSON.stringify(settings),
				pageid : pageID
			}*/,
			beforeSend : function() {
				console.log("Segmentation load: start");
			},
			success : function(data) {
				console.log('Segmentation load: successful');
				status.resolve(data);
			},
			error : function(jqXHR, textStatus, errorThrown) {
				console.log("Segmentation load: failed " + textStatus);
				status.resolve();
			}
		});
		return status;
	}

	this.requestMergedSegment = function(segmentIDs,pageID) {
		// Deferred object for function status
		var status = $.Deferred();

		$.ajax({
			type : "POST",
			url : "merge",
			dataType : "json",
			data : {
				segmentids : segmentIDs,
				pageid : pageID
			},
			beforeSend : function() {
				console.log("Merge load: start");
			},
			success : function(data) {
				console.log('Merge load: successful');
				status.resolve(data);
			},
			error : function(jqXHR, textStatus, errorThrown) {
				console.log("Merge load: failed" + textStatus);
				status.resolve();
			}
		});
		return status;
	}

	this.prepareExport = function(pageID, exportSettings){
		// Deferred object for function status
		var status = $.Deferred();

		var readingOrder = [];
		for(var i = 0; i < exportSettings.readingOrder.length; i++){
			readingOrder.push(exportSettings.readingOrder[i].id);
		}

		var segmentationRequest = {	page: pageID,
									segmentsToIgnore:exportSettings.segmentsToIgnore,
									changedTypes:exportSettings.changedTypes,
									segmentsToMerge:exportSettings.segmentsToMerge,
									fixedRegions:exportSettings.fixedRegions,
									 readingOrder:readingOrder}

		$.ajax({
			type : "POST",
			url : "prepareExport",
			contentType: "application/json",
			data : JSON.stringify(segmentationRequest),
			beforeSend : function() {
				console.log("Prepare Export: start");
			},
			success : function(data) {
				console.log('Prepare Export: successful');
				status.resolve(data);
			},
			error : function(jqXHR, textStatus, errorThrown) {
				console.log("Prepare Export: end");//"Prepare Export: failed " + textStatus);
				status.resolve();
			}
		});
		return status;
	}

	this.prepareSettingsExport = function(settings){
		// Deferred object for function status
		var status = $.Deferred();

		var segmentationRequest = {settings: settings,pages:[]}

		$.ajax({
			type : "POST",
			url : "saveSettings",
			contentType: "application/json",
			data : JSON.stringify(segmentationRequest),
			beforeSend : function() {
				console.log("Prepare Export Settings: start");
			},
			success : function(data) {
				console.log('Prepare Export Settings: successful');
				status.resolve(data);
			},
			error : function(jqXHR, textStatus, errorThrown) {
				console.log("Prepare Export Settings: end");//"Prepare Export: failed " + textStatus);
				status.resolve();
			}
		});
		return status;
	}

	this.uploadSettings = function(file) {
		// Deferred object for function status
		var status = $.Deferred();
		var formData = new FormData();
		formData.append("file", file);

		jQuery.ajax({
		    url: 'uploadSettings',
		    type: 'POST',
		    data: formData,
				dataType: 'json',
		    cache: false,
		    contentType: false,
		    processData: false,
		    success: function(data){
		        alert(data);
		    },
			beforeSend : function() {
				console.log("Settings upload: start");
			},
			success : function(data) {
				console.log('Settings upload: successful');
				status.resolve(data);
			},
			error : function(jqXHR, textStatus, errorThrown) {
				console.log("Settings upload: failed" + textStatus);
				status.resolve();
			}
		});
		return status;
	}

	this.uploadPageXML = function(file,pageNr) {
		// Deferred object for function status
		var status = $.Deferred();
		var formData = new FormData();
		formData.append("file", file);
		formData.append("pageNr", pageNr);

		jQuery.ajax({
		    url: 'uploadSegmentation',
		    type: 'POST',
		    data: formData,
				dataType: 'json',
		    cache: false,
		    contentType: false,
		    processData: false,
		    success: function(data){
		        alert(data);
		    },
			beforeSend : function() {
				console.log("Segmentation upload: start");
			},
			success : function(data) {
				console.log('Segmentation upload: successful');
				status.resolve(data);
			},
			error : function(jqXHR, textStatus, errorThrown) {
				console.log("Segmentation upload: failed" + textStatus);
				status.resolve();
			}
		});
		return status;
	}
	this.loadSegmentTypes = function() {
		// Deferred object for function status
		var status = $.Deferred();
		$.ajax({
			dataType : "json",
			url : "SegmentTypes",
			beforeSend : function() {
				console.log("SegmentTypes load: start");
			}
		}).done(function(data) {
			//_state.segmenttypes = data;

			status.resolve();

			console.log("SegmentTypes load: successful");
		}).fail(function() {
			console.log("SegmentTypes load: failed");
		}).always(function() {
			console.log("SegmentTypes load: complete");
		});

		return status;
	}

	this.loadSegments = function() {
		// Deferred object for function status
		var status = $.Deferred();

		$.ajax({
			dataType : 'json',
			url : "Segments",
			beforeSend : function() {
				console.log("Segmentation load: start");
			}
		}).done(
				function(data) {
					var pictureSegmentation = data;

					pictureSegmentation.segments.forEach(function(segment) {
						//_state.addSegment(segment.id, JSON.stringify(segment.points), segment.type);
					});

					status.resolve();

					console.log("Segmentation load: successful");
				}).fail(function() {
			console.log("Segmentation load: failed");
		}).always(function() {
			console.log("Segmentation load: complete");
		});

		return status;
	}
	this.loadImage = function(image, id){
		// Deferred object for function status
		var status = $.Deferred();
		
		var img = $("<img />").attr('src', "images/books/"+image).on('load', function() {
			img.attr('id', id);
			$('body').append(img);
			status.resolve();
		});
		return status;
	}
}
