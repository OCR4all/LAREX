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
		this.zoom = 1;
		this._baseImageSize = 35;
		this._baseFontSize = 20;
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
		} else {
			this.root.addClass("hide");		
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
	
	/**
	 * Add a textline to the textView with a textline-image and textline-text element
	 */
	addTextline(textline) {
		const $textlineContainer = $(`<div class='textline-container' data-id='${textline.id}'></div>`);
		if(textline.type == "TextLine_gt"){
			$textlineContainer.addClass("line-corrected")
			$textlineContainer.addClass("line-saved");
		} else {
			$textlineContainer.removeClass("line-corrected")
			$textlineContainer.removeClass("line-saved");
		}
		$textlineContainer.append(this._createImageObject(textline));
		$textlineContainer.append($("<br>"));
		$textlineContainer.append(this._createTextObject(textline));
		this.container.append($textlineContainer);

		this.zoomBase(textline.id);
		this.resizeTextline(textline.id);
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
		$(`.textline-container[data-id='${textline.id}'] > .textline-image`).replaceWith(this._createImageObject(textline));
		$(`.textline-container[data-id='${textline.id}'] > .textline-text`).replaceWith(this._createTextObject(textline));
		const $textlinecontent = $(`.textline-container[data-id='${textline.id}']`);
		if(textline.type == "TextLine_gt"){
			$textlinecontent.addClass("line-corrected")
			$textlinecontent.addClass("line-saved");
		} else {
			$textlinecontent.removeClass("line-corrected")
			$textlinecontent.removeClass("line-saved");
		}
		this.zoomBase(textline.id);
		this.resizeTextline(textline.id);
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
		$('.zoomvalue').text(Math.round(this.zoom * 10000) / 100);
	}

	/**
	 * Zoom all textlines 
	 * 
	 * @param {*} zoom_factor 
	 */
	zoomGlobal(zoom_factor){
		this.zoom += zoom_factor;
		this.zoom = this.zoom > 0 ? this.zoom : 0.05;
		for(const textline of $(`.textline-container`)){
			this.zoomBase($(textline).data("id"));
		}
		this.displayZoom();
	}

	/**
	 * Reset the global zoom to 100% 
	 */
	resetGlobalZoom(){
		this.zoom = 1;
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
		const $textline_text = $(`.textline-container[data-id='${id}'] > .textline-text`);
		if($textline_text && $textline_text.length > 0){
			const new_size = this._baseFontSize*this.zoom;
			$textline_text.css('fontSize',`${new_size}px`);
			$textline_text.data('raw-size',new_size);
		}

		const $textline_image = $(`.textline-container[data-id='${id}'] > .textline-image`);
		if($textline_image && $textline_image.length > 0){
			const new_size = this._baseImageSize*this.zoom;
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
	 * Insert a character into the current poisition on the textline
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

		if(hasGT){
			$textlineText.addClass("line-corrected")
			$textlineText.val(textline.text[0]);
		} else {
			$textlineText.removeClass("line-corrected")
			$textlineText.removeClass("line-saved");
			if (hasPredict){
				$textlineText.val(textline.text[1]);
			} else {
				$textlineText.val("");
			}
		}
		return $textlineText;
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
		for(const point of textline.points){
			minX = Math.min(point.x,minX);
			minY = Math.min(point.y,minY);
			maxX = Math.max(point.x,maxX);
			maxY = Math.max(point.y,maxY);
		}

		const width = maxX-minX;
		const height = maxY-minY;

		const $textlineImage =  $(`<canvas class='textline-image' width='${width}' height='${height}'/>`);

		const ctx = $textlineImage[0].getContext("2d");

		// Start clipping area
		ctx.beginPath();
		const transposed_points = textline.points.map(point => [point.x-minX,point.y-minY]);
		if(transposed_points.length > 0){
			ctx.moveTo(...transposed_points[0]);
			for(const [x,y] of transposed_points){
				ctx.lineTo(x,y);
			}
			ctx.closePath();
			ctx.fill();
			ctx.clip();
		}
		// Add image into clipping area
		ctx.drawImage(this.image[0],minX,minY,width,height,0,0,width,height);
		return $textlineImage;
	}
}
