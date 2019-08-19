package com.web.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.web.io.FileDatabase;

/**
 * Library of all books 
 * 
 */
public class Library {
	
	private Map<Integer, String> books;
	private List<LibraryEntry> sortedBooks;

	public Library(FileDatabase database) {
		this.books = database.listBooks();
		sortedBooks = new ArrayList<>();
		for(Entry<Integer, String> bookEntry: books.entrySet()) {
			sortedBooks.add(new LibraryEntry(bookEntry.getKey(),bookEntry.getValue()));
		}

		sortedBooks.sort(new Comparator<LibraryEntry>() {
			@Override
			public int compare(LibraryEntry o1, LibraryEntry o2) {
				return o1.getName().compareTo(o2.getName());
			}});
	}
	
	public Map<Integer, String> getBooks() {
		return books;
	}
	
	public List<LibraryEntry> getSortedBooks(){
		return sortedBooks;
	}
}
