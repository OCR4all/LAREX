/* The viewer is a display for result segments, region segments and contours of any kind.
 * It can handle inputs by forwarding it to a input manager (ViewerInput)
 * All functionality about viewing elements in the viewer is handled here.
 * It does not handle editing these elements. */
class TextViewer {
	constructor(viewerInput) {
		this.root = $("#viewerText");
		this.container = $("#viewerTextContainer");
		this.thisInput = viewerInput;
		this.image;
		this._zoomImage = 1;
		this._zoomText = 1;
		this._baseImageSize = 35;
		this._baseFontSize = 20;
		this._dmp = new diff_match_patch();
	}

	/**
	 * Set the page image used for every textline
	 *
	 * @param {string} id
	 */
	setImage(id) {
		this.image = $(`#${id}`);
	}

	/**
	 * Display this viewer in the gui interface
	 *
	 * @param {boolean} doDisplay
	 */
	display(doDisplay){
		if(doDisplay){
			this.root.removeClass("hide");
			$(".zoom_second").removeClass("hide");
			$('.hideTextView').removeClass('hide');
			$('.displayTextView').addClass('hide');
		} else {
			this.root.addClass("hide");
			$(".zoom_second").addClass("hide");
			$('.hideTextView').addClass('hide');
			$('.displayTextView').removeClass('hide');
		}
	}

	/**
	 * Check if the textviewer is currently opened.
	 */
	isOpen(){
		return !this.root.hasClass("hide");
	}

	/**
	 * Clear the textViewer, by removing all existing text lines and reseting the image
	 */
	clear() {
		this.image = null;
		this.container.empty();
	}

	setLoading(doLoad){
		if(doLoad){
			$('#viewerTextContainer').addClass("is-loading");
		} else {
			$('#viewerTextContainer').removeClass("is-loading");
		}
	}
	/**
	 * Add a textline to the textView with a textline-image and textline-text element
	 */
	addTextline(textline) {
		const $textlineContainer = $(`<div class='textline-container' data-id='${textline.id}' data-difflen='0'></div>`);
		if(textline.type === "TextLine_gt"){
			$textlineContainer.addClass("line-corrected")
			$textlineContainer.addClass("line-saved");
		} else {
			$textlineContainer.removeClass("line-corrected")
			$textlineContainer.removeClass("line-saved");
		}
		$textlineContainer.append(this._createImageObject(textline));
		$textlineContainer.append($("<br>"));

		const textObject = this._createTextObject(textline);
		$textlineContainer.append(textObject[0]);
		$textlineContainer.append(textObject[1]);
		$textlineContainer.append(textObject[2]);
		$textlineContainer.attr("data-difflen", textObject[3])
		this.container.append($textlineContainer);

		this.zoomBase(textline.id);
		this.resizeTextline(textline.id);
		this._displayPredictedText();
		this._displayDiff();
		this._displayOnlyMismatch();
	}

	/**
	 * Delete a textline from the TextViewer
	 * @param {*} id
	 */
	removeTextline(id) {
		$(`.textline-container[data-id='${id}']`).remove();
	}

	/**
	 * Order the textlines by moving them to the end of the container in order.
	 *
	 * @param {*} readingOrder List of all textline ids in order
	 */
	orderTextlines(readingOrder){
		for(const id of readingOrder) {
			this.container.append($(`.textline-container[data-id='${id}']`));
		}
	}

	/**
	 * Update the content, image and status of a textline
	 *
	 * @param {*} textline
	 */
	updateTextline(textline) {
		const textObject = this._createTextObject(textline);
		$(`.textline-container[data-id='${textline.id}'] > .textline-image`).replaceWith(this._createImageObject(textline));
		$(`.textline-container[data-id='${textline.id}'] > .pred-text`).replaceWith(textObject[0]);
		$(`.textline-container[data-id='${textline.id}'] > .diff-text`).replaceWith(textObject[1]);
		$(`.textline-container[data-id='${textline.id}'] > .textline-text`).replaceWith(textObject[2]);
		$(`.textline-container[data-id='${textline.id}']`).attr("data-difflen", textObject[3]);
		const $textlinecontent = $(`.textline-container[data-id='${textline.id}']`);
		if(textline.type === "TextLine_gt"){
			$textlinecontent.addClass("line-corrected")
			$textlinecontent.addClass("line-saved");
		} else {
			$textlinecontent.removeClass("line-corrected")
			$textlinecontent.removeClass("line-saved");
		}
		this.zoomBase(textline.id);
		this.resizeTextline(textline.id);
		this._displayPredictedText();
		this._displayDiff();
		this._displayOnlyMismatch();
	}

