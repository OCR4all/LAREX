package com.web.model;

import java.util.Map;

import com.web.model.database.IDatabase;

/**
 * Library of all books 
 * 
 */
public class Library {
	
	private Map<Integer, Book> books;

	public Library(IDatabase database) {
		this.books = database.getBooks();
	}

	public Book getBook(int id) {
		return books.get(id);
	}
	
	public Map<Integer, Book> getBooks() {
		return books;
	}
}
