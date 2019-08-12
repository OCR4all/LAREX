package com.web.facade.segmentation;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.web.model.Polygon;
import com.web.model.Region;

/**
 * Handles page sensitive settings for the algorithm
 * 
 */
public class FixedGeometry {

	@JsonProperty("cuts")
	protected Map<String, Polygon> cuts;
	@JsonProperty("segments")
	protected Map<String, Region> fixedSegments;
	
	@JsonCreator
	public FixedGeometry(
			@JsonProperty("cuts") Map<String, Polygon> cuts,
			@JsonProperty("segments") Map<String,Region> fixedSegments){
		this.cuts = cuts;
		this.fixedSegments = fixedSegments;
	}
	
	public FixedGeometry(){ 
		this(new HashMap<String,Polygon>(), new HashMap<String,Region>());
	}
	
	public Map<String, Polygon> getCuts() {
		return new HashMap<String, Polygon>(cuts);
	}
	
	public Map<String, Region> getFixedSegments() {
		return new HashMap<String, Region>(fixedSegments);
	}
}