	/**
	 * Display a save of the contents of a textline
	 *
	 * @param {string} id
	 * @param {boolean} doSave
	 */
	saveTextLine(id,doSave=true){
		const $textlinecontent = $(`.textline-container[data-id='${id}']`);
		if(doSave){
			$textlinecontent.addClass("line-saved");
		} else {
			$textlinecontent.removeClass("line-saved");
		}
	}

	/**
	 * Resets the display of a textline
	 * @param id
	 */
	resetTextLine(id){
		const $textlinecontent = $(`.textline-container[data-id='${id}']`);
		$textlinecontent.removeClass("line-corrected");
		$textlinecontent.removeClass("line-saved");

	}

	highlightTextline(id, doHighlight = true) {
		//TODO
	}

	/**
	 * Resize the text input of a textline depending on its content
	 *
	 * @param {string} id
	 */
	resizeTextline(id){
		const $textline = $(`.textline-container[data-id='${id}'] > .textline-text`);

		if($textline && $textline.length > 0){
			const $buffer = $('#textline-viewer-buffer');
			$buffer.css('fontSize',$textline.css('fontSize'));
			const width = $buffer.text($textline[0].value.replace(/ /g, "\xa0")).outerWidth();
			$textline.outerWidth(width);
		}
	}

	/**
	 * Set the pointer focus to a specified textline
	 *
	 * @param {string} id
	 */
	setFocus(id){
		const $textline = $(`.textline-container[data-id=${id}]`);
		if($textline  && $textline.length > 0){
			$textline[0].scrollIntoView({block:"center",behavior:"smooth"});
			$(`.textline-container[data-id='${id}'] > .textline-text`).focus();
		}
	}

	/**
	 * Check if any textline is focused in the text viewer
	 */
	isAnyLineFocused(){
		const focused_element = document.activeElement;
		if($(focused_element).hasClass("textline-text")){
			return true;
		}
		return false;
	}

	/**
	 * Get the id of the currently focused textline or false if none is focused
	 */
	getFocusedId(){
		const focused_element = document.activeElement;
		if($(focused_element).hasClass("textline-text")){
			return focused_element.parentElement.dataset.id;
		}
		return false;
	}

	/**
	 * Retrieve the text content of a specified textline, written in the textviewer
	 *
	 * @param {string} id
	 */
	getText(id){
		const $textline = $(`.textline-container[data-id='${id}'] > .textline-text`);
		if($textline){
			return $textline.val();
		}
		return null;
	}

	/**
	 * Display the zoom of the text viewer in the gui
	 */
	displayZoom(){
		$('.zoomvalue').text(Math.round(this._zoomImage * 10000) / 100);
		$('.zoomvalue_second').text(Math.round(this._zoomText * 10000) / 100);
	}

	/**
	 * Zoom all textline images
	 *
	 * @param {*} zoom_factor
	 */
	zoomGlobalImage(zoom_factor){
		this._zoomImage += zoom_factor;
		this._zoomImage = this._zoomImage > 0 ? this._zoomImage : 0.05;
		for(const textline of $(`.textline-container`)){
			this.zoomBase($(textline).data("id"));
		}
		this.displayZoom();
	}

	/**
	 * Zoom all textline inputs
	 *
	 * @param {*} zoom_factor
	 */
	zoomGlobalText(zoom_factor){
		this._zoomText += zoom_factor;
		this._zoomText = this._zoomText > 0 ? this._zoomText : 0.05;
		for(const textline of $(`.textline-container`)){
			this.zoomBase($(textline).data("id"));
		}
		this.displayZoom();
	}

	/**
	 * Reset the global image zoom to 100%
	 */
	resetGlobalImageZoom(){
		this._zoomImage = 1;
		for(const textline of $(`.textline-container`)){
			this.zoomBase($(textline).data("id"));
		}
		this.displayZoom();
	}

	/**
	 * Reset the global text zoom to 100%
	 */
	resetGlobalTextZoom(){
		this._zoomText = 1;
		for(const textline of $(`.textline-container`)){
			this.zoomBase($(textline).data("id"));
		}
		this.displayZoom();
	}

