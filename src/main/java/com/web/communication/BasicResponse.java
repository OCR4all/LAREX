package com.web.communication;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.web.facade.BookSettings;
import com.web.model.Book;

import larex.geometry.regions.type.PAGERegionType;

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
	protected Map<String, Integer> regionTypes;

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

	public Map<String, Integer> getregionTypes() {
		if (regionTypes == null)
			initregionTypes();
		return regionTypes;
	}

	private void initregionTypes() {
		this.regionTypes = new HashMap<String, Integer>();
		int i = 0;
		for (PAGERegionType type : PAGERegionType.values()) {
			regionTypes.put(type.toString(), i);
			i++;
		}
	}
}
