package com.web.communication;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import com.web.model.Polygon;

import larex.regions.type.RegionType;

/**
 * Communication object for the gui to request an export of a specifit page.
 * Contains the export settings and the page number to export.
 * 
 */
public class ExportRequest {

	@JsonProperty("page")
	private int page;
	@JsonProperty("segmentsToIgnore")
	private List<String> segmentsToIgnore;
	@JsonProperty("segmentsToMerge")
	private Map<String,ArrayList<String>> segmentsToMerge;
	@JsonProperty("changedTypes")
	private Map<String,RegionType> changedTypes;
	@JsonProperty("fixedRegions")
	protected Map<String, Polygon> fixedRegions;

	@JsonCreator
	public ExportRequest(@JsonProperty("page") int page, 
			@JsonProperty("segmentsToIgnore") List<String> segmentsToIgnore,
			@JsonProperty("segmentsToMerge") Map<String,ArrayList<String>> segmentsToMerge,
			@JsonProperty("changedTypes") Map<String,RegionType> changedTypes,
			@JsonProperty("fixedRegions") Map<String, Polygon> fixedRegions) {
		this.page = page;
		this.segmentsToIgnore = segmentsToIgnore;
		this.segmentsToMerge = segmentsToMerge;
		this.changedTypes = changedTypes;
		this.fixedRegions = fixedRegions;
	}

	public int getPage() {
		return page;
	}

	public List<String> getSegmentsToIgnore() {
		return segmentsToIgnore;
	}
	
	public Map<String,ArrayList<String>> getSegmentsToMerge() {
		return segmentsToMerge;
	}
	
	public Map<String, RegionType> getChangedTypes() {
		return changedTypes;
	}
	
	public Map<String, Polygon> getFixedRegions() {
		return fixedRegions;
	}
}
