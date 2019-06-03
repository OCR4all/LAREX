/* The viewer is a display for result segments, region segments and contours of any kind. 
 * It can handle inputs by forwarding it to a input manager (ViewerInput) 
 * All functionality about viewing elements in the viewer is handled here. 
 * It does not handle editing these elements. */
class TextViewer {
	constructor(viewerInput, colors) {
		this.container = $("viewerText");
		this.thisInput = viewerInput;
		this.image;
	}

	setImage(id) {
		this.image = $(`#${id}`);
	}
	
	/**
	 * Add a textline to the textView with a textline-image and textline-text element
	 */
	addTextline(textline) {
		const $textlineContainer = $(`<div class='textline-container' data-id='${textline.id}'></div>`);
		this.container.append($textlineContainer);

		$textlineContainer.append(this._createImageObject(textline));
		$textlineContainer.append(this._createTextObject(textline));

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
	}

	highlightTextline(id, doHighlight = true) {
		const polygon = this._polygons[id];
		if (polygon) {
			if (polygon.fillColor != null) {
				if (doHighlight) {
					polygon.fillColor.alpha = 0.6;
				} else {
					polygon.fillColor.alpha = polygon.fillColor.mainAlpha;
				}
			}
		}
	}

	focusTextline(id, doFocus = true) {
		this.displayOverlay("focus",doFocus);
		const polygon = this._polygons[id];
		if (polygon && doFocus) {
			this._focus.removeChildren(1);
			this._focus.addChild(new paper.Path(polygon.segments));
		}
		this._focus.visible = doFocus;
	}

	/**
	 * Create a textline text object for a given textline.
	 * 
	 * @param {*} textline 
	 */
	_createTextObject(textline) {
		const $textlineText =  $(`<span class='textline-text'></span>`);

		// Fill with content
		const hasPredict = 1 in this.tempTextline.text;
		const hasGT = 0 in this.tempTextline.text;

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
		let minX = this.image.width();
		let minY = this.image.height();
		let maxX = 0;
		let maxY = 0;
		for(const x,y of textline.points){
			minX = Math.min(x,minX);
			minY = Math.min(y,minY);
			maxX = Math.max(x,maxX);
			maxY = Math.max(y,maxY);
		}

		const $textlineImage =  $(`<canvas class='textline-image' width='${maxX - minX}' height='${maxY - minY}'/>`)

		const ctx = $textlineImage.getContext("2d");
		ctx.drawImage(this.image,minX,minY,maxX-minX,maxY-minY,0,0);
		return $textlineImage;
	}

}
