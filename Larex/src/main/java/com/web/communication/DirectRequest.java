package com.web.communication;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import com.web.model.Polygon;

import larex.regions.type.RegionType;

/**
 * Direct Request for a book. Possible to request books outside of the Database.
 * Has to be enabled in Larex Config
 * 
 */
public class DirectRequest {

	@JsonProperty("bookpath")
	private String bookpath;
	@JsonProperty("bookname")
	private String bookname;

	@JsonCreator
	public DirectRequest(@JsonProperty("bookpath") String bookpath, @JsonProperty("bookname") String bookname) {
		this.bookpath = bookpath;
		this.bookname = bookname;
	}

	public String getBookpath() {
		return bookpath;
	}

	public String getBookname() {
		return bookname;
	}
}
