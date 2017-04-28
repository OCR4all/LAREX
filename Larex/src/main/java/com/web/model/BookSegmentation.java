package com.web.model;

import java.util.HashMap;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

/**
 * A collection of all segmentation polygones of each page of a book 
 * 
 */
public class BookSegmentation {

	@JsonProperty("book")
	private int book;
	@JsonProperty("pages")
	private HashMap<Integer,PageSegmentation> pages;

	@JsonCreator
	public BookSegmentation(@JsonProperty("book") int book, @JsonProperty("pages") HashMap<Integer,PageSegmentation> pages) {
		this.book = book;
		this.pages = pages;
	}

	public BookSegmentation(int book) {
		this(book, new HashMap<Integer,PageSegmentation>());
	}

	@Deprecated
	public void addPage(PageSegmentation page) {
		//pages.add(page);
	}

	public void setPage(PageSegmentation page, int pageID){
		pages.put(pageID, page);
	}
	
	public HashMap<Integer,PageSegmentation> getPages() {
		return new HashMap<Integer,PageSegmentation>(pages);
	}

	public int getBook() {
		return book;
	}
}
