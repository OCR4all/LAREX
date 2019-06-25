package com.web.facade;

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
public class PageSettings {

	@JsonProperty("target")
	protected int pageNr;
	@JsonProperty("cuts")
	protected Map<String, Polygon> cuts;
	@JsonProperty("segments")
	protected Map<String, Region> fixedSegments;
	
	@JsonCreator
	public PageSettings(
			@JsonProperty("target") int pageNr,
			@JsonProperty("cuts") Map<String, Polygon> cuts,
			@JsonProperty("segments") Map<String,Region> fixedSegments){
		this.pageNr = pageNr;
		this.cuts = cuts;
		this.fixedSegments = fixedSegments;
	}
	
	public PageSettings(int pageNr){ 
		this(pageNr, new HashMap<String,Polygon>(), new HashMap<String,Region>());
	}
	
	public void addCut(Region cut){
		cuts.put(cut.getId(), cut);
	}
	
	public Map<String, Polygon> getCuts() {
		return new HashMap<String, Polygon>(cuts);
	}
	
	public Map<String, Region> getFixedSegments() {
		return new HashMap<String, Region>(fixedSegments);
	}
	
	
	public int getTarget() {
		return pageNr;
	}
}
