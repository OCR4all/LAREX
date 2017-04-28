package com.web.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

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
	protected Map<String, Polygon> fixedSegments;
	
	@JsonCreator
	public PageSettings(
			@JsonProperty("target") int pageNr,
			@JsonProperty("cuts") Map<String,Polygon> cuts,
			@JsonProperty("segments") Map<String,Polygon> fixedSegments){
		this.pageNr = pageNr;
		this.cuts = cuts;
		this.fixedSegments = fixedSegments;
	}
	
	public PageSettings(int pageNr){ 
		this(pageNr, new HashMap<String,Polygon>(), new HashMap<String,Polygon>());
	}
	
	public void addCut(Polygon cut){
		cuts.put(cut.getId(), cut);
	}
	
	public Map<String, Polygon> getCuts() {
		return new HashMap<String, Polygon>(cuts);
	}
	
	public Map<String, Polygon> getFixedSegments() {
		return new HashMap<String, Polygon>(fixedSegments);
	}
	
	
	public int getTarget() {
		return pageNr;
	}
}
