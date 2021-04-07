package de.uniwue.web.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import de.uniwue.web.io.FileDatabase;

/**
 * Library of all books. List of all books in the bookPath with their id (key) and name (value).
 */
public class Library {
	
	private final Map<Integer, List<String>> books;
	private final List<Entry<Integer,List<String>>> sortedBooks;

	public Library(FileDatabase database) {
		this.books = database.listBooks();
		sortedBooks = new ArrayList<>();
		sortedBooks.addAll(books.entrySet());

		sortedBooks.sort(Entry.comparingByKey());
	}
	
	/**
	 * Retrieve all books in a map of "id -> name"
	 * 
	 * @return book map of all books in the bookPath
	 */
	public Map<Integer, List<String>> getBooks() {
		return books;
	}
	
	/**
	 * Retrieve a list of all books with id and name, sorted in lexical order.
	 *  
	 * @return sorted list of all books in the bookPath
	 */
	public List<Entry<Integer,List<String>>> getSortedBooks(){
		return sortedBooks;
	}
}
