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
		$("#displayDiff").prop('checked', true);
		//$("#displayPrediction").prop('checked', true);
		$("#displayWordConf").prop('checked', true);
		this._toggleConfSettings();
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
		$textlineContainer.attr("data-difflen", textObject[3]);
		$textlineContainer.attr("data-minconf", textObject[4]);
		$textlineContainer.attr("data-hasValidVariant", textObject[5]);
		this.container.append($textlineContainer);

		this.zoomBase(textline.id);
		this.resizeTextline(textline.id);
		this._displayPredictedText();
		this._displayDiff();
		this._displayOnly();
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
		$(`.textline-container[data-id='${textline.id}']`).attr("data-minconf", textObject[4]);
		$(`.textline-container[data-id='${textline.id}']`).attr("data-hasValidVariant", textObject[5]);
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
		this._displayOnly();
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
		let pred_text_container = this._checkConfidence(textline, hasPredict);
		let pred_text = pred_text_container[0];
		let minConf = pred_text_container[1];
		let hasValidVariant = pred_text_container[2];
		const $predText = $(`<p class="pred-text">${pred_text}</p>`);
		return [$predText, ($diffText), ($textlineText), (diff.length), (minConf), (hasValidVariant)];
	}

	_checkConfidence(textline, hasPredict) {
		let pred_text = hasPredict ? textline.text[1] : "";
		let displayConf = $("#displayConfidence").is(":checked");
		let displayWordConf = $("#displayWordConf").is(":checked");
		let displayGlyphConf = $("#displayGlyphConf").is(":checked");
		let threshold1 = parseFloat($("#confThreshold1").val());
		let displayAlternativeGlyphs = $("#displayConfidence2").is(":checked");
		let threshold2 = displayAlternativeGlyphs ? parseFloat($("#confThreshold2").val()) : -1.0;
		let useGradient = false;
		let minConf = 1.0;
		let hasValidVariants = false;
		if(hasPredict && displayConf && (displayWordConf || displayGlyphConf || displayAlternativeGlyphs) && 'words' in textline && textline.words.length > 0 ) {
			//ensure words are sorted by id
			let collator = new Intl.Collator(undefined, {numeric: true, sensitivity: 'base'});
			let sortedWords = textline.words.sort((a, b) => collator.compare(a.id, b.id));
			let wordList = [];
			//check if line length matches word length
			if(textline.words.length !== pred_text.split(' ').length) {
				console.log("WARNING: Textline length differs from word count:");
				console.log('textline length    : ' + pred_text.split(' ').length.toString());
				console.log(pred_text);
				console.log('textline word count: ' + textline.words.length.toString());
			}
			for(let word of sortedWords) {
				let word_text = word.text;
				if((displayGlyphConf || displayAlternativeGlyphs) && 'glyphs' in word && word.glyphs.length > 0) {
					//ensure glyphs are sorted by id
					let sortedGlyphs = word.glyphs.sort((a, b) => collator.compare(a.id, b.id));
					let glyphList = [];
					for(let glyph of sortedGlyphs) {
						if('glyphVariants' in glyph && glyph.glyphVariants.length > 0) {
							let mainVariant = glyph.glyphVariants[0];
							let glyph_text = mainVariant.text;
							if('conf' in mainVariant) {
								minConf = mainVariant.conf < minConf ? mainVariant.conf : minConf;
								let markedUp = this._markUpGlyphConfidence(glyph.glyphVariants, threshold1, threshold2);
								glyph_text = markedUp[0]
								hasValidVariants = hasValidVariants || markedUp[1];
							}
							glyphList.push(glyph_text);
						}
					}
					word_text = glyphList.join('');
				}else {
					if('conf' in word) {
						minConf = word.conf < minConf ? word.conf : minConf;
						word_text = this._markUpConfidence(word_text, word.conf, threshold1, false);
					}
				}
				wordList.push(word_text);
			}
			pred_text = wordList.join(' ');
		}
		//replace multiple whitespaces
		pred_text = pred_text.replace( /  +/g, ' ' );
		return [pred_text,minConf,hasValidVariants];
	}
	/**
	 * Checks if text is above certain threshold and colors its background using css
	 * @param text
	 * @param confidence
	 * @param threshold
	 * @param useGradient
	 * @returns {string} html/css containing text with background
	 * @private
	 */
	_markUpConfidence(text,confidence, threshold, useGradient){
		//temporarily use insert/delete colors
		let aboveColor = globalSettings.conf_above_color;
		let belowColor = globalSettings.conf_below_color;
		if(aboveColor == "" || !this._validColor(aboveColor)) {aboveColor = "#FFFFFF";}
		if(belowColor == "" || !this._validColor(belowColor)) {belowColor = "#e56123";}
		let html;
		if(confidence > threshold) {
			if(text == ' ') {text = '';}
			if(aboveColor == "#FFFFFF") { return text;}
			return '<span style="background:' + aboveColor + ';">' + text + '</span>';
		} else {
			if(text == ' ') {text = '⌴';}
			return '<span style="background:' + belowColor + ';">' + text + '</span>';
		}
	}
	/**
	 * Checks if text is above certain threshold and colors its background using css
	 * @param text
	 * @param confidence
	 * @param threshold
	 * @returns {(*|boolean)[]} html/css containing text with background
	 * @private
	 */
	_markUpGlyphConfidence(glyphVariants, threshold1, threshold2){
		//init colors
		let belowT2Color = globalSettings.conf_below_threshold2_color;
		if(belowT2Color == "" || !this._validColor(belowT2Color)) {belowT2Color = "#e5c223";}

		let text = glyphVariants[0].text;
		if(text == ' ') {text = '⌴';}
		let confidence = glyphVariants[0].conf;
		let hasValidVariant = false;
		if(threshold2 > 0.0 && glyphVariants.length > 1) {
			let validGlyphList = []
			for(let i = 1; i < glyphVariants.length; i++) {
				let glyphVariant = glyphVariants[i];
				if('conf' in glyphVariant && glyphVariant.conf > threshold2 && glyphVariant.text != null) {
					validGlyphList.push(glyphVariant);
					hasValidVariant = true;
				}
			}
			if(validGlyphList.length > 0) {
				let variantHtml = "";
				for (let variantGlyph of validGlyphList) {
					let variantGlyphText = variantGlyph.text;
					if(variantGlyphText == ' ') {variantGlyphText = '⌴';}
					variantHtml = variantHtml + '<option class="glyph-option">' + variantGlyph.text + '</option>';
				}
				text = '<span></span><select class="glyph-select" style="background:' + belowT2Color + ';"><option class="glyph-option">' + text + '</option>' + variantHtml + '</select></span>';
				return [text,hasValidVariant];
			}
		}
		return [this._markUpConfidence(text,confidence,threshold1,false),hasValidVariant];
	}

	/**
	 *  *** Not yet implemented ***
	 *  calculate color gradient with confidence and three colors
	 *  https://stackoverflow.com/a/61396704
	 * @param confidence
	 * @param rgbColor1
	 * @param rgbColor2
	 * @param rgbColor3
	 * @returns {string} rgbColor
	 */
	colorGradient(confidence, rgbColor1, rgbColor2, rgbColor3) {
		if( confidence < 0) {
			return 'rgb(255,255,255)';
		}
		let color1 = rgbColor1;
		let color2 = rgbColor2;
		let fade = confidence;

		// Do we have 3 colors for the gradient? Need to adjust the params.
		if(rgbColor3) {
			fade = fade * 2;

			// Find which interval to use and adjust the fade percentage
			if(fade >= 1) {
				fade -= 1;
				color1 = rgbColor2;
				color2 = rgbColor3;
			}
		}

		let diffRed = color2.red - color1.red;
		let diffGreen = color2.green - color1.green;
		let diffBlue = color2.blue - color1.blue;

		let gradient = {
			red: Math.round(Math.floor(color1.red + (diffRed * fade))),
			green: Math.round(Math.floor(color1.green + (diffGreen * fade))),
			blue: Math.round(Math.floor(color1.blue + (diffBlue * fade))),
		};
		return 'rgb(' + gradient.red + ',' + gradient.green + ',' + gradient.blue + ')';
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
			document.getElementById("displayConfidenceContainer").style.display = "block";
			$(".pred-text").show();
		}else{
			document.getElementById("displayConfidenceContainer").style.display = "none";
			$(".pred-text").hide();
		}
		this._toggleConfSettings();
	}
	_toggleConfSettings(){
		if(!($("#displayConfidence").is(":checked")) || !($("#displayPrediction").is(":checked"))){
			document.getElementById("inputConfThresholdContainer").style.display = "none";
			document.getElementById("displayGlyphConfContainer").style.display = "none";
			document.getElementById("displayWordConfContainer").style.display = "none";
			document.getElementById("displayConfBelowContainer").style.display = "none";
			document.getElementById("displayConfidence2Container").style.display = "none";
			document.getElementById("inputConfThreshold2Container").style.display = "none";
			document.getElementById("displayConfAboveContainer").style.display = "none";
			if(!$("#displayGlyphConf").is(":checked")) {
				document.getElementById("displayConfidence2Container").style.display = "none";
				if(!$("#displayConfidence2").is(":checked")) {
					document.getElementById("inputConfThreshold2Container").style.display = "none";
					document.getElementById("displayConfAboveContainer").style.display = "none";
				}
			}
		}else{
			document.getElementById("inputConfThresholdContainer").style.display = "block";
			document.getElementById("displayGlyphConfContainer").style.display = "block";
			document.getElementById("displayWordConfContainer").style.display = "block";
			document.getElementById("displayConfBelowContainer").style.display = "block";
			if($("#displayGlyphConf").is(":checked")) {
				document.getElementById("displayConfidence2Container").style.display = "block";
				if($("#displayConfidence2").is(":checked")) {
					document.getElementById("inputConfThreshold2Container").style.display = "block";
					document.getElementById("displayConfAboveContainer").style.display = "block";
				}
			}
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
		let insertColor = globalSettings.diff_insert_color;
		let deleteColor = globalSettings.diff_delete_color;
		if(insertColor == "" || !this._validColor(insertColor)) {insertColor = "#58e123";}
		if(deleteColor == "" || !this._validColor(deleteColor)) {deleteColor = "#e56123";}
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
					html[x] = '<span style="background:' + insertColor + ';">' + text + '</span>';
					break;
				case DIFF_DELETE:
					html[x] = '<span style="background:' + deleteColor + ';">' + text + '</span>';
					break;
				case DIFF_EQUAL:
					html[x] = '<span>' + text + '</span>';
					break;
			}
		}
		return html.join('');
	}
	/**
	 * Checks if color is valid css color representation.
	 * This works for ALL color types not just hex values. It also does not append unnecessary elements to the DOM tree.
	 */
	_validColor(color){
		if(color=="")return false;
		let $div = $("<div>");
		$div.css("border", "1px solid "+color);
		return ($div.css("border-color")!="")
	}
	/**
	 * Checks whether differences between gt and pred should get displayed
	 *
	 */
	_displayDiff(){
		if($("#displayDiff").is(":checked")){
			document.getElementById("displayMismatchContainer").style.display = "block";
			$(".line-corrected").prev(".diff-text").show();
		}else{
			document.getElementById("displayMismatchContainer").style.display = "none";
			$(".diff-text").hide();
		}
	}
	/**
	 * Hides textlines which do not meat configured criteria
	 *
	 */
	_displayOnly(){
		let onlyDiffMismatch = $("#displayMismatch").is(":checked");
		let onlyConfBelow = $("#displayConfBelow").is(":checked");
		let onlyConfAbove = $("#displayConfAbove").is(":checked");
		$(".textline-container").each(function () {
			let difflenBool = parseInt($(this).attr("data-difflen")) > 1;
			let confBelowBool = (parseFloat($(this).attr("data-minconf")) < parseFloat($("#confThreshold1").val()));
			let confAboveBool = $(this).attr("data-hasValidVariant") == 'true';
			if((!onlyDiffMismatch || difflenBool) &&
				(!onlyConfBelow || confBelowBool) &&
				(!onlyConfAbove || confAboveBool)) {
				$(this).show();
			} else {
				$(this).hide();
			}
		});

	}
	_copyTextToClipboard(text) {
		navigator.clipboard.writeText(text).then(function() {
			console.log('Async: Copying to clipboard was successful!');
			if(text = ' ') {text = '⌴';}
			let toastMsg = text + " copied to clipboard!";
			Materialize.toast(toastMsg, 4000, "green");
			// handle old materialize bug where multiple toasts are displayed
			// TODO: remove with vue.js
			let i = 0;
			$('#toast-container').children().each(function () {
				if(i > 0) {
					$(this).remove();
				}
				i = i + 1;
			});
		}, function(err) {
			console.error('Async: Could not copy text: ', err);
			Materialize.toast("ERROR: glyph could not be copied!", 4000, "grey darken-4");
		});
	}
}