	/**
	 * Reset current zoom and set to global base zoom
	 *
	 * @param {*} id
	 */
	zoomBase(id){
		const $textline_prediction = $(`.textline-container[data-id='${id}'] > .pred-text`);
		if($textline_prediction && $textline_prediction.length > 0){
			const new_size = this._baseFontSize*this._zoomText;
			$textline_prediction.css('fontSize',`${new_size}px`);
		}
		const $textline_diff = $(`.textline-container[data-id='${id}'] > .diff-text`);
		if($textline_diff && $textline_diff.length > 0){
			const new_size = this._baseFontSize*this._zoomText;
			$textline_diff.css('fontSize',`${new_size}px`);
			$textline_diff.data('raw-size',new_size);
		}
		const $textline_text = $(`.textline-container[data-id='${id}'] > .textline-text`);
		if($textline_text && $textline_text.length > 0){
			const new_size = this._baseFontSize*this._zoomText;
			$textline_text.css('fontSize',`${new_size}px`);
			$textline_text.data('raw-size',new_size);
		}

		const $textline_image = $(`.textline-container[data-id='${id}'] > .textline-image`);
		if($textline_image && $textline_image.length > 0){
			const new_size = this._baseImageSize*this._zoomImage;
			$textline_image.css('height',`${new_size}px`);
			$textline_image.data('raw-size',new_size);
		}

		this.resizeTextline(id);
	}

	/**
	 * Move the currently focused text line input by a delta value
	 *
	 * @param {number} delta
	 * @param {string} id (will use currently focused if none defined)
	 */
	moveTextInput(delta,id=this.getFocusedId()){
		if(id){
			const $textline = $(`.textline-container[data-id='${id}'] > .textline-text`);
			const prev_margin = parseInt($textline.css('marginLeft').replace('px',''));
			$textline.css('marginLeft',`${prev_margin+delta}px`);
		}
	}

	/**
	 * Zoom text line input
	 *
	 * @param {float} zoom_factor
	 * @param {string} id (will use currently focused if none defined)
	 */
	zoomTextInput(zoom_factor,id=this.getFocusedId()){
		const $textline = $(`.textline-container[data-id='${id}'] > .textline-text`);
		if($textline && $textline.length > 0){
			const prev_size = $textline.data("raw-size") ? $textline.data("raw-size")
								: parseInt($textline.css('fontSize').replace('px',''));
			const new_size = prev_size*zoom_factor > 0 ? prev_size*zoom_factor : 1;
			$textline.css('fontSize',`${new_size}px`);
			$textline.data('raw-size',new_size);
		}
	}

	/**
	 * Zoom text line image
	 *
	 * @param {float} zoom_factor
	 * @param {string} id (will use currently focused if none defined)
	 */
	zoomImage(zoom_factor,id=this.getFocusedId()){
		const $textline = $(`.textline-container[data-id='${id}'] > .textline-image`);
		if($textline && $textline.length > 0){
			const prev_size = $textline.data("raw-size") ? $textline.data("raw-size")
								: parseInt($textline.css('height').replace('px',''));
			const new_size = prev_size*zoom_factor > 0 ? prev_size*zoom_factor : 1;
			$textline.css('height',`${new_size}px`);
			$textline.data('raw-size',new_size);
		}
	}

	/**
	 * Insert a character into the current position on the textline
	 *
	 * @param {string} character
	 */
	insertCharacterTextLine(character){
		if(this.isOpen()){
			const id = this.getFocusedId();
			const $input = $(`.textline-container[data-id='${id}'] > .textline-text`);
			const start = $input[0].selectionStart;
			const end = $input[0].selectionEnd;
			let text = $input.val();

			$input.val(text.substring(0,start)+character+text.substring(end));
			this.resizeTextline(id);
			$input.focus();
			$input[0].selectionStart = start+character.length;
			$input[0].selectionEnd = start+character.length;
			this.saveTextLine(id,false);
		}
	}
	/**
	 * Create a textline text object for a given textline.
	 *
	 * @param {*} textline
	 */
	_createTextObject(textline) {
		const $textlineText =  $(`<input class='textline-text'></input>`);

		// Fill with content
		const hasPredict = 1 in textline.text;
		const hasGT = 0 in textline.text;

		let diff = "";

		if(hasGT){
			$textlineText.addClass("line-corrected");
			$textlineText.val(textline.text[0]);
			if(hasPredict) {
				diff = this._createDiffObject(textline.text);
			}
		} else {
			$textlineText.removeClass("line-corrected");
			$textlineText.removeClass("line-saved");
			if (hasPredict){
				$textlineText.val(textline.text[1]);
			} else {
				$textlineText.val("");
			}
		}
		const $diffText = $(`<p class="diff-text">${this._prettifyDiff(diff)}</p>`);
		if(diff === "") {$diffText.hide();} else {$diffText.show();}

		const pred_text = hasPredict ? textline.text[1] : "";
		const $predText = $(`<p class="pred-text">${pred_text}</p>`);
		return [$predText, ($diffText), ($textlineText), (diff.length)];
	}

