class Colors {
	constructor(colors, regionColors) {
		this._colors = colors;
		this._regionColors = regionColors;
	}
	
	getColor(segmentType) {
		return new paper.Color(this.unpackColor(this.getColorID(segmentType)));
	}

	getAssigned(){
		return JSON.parse(JSON.stringify(this._regionColors));
	}

	getColorID(segmentType) {
		if (!this.hasColor(segmentType)) 
			throw "No color for region type defined! ["+segmentType+"]";
		
		return this._regionColors[segmentType];
	}

	hasColor(segmentType) {
		return this._regionColors.hasOwnProperty(segmentType);
	}

	assignAvailableColor(segmentType){
		if(!this.hasColor(segmentType)){
			let freeColorIDs = this.getAvailableColorIDs();

			if (freeColorIDs && freeColorIDs.length > 0) {
				const colorID = freeColorIDs.pop();
				this.setColor(segmentType,colorID);
			} else { 
				throw "No colors left!";
			}
		}
	}

	unpackColor(id){
		return this._colors[id];
	}

	setColor(segmentType, colorID){
		this._regionColors[segmentType] = colorID;
	}

	getAllColorIDs(){
		const ids = [];
		for (let i = 0; i < this._colors.length; i++)
      		ids.push(i);
   		return ids;
	}
	
	getAvailableColorIDs(){
		let freeColors = this.getAllColorIDs().slice();
		Object.keys(this._regionColors).forEach(type =>{
			freeColors.splice($.inArray(this._regionColors[type], freeColors), 1)});
		return freeColors;
	}
}
