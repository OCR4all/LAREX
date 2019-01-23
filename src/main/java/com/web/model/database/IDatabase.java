package com.web.model.database;

import java.util.Map;

import com.web.model.Book;

/**
 * Database interface to handle database operations for the book library
 * 
 */
public interface IDatabase {

	public Map<Integer, Book> getBooks();

	public Book getBook(int id);

	public void addBook(Book book);
}
