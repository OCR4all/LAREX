
package com.web.model;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import larex.regions.type.RegionType;
import larex.segmentation.parameters.ImageSegType;

/**
 * Handels all parameters and settings passing through the gui to the
 * segmentation algorithm
 * 
 */
public class BookSettings {

	@JsonProperty("book")
	protected int bookID;
	@JsonProperty("pages")
	private LinkedList<PageSettings> pages;
	@JsonProperty("parameters")
	private Map<String, Integer> parameters;
	@JsonProperty("regions")
	protected Map<RegionType, Region> regions;
	@JsonProperty("global")
	protected PageSettings globalSettings;
	@JsonProperty("combine")
	protected boolean combine;
	@JsonProperty("imageSegType")
	private ImageSegType imageSegType;

	@JsonCreator
	public BookSettings(@JsonProperty("book") int bookID, @JsonProperty("pages") LinkedList<PageSettings> pages,
			@JsonProperty("parameters") Map<String, Integer> parameters,
			@JsonProperty("regions") Map<RegionType, Region> regions,
			@JsonProperty("global") PageSettings globalSettings, @JsonProperty("combine") boolean combine,
			@JsonProperty("imageSegType") ImageSegType imageSegType) {
		this.bookID = bookID;
		this.globalSettings = globalSettings;
		this.regions = regions;
		this.pages = pages;
		this.parameters = parameters;
		this.combine = combine;
		this.imageSegType = imageSegType;
	}

	public BookSettings(Book book) {
		this.bookID = book.getId();
		this.globalSettings = new PageSettings(book.getId());
		this.regions = new HashMap<RegionType, Region>();
		pages = new LinkedList<PageSettings>();
		for (Page page : book.getPages()) {
			pages.add(new PageSettings(page.getId()));
		}
		this.parameters = new HashMap<String, Integer>();
	}

	public void addRegion(Region region) {
		regions.put(region.getType(), region);
	}

	public Map<RegionType, Region> getRegions() {
		return new HashMap<RegionType, Region>(regions);
	}

	public void addPage(PageSettings page) {
		pages.add(page);
	}

	public LinkedList<PageSettings> getPages() {
		return new LinkedList<PageSettings>(pages);
	}

	public PageSettings getPage(int pageNr) {
		return pages.get(pageNr);
	}

	public Map<String, Integer> getParameters() {
		return parameters;
	}

	public int getBookID() {
		return bookID;
	}

	public PageSettings getGlobalSettings() {
		return globalSettings;
	}

	public ImageSegType getImageSegType() {
		return imageSegType;
	}

	public boolean isCombine() {
		return combine;
	}

	public void setImageSegType(ImageSegType imageSegType) {
		this.imageSegType = imageSegType;
	}

	public void setCombine(boolean combine) {
		this.combine = combine;
	}
}
