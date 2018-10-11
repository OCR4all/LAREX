class Colors {
	constructor(colors, regionColors) {
		this._colors = colors;
		this._regionColors = regionColors;
	}
	
	getColor(segmentType) {
		let color = this.getPredefinedColor(this._regionColors[segmentType]);
		
		if (!color) {
			let freeColors = this.getAvailableColors();

			if (freeColors && freeColors.length > 0) {
				color = freeColors.pop();
			} else {
				// Fallback generator
				console.log("Warning: Not enough colors for region types. Will display region \'"+segmentType+"\' as black");
				color = [0,0,0];
			}

		}
		return new paper.Color(color);
	}

	getPredefinedColor(id){
		return this._colors[id];
	}

	setColor(segmentType,colorID){
		this._regionColors[segmentType] = colorID;
	}

	getPredefinedColors(){
		return this._colors;
	}

	getPredefinedColorIDs(){
		var ids = [];
    	for (var i = 0; i < this._colors.length; i++) {
      		ids.push(i);
    	}
   		return ids;
	}
	
	getAvailableColors(){
		let freeColorsIDs = this.getAvailableColorIDs();
		return freeColorsIDs.map(id => {return this.getPredefinedColor(id)});
	}
	
	getAvailableColorIDs(){
		let freeColors = this.getPredefinedColorIDs().slice();
		Object.keys(this._regionColors).forEach(type =>{
			freeColors.splice($.inArray(this._regionColors[type], freeColors), 1)});
		return freeColors;
	}

	getColorID(color){
		for(let index = 0; index < this._colors.length; index++){
			let c = this._colors[index];
			if(color.red === c.red && color.blue === c.blue && color.green === c.green) 
				return index;
		}
		return undefined;
	}
}
