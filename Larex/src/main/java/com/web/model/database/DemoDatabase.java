package com.web.model.database;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import com.web.model.Book;
import com.web.model.Page;

/**
 * Static demo database with specific books for the web view
 * 
 */
public class DemoDatabase implements IDatabase{

	private Map<Integer, Book> books;

	public DemoDatabase() {
		this.books = new HashMap<Integer, Book>();

		books.put(0, createAndReturnBook("Book 1", new String[]{"test","test2","test3"}));
		books.put(1, createAndReturnBook("Book 2", new String[]{"test2","test","test3"}));
		books.put(2, createAndReturnBook("Book 3", new String[]{"test2","test3","test"}));
		books.put(3, createAndReturnBook("Book 4", new String[]{"test3","test2","test"}));
		books.put(4, createAndReturnBook("Book 5", new String[]{"test3","test","test2"}));
	}

	public Map<Integer, Book> getBooks() {
		return books;
	}

	public Book getBook(int id) {
		return books.get(id);
	}

	public void addBook(Book book) {
		books.put(book.getId(), book);
	}

	private Book createAndReturnBook(String bookname, String[] pageImages){
		LinkedList<Page> pages = new LinkedList<Page>();
		int pageCounter = 0;
		for(String pageImage: pageImages){
			pages.add(new Page(pageCounter,pageImage));
			pageCounter++;
		}
		
		return new Book(books.size(), bookname, pages);
	}
}
