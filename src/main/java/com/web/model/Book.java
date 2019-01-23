package com.web.model;

import java.util.LinkedList;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Representation of a Book with a name and pages.
 * (Settings and Segmentation of a book are handled 
 * in BookSettings and BookSegmentation respectively)
 * 
 */
public class Book {

	@JsonProperty("id")
	protected int id;
	@JsonProperty("name")
	protected String name;
	@JsonProperty("pages")
	protected LinkedList<Page> pages;

	@JsonCreator
	public Book(@JsonProperty("id") int id, @JsonProperty("name") String name,
			@JsonProperty("pages") LinkedList<Page> pages) {
		this.id = id;
		this.pages = pages;
		this.name = name;
	}

	public Book(int id, String name) {
		this(id, name, new LinkedList<Page>());
	}

	public void addPage(Page page) {
		pages.add(page);
	}

	// pagenumber starting with 0. Lower than 0 or higher than maxpages =>
	// page(0)
	public Page getPage(int pagenumber) {
		if (pages.size() < 1) {
			return null;
		} else if (pagenumber >= 0 || pagenumber < pages.size()) {
			return pages.get(pagenumber);
		} else {
			return pages.get(0);
		}
	}

	public int getId() {
		return id;
	}

	public LinkedList<Page> getPages() {
		return pages;
	}

	public String getName() {
		return name;
	}
}
