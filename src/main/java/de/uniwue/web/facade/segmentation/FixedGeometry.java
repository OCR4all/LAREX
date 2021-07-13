package de.uniwue.web.facade.segmentation;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.uniwue.web.model.Element;
import de.uniwue.web.model.Region;

/**
 * Handles page sensitive settings for the algorithm
 *
 */
public class FixedGeometry {

	@JsonProperty("cuts")
	protected Map<String, Element> cuts;
	@JsonProperty("segments")
	protected Map<String, Region> fixedSegments;

	@JsonCreator
	public FixedGeometry(
			@JsonProperty("cuts") Map<String, Element> cuts,
			@JsonProperty("segments") Map<String,Region> fixedSegments){
		this.cuts = cuts;
		this.fixedSegments = fixedSegments;
	}

	public FixedGeometry(){
		this(new HashMap<>(), new HashMap<>());
	}

	public Map<String, Element> getCuts() {
		return new HashMap<>(cuts);
	}

	public Map<String, Region> getFixedSegments() {
		return new HashMap<>(fixedSegments);
	}
}
