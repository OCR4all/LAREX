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

	getPresetVirtualKeyboard(language){
		return this.request("data/virtualkeyboardPreset", {language: language})
	}

	getPageAnnotations(bookid, pageid) {
		return this.request("data/page/annotations", {bookid:bookid, pageid:pageid});
	}

	getPageAnnotationsBatch(bookid, pages) {
		return this.request("data/page/batchAnnotations", {bookid:bookid, pages:pages}, DataType.JSON);
	}

	getHaveAnnotations(bookID) {
		return this.request("data/status/all/annotations", {bookid:bookID});
	}

	// Segmentation
	segmentPage(settings, page) {
		return this.request("segmentation/segment", {settings:settings,page:page}, DataType.JSON);
	}

	batchSegmentPage(settings, pages, bookID, pageXMLVersion){
		return this.request("segmentation/batchSegment", {settings:settings, pages:pages,
			bookid:bookID, version:pageXMLVersion}, DataType.JSON)
	}

	batchExportPage(bookID, pages, segmentations, pageXMLVersion) {
		//using fetch instead of JQuery.ajax because of arrayBuffer type
		let url = 'file/export/batchExport';
		let ajaxdata = {bookid:bookID,pages:pages,segmentations:segmentations,version:pageXMLVersion,
			downloadPage:globalSettings.downloadPage};
		return fetch(url, {
			method: 'POST',
			headers: {'Content-Type': 'application/json; charset=utf-8'},
			body: JSON.stringify(ajaxdata)
		}).then(function (response) {
			return response.arrayBuffer();
		}).catch(function (err) {
			// There was an error
			console.warn('Something went wrong.', err);
		});
	}

	emptySegmentation(bookID, pageID) {
		return this.request("segmentation/empty",  {pageid:pageID,bookid:bookID});
	}

	getSettings(bookID) {
		return this.request("segmentation/settings", {bookid:bookID});
	}

	// Processing
	mergeSegments(segments) {
		return this.request("process/regions/merge", segments, DataType.JSON);
	}

	simplify(segments, tolerance) {
		return this.request("process/regions/simplify", {segments:JSON.stringify(segments), tolerance:tolerance});
	}

	extractContours(pageid, bookid) {
		return this.request("process/contours/extract", {pageid:pageid,bookid:bookid});
	}

	combineContours(contours, page_width, page_height, accuracy) {
		return this.request("process/contours/combine", {contours:contours,page_width:page_width,
			page_height:page_height, accuracy:accuracy}, DataType.JSON);
	}

	minAreaRect(segment) {
		return this.request("process/polygons/minarearect", {id:segment.id,points:segment.coords.points,
			isRelative:segment.coords.isRelative}, DataType.JSON);
	}

	// Files
	exportSegmentation(segmentation, bookID, pageXMLVersion) {
		return this.request("file/export/annotations", {bookid:bookID,segmentation:segmentation,
			version:pageXMLVersion}, DataType.JSON, DataType.BYTE);
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

		return this.request("file/upload/annotations", formData, DataType.SIMPLE);
	}

	loadImage(image_path, id) {
		// Deferred object for function status
		const status = $.Deferred();
		let pathEnc = encodeURIComponent(JSON.stringify(image_path.replace(/\//g, "‡")));
		const img = $("<img />").attr('src', "loadImage/" + pathEnc).on('load', () => {
			img.attr('id', id);
			$('body').append(img);
			status.resolve();
		});
		return status;
	}

	getOCR4allMode(){
		return this.request("config/ocr4all", {}, DataType.JSON);
	}

	getDirectRequestMode(){
		return this.request("config/directrequest", {}, DataType.JSON);
	}

	getBatchSegmentationProgress(){
		return $.ajax({
			type: "GET",
			url: "segmentation/batchSegmentProgress",
			cache: false,
		});
	}

	getLibraryBookPages(book_id, bookpath, booktype) {
		return this.request("library/getPageLocations", {bookid:book_id,bookpath:bookpath,booktype:booktype}, DataType.SIMPLE)
	}

	getMetsData(mets_path) {
		return this.request("library/getMetsData", {metspath : mets_path}, DataType.SIMPLE)
	}
	getOldRequestData() {
		return this.request("library/getOldRequest")
	}
	getBatchExportProgress(){
		return $.ajax({
			type: "GET",
			url: "file/export/batchExportProgress",
			cache: false,
		});
	}
}
