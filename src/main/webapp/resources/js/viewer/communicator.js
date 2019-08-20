const DataType = {SIMPLE:0,JSON:1,BYTE:2};

class Communicator {
	request(url,data={},uploadDataType=DataType.SIMPLE,downloadDataType=DataType.JSON){
		// Deferred object for function status
		const status = $.Deferred();

		let request = {
			type: "POST",
			url: url,
			beforeSend: () => console.log('/'+url +' request : start'),
			success: (data) => {
				console.log('/'+url +' request: successful');
				status.resolve(data);
			},
			error: (jqXHR, textStatus, errorThrown) => {
				console.log('/'+url +' request: failed' + textStatus);
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
			
	loadBook(bookID) { 
		return this.request("book", {bookid:bookID});
	}

	segmentBook(settings, page, allowLoadLocal) {
		return this.request("segment", {settings:settings,page:page,allowLoadLocal:allowLoadLocal}, DataType.JSON);
	}

	emptySegmentation(bookID, pageID) {
		return this.request("emptysegment",  {pageid:pageID,bookid:bookID});
	}

	mergeSegments(segments) {
		return this.request("merge", segments, DataType.JSON);
	}

	extractContours(pageid, bookid) {
		return this.request("extractcontours", {pageid:pageid,bookid:bookid});
	}
	
	combineContours(contours, page_width, page_height, accuracy) {
		return this.request("combinecontours", {contours:contours,page_width:page_width,page_height:page_height,accuracy:accuracy}, DataType.JSON);
	}

	minAreaRect(segment) {
		return this.request("minarearect", {id:segment.id,points:segment.points,isRelative:segment.isRelative}, DataType.JSON);
	}

	exportSegmentation(segmentation, bookID, pageXMLVersion) {
		return this.request("exportXML", {bookid:bookID,segmentation:segmentation,version:pageXMLVersion}, DataType.JSON, DataType.BYTE);
	}

	exportSettings(settings) {
		return this.request("downloadSettings", settings, DataType.JSON, DataType.BYTE);
	}
	
	getSettings(bookID) { 
		return this.request("segmentation/settings", {bookid:bookID});
	}

	getSegmented(bookID) { 
		return this.request("segmentedpages", {bookid:bookID});
	}
	
	getVirtualKeyboard() { 
		return this.request("virtualkeyboard");
	}

	uploadSettings(file, bookID) {
		const formData = new FormData();
		formData.append("file", file);
		formData.append("bookID", bookID);

		return this.request("uploadSettings", formData, DataType.BYTE);
	}

	uploadPageXML(file, pageNr, bookID) {
		const formData = new FormData();
		formData.append("file", file);
		formData.append("pageNr", pageNr);
		formData.append("bookID", bookID);

		return this.request("uploadSegmentation", formData, DataType.BYTE);
	}

	loadImage(image, id) {
		// Deferred object for function status
		const status = $.Deferred();

		const img = $("<img />").attr('src', "images/books/" + image).on('load', () => {
			img.attr('id', id);
			$('body').append(img);
			status.resolve();
		});
		return status;
	}
}