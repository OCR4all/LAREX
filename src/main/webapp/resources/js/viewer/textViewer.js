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
	}

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

		$textlineContainer.append(this._createImageObject(textline));
		$textlineContainer.append($("<br>"));
		$textlineContainer.append(this._createTextObject(textline));
		this.container.append($textlineContainer);

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

	
	updateTextline(textline) {
		$(`.textline-container[data-id='${textline.id}'] > .textline-image`).replaceWith(this._createImageObject(textline));
		$(`.textline-container[data-id='${textline.id}'] > .textline-text`).replaceWith(this._createTextObject(textline));
		this.resizeTextline(textline.id);
	}

	highlightTextline(id, doHighlight = true) {
		//TODO
	}

	resizeTextline(id){
		const $textline = $(`.textline-container[data-id='${id}'] > .textline-text`);
		const width = $('#textline-viewer-buffer').text($textline.val()).outerWidth();

		$textline.outerWidth(width);
	}

	focusTextline(id, doFocus = true) {
		//TODO
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
		ctx.drawImage(this.image[0],minX,minY,width,height,0,0,width,height);
		return $textlineImage;
	}
}
