package com.web.model.database;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import com.web.model.Book;
import com.web.model.Page;

/**
 * Static demo database with specific books for the web view
 * 
 */
public class FileDatabase implements IDatabase{

	private Map<Integer, Book> books;
	private File databaseFolder;
	
	public FileDatabase(File databaseFolder) {
		this.databaseFolder = databaseFolder;
		this.books = new HashMap<Integer, Book>();
	}

	public Map<Integer, Book> getBooks() {
		File[] files = databaseFolder.listFiles();
		if(files != null){
			for(File bookFile: files){
				if(bookFile.isDirectory()){
					int bookHash = bookFile.getName().hashCode();
					Book book = readBook(bookFile, bookHash);
					books.put(bookHash, book);
				}
			}
		}
		return books;
	}

	public Book getBook(int id) {
		if(books == null || !books.containsKey(id)){
			getBooks();
		}
		return books.get(id);
	}

	public void addBook(Book book) {
		books.put(book.getId(), book);
	}

	private Book readBook(File bookFile, int bookID){
		String bookName = bookFile.getName();
		
		LinkedList<Page> pages = new LinkedList<Page>();
		int pageCounter = 0;
		for(File pageFile: bookFile.listFiles()){
			if(pageFile.isFile()){
				String pageName = pageFile.getName();
				pages.add(new Page(pageCounter, bookName + File.separator + pageName));
				pageCounter++;
			}
		}
		
		Book book = new Book(bookID,bookName,pages);
		return book;
	}
}
