function Communicator() {

	this.load = function() {
		// Deferred object for function status
		const status = $.Deferred();
		const dataloader = this;

		this.loadSegmentTypes().done(() => {
			dataloader.loadSegments().done(() => status.resolve());
		});
		return status;
	}

	this.loadBook = function(bookID,pageID) {
		// Deferred object for function status
		const status = $.Deferred();

		$.ajax({
			type : "POST",
			url : "book",
			dataType : "json",
			data : {
				bookid : bookID,
				pageid : pageID
			},
			beforeSend : () => console.log("Book load: start"),
			success : (data) => {
				console.log('Book load: successful');
				status.resolve(data);
			},
			error : (jqXHR, textStatus, errorThrown) => {
				console.log("Book load: failed" + textStatus);
				status.resolve();
			}
		});
		return status;
	}

	this.segmentBook = function(settings,page,allowLoadLocal) {
		// Deferred object for function status
		const status = $.Deferred();

		const segmentationRequest = {settings: settings,page:page,allowLoadLocal:allowLoadLocal}

		$.ajax({
			type : "POST",
			url : "segment",
			dataType : "json",
			contentType: "application/json",
			data : JSON.stringify(segmentationRequest)/*{
				settings : JSON.stringify(settings),
				pageid : pageID
			}*/,
			beforeSend : () => console.log("Segmentation load: start"),
			success : (data) => {
				console.log('Segmentation load: successful');
				status.resolve(data);
			},
			error : (jqXHR, textStatus, errorThrown) => {
				console.log("Segmentation load: failed " + textStatus);
				status.resolve();
			}
		});
		return status;
	}

	this.requestMergedSegment = function(segments,pageID,bookID) {
		// Deferred object for function status
		const status = $.Deferred();

		const mergeRequest = {segments : segments, pageid: pageID, bookid: bookID}

		$.ajax({
			type : "POST",
			url : "merge",
			dataType : "json",
			contentType: "application/json",
			data : JSON.stringify(mergeRequest),
			beforeSend : () => console.log("Merge load: start"),
			success : (data) => {
				console.log('Merge load: successful');
				status.resolve(data);
			},
			error : (jqXHR, textStatus, errorThrown) => {
				console.log("Merge load: failed" + textStatus);
				status.resolve();
			}
		});
		return status;
	}

	this.prepareExport = function(segmentation,bookID){
		const exportRequest = {bookid: bookID, segmentation: segmentation}
		// Deferred object for function status
		const status = $.Deferred();

		$.ajax({
			type : "POST",
			url : "prepareExport",
			contentType: "application/json",
			data : JSON.stringify(exportRequest),
			beforeSend : () => console.log("Prepare Export: start"),
			success : (data) => {
				console.log('Prepare Export: successful');
				status.resolve(data);
			},
			error : (jqXHR, textStatus, errorThrown) => {
				console.log("Prepare Export: end");//"Prepare Export: failed " + textStatus);
				status.resolve();
			}
		});
		return status;
	}

	this.prepareSettingsExport = function(settings){
		// Deferred object for function status
		const status = $.Deferred();

		const segmentationRequest = {settings: settings,page:0}

		$.ajax({
			type : "POST",
			url : "downloadSettings",
			contentType: "application/json",
			data : JSON.stringify(segmentationRequest),
			beforeSend : () => console.log("Prepare Export Settings: start"),
			success : (data) => {
				console.log('Prepare Export Settings: successful');
				status.resolve(data);
			},
			error : (jqXHR, textStatus, errorThrown) => {
				console.log("Prepare Export Settings: end");//"Prepare Export: failed " + textStatus);
				status.resolve();
			}
		});
		return status;
	}

	this.uploadSettings = function(file, bookID) {
		// Deferred object for function status
		const status = $.Deferred();
		const formData = new FormData();
		formData.append("file", file);
		formData.append("bookID", bookID)

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
			beforeSend : () => console.log("Settings upload: start"),
			success : (data) => {
				console.log('Settings upload: successful');
				status.resolve(data);
			},
			error : (jqXHR, textStatus, errorThrown) => {
				console.log("Settings upload: failed" + textStatus);
				status.resolve();
			}
		});
		return status;
	}

	this.uploadPageXML = function(file,pageNr,bookID) {
		// Deferred object for function status
		const status = $.Deferred();
		const formData = new FormData();
		formData.append("file", file);
		formData.append("pageNr", pageNr);
		formData.append("bookID", bookID);

		jQuery.ajax({
		    url: 'uploadSegmentation',
		    type: 'POST',
		    data: formData,
				dataType: 'json',
		    cache: false,
		    contentType: false,
		    processData: false,
		    success: (data) => alert(data),
			beforeSend : () => console.log("Segmentation upload: start"),
			success : (data) => {
				console.log('Segmentation upload: successful');
				status.resolve(data);
			},
			error : (jqXHR, textStatus, errorThrown) => {
				console.log("Segmentation upload: failed" + textStatus);
				status.resolve();
			}
		});
		return status;
	}
	this.loadSegmentTypes = function() {
		// Deferred object for function status
		const status = $.Deferred();
		$.ajax({
			dataType : "json",
			url : "SegmentTypes",
			beforeSend : () => console.log("SegmentTypes load: start")
		}).done((data) => {
			status.resolve();
			console.log("SegmentTypes load: successful");
		}).fail(() => console.log("SegmentTypes load: failed")
		).always(() => console.log("SegmentTypes load: complete"));

		return status;
	}

	this.loadSegments = function() {
		// Deferred object for function status
		const status = $.Deferred();

		$.ajax({
			dataType : 'json',
			url : "Segments",
			beforeSend : function() {
				console.log("Segmentation load: start");
			}
		}).done((data) => {
			status.resolve();
			console.log("Segmentation load: successful");
		}).fail(() => console.log("Segmentation load: failed")
		).always(() => console.log("Segmentation load: complete"));

		return status;
	}
	this.loadImage = function(image, id){
		// Deferred object for function status
		const status = $.Deferred();
		
		const img = $("<img />").attr('src', "images/books/"+image).on('load', () => {
			img.attr('id', id);
			$('body').append(img);
			status.resolve();
		});
		return status;
	}
}