	/**
	 * Create a textline image object for a given textline
	 *
	 * @param {*} textline
	 */
	_createImageObject(textline) {
		let minX = this.image[0].naturalWidth;
		let minY = this.image[0].naturalHeight;
		let maxX = 0;
		let maxY = 0;
		for(const point of textline.coords.points){
			minX = Math.min(point.x,minX);
			minY = Math.min(point.y,minY);
			maxX = Math.max(point.x,maxX);
			maxY = Math.max(point.y,maxY);
		}

		const width = maxX-minX;
		const height = maxY-minY;

		const $textlineImage =  $(`<canvas class='textline-image' width='${width}' height='${height}'/>`);

		const ctx = $textlineImage[0].getContext("2d");

		// Add image into clipping area
		ctx.drawImage(this.image[0],minX,minY,width,height,0,0,width,height);
		return $textlineImage;
	}
	/**
	 * Checks whether predicted text should get displayed
	 *
	 */
	_displayPredictedText(){
		if($("#displayPrediction").is(":checked")){
			$(".line-corrected").prev(".diff-text").hide();
			$(".line-corrected").prev().prev(".pred-text").show();
		}else{
			$(".line-corrected").prev(".diff-text").show();
			$(".pred-text").hide();
		}
	}
	/**
	 * Create a diff object for a given textline
	 *
	 * @param {*} textline
	 */
	_createDiffObject(textline){
		let gtText = textline[0];
		let predText = textline[1];

		return this._dmp.diff_main(predText, gtText);
	}
	/**
	 * prettify a diff object for display
	 * NOTE: Currently uses modified standard dmp prettifier
	 *
	 * @param {*} diff
	 */
	_prettifyDiff(diff){
		let html = [];
		let pattern_amp = /&/g;
		let pattern_lt = /</g;
		let pattern_gt = />/g;
		let pattern_para = /\n/g;
		for (let x = 0; x < diff.length; x++) {
			let op = diff[x][0];    // Operation (insert, delete, equal)
			let data = diff[x][1];  // Text of change.
			let text = data.replace(pattern_amp, '&amp;').replace(pattern_lt, '&lt;')
				.replace(pattern_gt, '&gt;').replace(pattern_para, '&para;<br>');
			switch (op) {
				case DIFF_INSERT:
					html[x] = '<span style="background:#58e562;">' + text + '</span>';
					break;
				case DIFF_DELETE:
					html[x] = '<span style="background:#e56258;">' + text + '</span>';
					break;
				case DIFF_EQUAL:
					html[x] = '<span>' + text + '</span>';
					break;
			}
		}
		return html.join('');
	}
	/**
	 * Checks whether differences between gt and pred should get displayed
	 *
	 */
	_displayDiff(){
		if($("#displayDiff").is(":checked")){
			document.getElementById("displayPredictionContainer").style.display = "block";
			document.getElementById("displayMismatchContainer").style.display = "block";
			$(".line-corrected").prev(".diff-text").show();
		}else{
			document.getElementById("displayPredictionContainer").style.display = "none";
			document.getElementById("displayMismatchContainer").style.display = "none";
			$(".diff-text").hide();
		}
	}
	/**
	 * Hides textlines with no differences
	 *
	 */
	_displayOnlyMismatch(){
		if($("#displayMismatch").is(":checked")){
			$(".textline-container").each(function () {
				if(parseInt($(this).attr("data-difflen")) > 1 ) {
					$(this).show();
				} else {
					$(this).hide();
				}
			});
		}else{
			$(".textline-container").each(function () {
				$(this).show();
			});
		}
	}
}
