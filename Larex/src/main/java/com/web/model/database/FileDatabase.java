package com.web.model.database;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.web.model.Book;
import com.web.model.Page;

/**
 * Static demo database with specific books for the web view
 * 
 */
public class FileDatabase implements IDatabase {

	private Map<Integer, Book> books;
	private File databaseFolder;
	private List<String> supportedFileExtensions;

	public FileDatabase(File databaseFolder, List<String> supportedFileExtensions) {
		this.databaseFolder = databaseFolder;
		this.books = new HashMap<Integer, Book>();
		this.supportedFileExtensions = new ArrayList<String>(supportedFileExtensions);
	}

	public FileDatabase(File databaseFolder) {
		this(databaseFolder, Arrays.asList("png", "jpg", "jpeg", "tif", "tiff"));
	}

	public Map<Integer, Book> getBooks() {
		File[] files = databaseFolder.listFiles();

		// sort book files/folders
		ArrayList<File> sortedFiles = new ArrayList<File>(Arrays.asList(files));
		// sortedFiles.sort((f1,f2) -> f1.getName().compareTo(f2.getName()));
		// //ArrayOutOfBounds exception ?
		sortedFiles.sort(new FileNameComparator());// Because lambda throws
													// exception

		for (File bookFile : sortedFiles) {
			if (bookFile.isDirectory()) {
				int bookHash = bookFile.getName().hashCode();
				Book book = readBook(bookFile, bookHash);
				books.put(bookHash, book);
			}
		}
		return books;
	}

	public Book getBook(int id) {
		if (books == null || !books.containsKey(id)) {
			getBooks();
		}
		return books.get(id);
	}

	public void addBook(Book book) {
		books.put(book.getId(), book);
	}

	private Book readBook(File bookFile, int bookID) {
		String bookName = bookFile.getName();

		LinkedList<Page> pages = new LinkedList<Page>();
		int pageCounter = 0;

		ArrayList<File> sortedFiles = new ArrayList<File>(Arrays.asList(bookFile.listFiles()));

		// Because lambda throws exception
		sortedFiles.sort(new FileNameComparator());
		for (File pageFile : sortedFiles) {
			if (pageFile.isFile()) {
				String pageName = pageFile.getName();

				if (isValidImageFile(pageName)) {
					int width = 0;
					int height = 0;

					try {
						ImageInputStream in = ImageIO.createImageInputStream(pageFile);
						final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
						if (readers.hasNext()) {
							ImageReader reader = readers.next();
							reader.setInput(in);
							width = reader.getWidth(0);
							height = reader.getHeight(0);

							reader.dispose();
						}
					} catch (IOException e) {
						e.printStackTrace();
					}

					pages.add(new Page(pageCounter, bookName + File.separator + pageName, width, height));
					pageCounter++;
				}
			}
		}

		Book book = new Book(bookID, bookName, pages);
		return book;
	}

	private Boolean isValidImageFile(String filepath) {
		String[] extensionArray = filepath.split("\\.");
		if (extensionArray.length > 0) {
			String extension = extensionArray[extensionArray.length - 1];
			for (String supportedExtension : supportedFileExtensions) {
				if (extension.equals(supportedExtension)) {
					return true;
				}
			}
		}
		return false;
	}

	private class FileNameComparator implements Comparator<File> {
		@Override
		public int compare(File o1, File o2) {
			return o1.getName().compareTo(o2.getName());
		}
	}
}