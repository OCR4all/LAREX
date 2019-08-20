const DataType = {SIMPLE:0,JSON:1,BYTE:2};

class Communicator {
	request(url,data={},uploadDataType=DataType.SIMPLE,downloadDataType=DataType.JSON){
		// Deferred object for function status
		const status = $.Deferred();

		let request = {
			type: "POST",
			url: url,
			beforeSend: () => console.log(`request:/${url} - start`),
			success: (data) => {
				console.log(`request:/${url} - success`);
				status.resolve(data);
			},
			error: (jqXHR, textStatus, errorThrown) => {
				console.log(`request:/${url} - fail '${textStatus}'`);
				status.resolve();
			}
		}
		switch(uploadDataType){
			case DataType.SIMPLE:
				request.data = data;	
				break;
			case DataType.JSON:
				request.contentType = "application/json";
				request.data = JSON.stringify(data);
				break;
			case DataType.BYTE:
				request.data = data;
				request.cache = false;
				request.contentType = false;
				request.processData = false;
				break;
		}

		if(downloadDataType === DataType.JSON){
			request.dataType = "json";
		}

		$.ajax(request);

		return status;
	}
			
	// Data
	loadBook(bookID) { 
		return this.request("data/book", {bookid:bookID});
	}

	getVirtualKeyboard() { 
		return this.request("data/virtualkeyboard");
	}

	// Segmentation
	segmentBook(settings, page, allowLoadLocal) {
		return this.request("segmentation/segment", {settings:settings,page:page,allowLoadLocal:allowLoadLocal}, DataType.JSON);
	}

	emptySegmentation(bookID, pageID) {
		return this.request("segmentation/empty",  {pageid:pageID,bookid:bookID});
	}

	getSettings(bookID) { 
		return this.request("segmentation/settings", {bookid:bookID});
	}

	getSegmented(bookID) { 
		return this.request("segmentation/segmentedpages", {bookid:bookID});
	}
	
	// Processing
	mergeSegments(segments) {
		return this.request("process/regions/merge", segments, DataType.JSON);
	}

	extractContours(pageid, bookid) {
		return this.request("process/contours/extract", {pageid:pageid,bookid:bookid});
	}
	
	combineContours(contours, page_width, page_height, accuracy) {
		return this.request("process/contours/combine", {contours:contours,page_width:page_width,page_height:page_height,accuracy:accuracy}, DataType.JSON);
	}

	minAreaRect(segment) {
		return this.request("process/polygons/minarearect", {id:segment.id,points:segment.points,isRelative:segment.isRelative}, DataType.JSON);
	}

	// Files
	exportSegmentation(segmentation, bookID, pageXMLVersion) {
		return this.request("file/export/annotations", {bookid:bookID,segmentation:segmentation,version:pageXMLVersion}, DataType.JSON, DataType.BYTE);
	}

	exportSettings(settings) {
		return this.request("file/download/segmentsettings", settings, DataType.JSON, DataType.BYTE);
	}
	
	uploadSettings(file, bookID) {
		const formData = new FormData();
		formData.append("file", file);
		formData.append("bookID", bookID);

		return this.request("file/upload/segmentsettings", formData, DataType.BYTE);
	}

	uploadPageXML(file, pageNr, bookID) {
		const formData = new FormData();
		formData.append("file", file);
		formData.append("pageNr", pageNr);
		formData.append("bookID", bookID);

		return this.request("file/upload/annotations", formData, DataType.BYTE);
	}

	loadImage(image_path, id) {
		// Deferred object for function status
		const status = $.Deferred();

		const img = $("<img />").attr('src', "images/books/" + image_path).on('load', () => {
			img.attr('id', id);
			$('body').append(img);
			status.resolve();
		});
		return status;
	}
}