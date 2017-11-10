package com.web.communication;

import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import com.web.model.Book;
import com.web.model.BookSegmentation;
import com.web.model.BookSettings;
import com.web.model.PageSegmentation;

import larex.regions.type.RegionType;

/**
 * A communication object for the controller to parse a complete book response.
 * Contains a book, settings for segmentation and a (potentially uncompleted)
 * segmentation.
 * 
 */
public class FullBookResponse {

	@JsonProperty("book")
	private Book book;
	@JsonProperty("segmentation")
	private Map<Integer, PageSegmentation> segmentation;
	@JsonProperty("settings")
	private BookSettings settings;
	@JsonProperty("segmenttypes")
	protected Map<RegionType, Integer> segmentTypes;

	@JsonCreator
	public FullBookResponse(@JsonProperty("book") Book book,
			@JsonProperty("segmentation") Map<Integer, PageSegmentation> segmentation,
			@JsonProperty("settings") BookSettings settings) {
		this.book = book;
		this.segmentation = segmentation;
		this.settings = settings;
	}

	public Book getBook() {
		return book;
	}

	public Map<Integer, PageSegmentation> getSegmentation() {
		return segmentation;
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
