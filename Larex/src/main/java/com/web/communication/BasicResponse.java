package com.web.communication;

import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import com.web.model.Book;
import com.web.model.BookSettings;

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
	@JsonProperty("regionTypes")
	protected Map<RegionType, Integer> regionTypes;

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

	public Map<RegionType, Integer> getregionTypes() {
		if (regionTypes == null)
			initregionTypes();
		return regionTypes;
	}

	private void initregionTypes() {
		this.regionTypes = new HashMap<RegionType, Integer>();
		int i = 0;
		for (RegionType type : RegionType.values()) {
			regionTypes.put(type, i);
			i++;
		}
	}
}
