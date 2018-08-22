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
				const id = this._segmenttypes[segmentType]
				const counter = 6;
				const modifier1 = (id + 6) % counter;
				const modifier2 = Math.floor(((id - 6) / counter));
				const c = modifier2 == 0 ? 1 : 1 - (1 / modifier2);

				switch (modifier1) {
					case 0: color = [c, 0, 0];
						break;
					case 1: color = [0, c, 0];
						break;
					case 2: color = [0, 0, c];
						break;
					case 3: color = [c, c, 0];
						break;
					case 4: color = [0, c, c];
						break;
					case 5: color = [c, 0, c];
						break;
				}
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
