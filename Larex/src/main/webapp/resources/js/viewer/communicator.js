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

	this.segmentBook = function(settings,pages) {
		// Deferred object for function status
		var status = $.Deferred();

		var segmentationRequest = {settings: settings,pages:pages}

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

		var segmentationRequest = {	page: pageID,
																segmentsToIgnore:exportSettings.segmentsToIgnore,
																changedTypes:exportSettings.changedTypes,
																segmentsToMerge:exportSettings.segmentsToMerge,
																fixedRegions:exportSettings.fixedRegions}

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

	this.debugConnection = function() {
				getTestQuerry().done(function(data){

				// Deferred object for function status
					var status = $.Deferred();

					$.ajax({
						type : "POST",
						url : "testConnection",
						dataType : "json",
						contentType: "application/json",
						//data : JSON.stringify({test1:'blub',test2:'blub2',test3:{}}),
						data : JSON.stringify(data),
						beforeSend : function() {
							console.log("Debug send: start");
						},
						success : function(data) {
							alert(JSON.stringify(data));
							console.log('Debug send: successful');
							status.resolve(data);
						},
						error : function(jqXHR, textStatus, errorThrown) {
							console.log("Debug send: failed " + textStatus);
							status.resolve();
						}
					});
					return status;

				});
	}

	var getTestQuerry = function() {
		// Deferred object for function status
		var status = $.Deferred();

		$.ajax({
			url : "getTestQuerry",
			dataType : "json",
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
}
