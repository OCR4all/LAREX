package com.web.communication;

import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import com.web.model.Book;
import com.web.model.BookSettings;
import com.web.model.PageSegmentation;

import larex.regions.type.RegionType;

/**
 * A communication object for the controller to parse a basic response to the viewer.
 * Contains a book, settings for segmentation and segmentation types.
 * 
 */
public class BasicResponse {

	@JsonProperty("book")
	private Book book;
	@JsonProperty("settings")
	private BookSettings settings;
	@JsonProperty("segmenttypes")
	protected Map<RegionType, Integer> segmentTypes;

	@JsonCreator
	public BasicResponse(@JsonProperty("book") Book book,
			@JsonProperty("settings") BookSettings settings) {
		this.book = book;
		this.settings = settings;
	}

	public Book getBook() {
		return book;
	}

	public BookSettings getSettings() {
		return settings;
	}

	public Map<RegionType, Integer> getSegmentTypes() {
		if (segmentTypes == null)
			initSegmentTypes();
		return segmentTypes;
	}

	private void initSegmentTypes() {
		this.segmentTypes = new HashMap<RegionType, Integer>();
		int i = 0;
		for (RegionType type : RegionType.values()) {
			segmentTypes.put(type, i);
			i++;
		}
	}
}
