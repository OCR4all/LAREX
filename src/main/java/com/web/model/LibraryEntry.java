package com.web.model;

public class LibraryEntry{
	private final int id;
	private final String name;
	public LibraryEntry(int id, String name) {
		this.id = id;
		this.name = name;
	}
	
	public int getId() {
		return id;
	}
	
	public String getName() {
		return name;
	}
}
