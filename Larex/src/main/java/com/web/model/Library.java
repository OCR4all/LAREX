package com.web.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.web.model.database.IDatabase;

/**
 * Library of all books 
 * 
 */
public class Library {
	
	private Map<Integer, Book> books;
	private List<Book> sortedBooks;

	public Library(IDatabase database) {
		this.books = database.getBooks();
		System.out.println(books.values().size());
		sortedBooks = new ArrayList<>(books.values());
		sortedBooks.sort(new Comparator<Book>() {
			@Override
			public int compare(Book o1, Book o2) {
				return o1.getName().compareTo(o2.getName());
			}});
	}

	public Book getBook(int id) {
		return books.get(id);
	}
	
	public Map<Integer, Book> getBooks() {
		return books;
	}
	
	public List<Book> getSortedBooks(){
		return sortedBooks;
	}
}
