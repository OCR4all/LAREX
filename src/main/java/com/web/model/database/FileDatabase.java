package com.web.model.database;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.web.model.Book;
import com.web.model.Page;

/**
 * File Database to load book folders 
 */
public class FileDatabase implements IDatabase {

	private Map<Integer, File> books;
	private File databaseFolder;
	private List<String> supportedFileExtensions;
	private List<String> filterSubExtensions;

	public FileDatabase(File databaseFolder, List<String> supportedFileExtensions, List<String> filterSubExtensions) {
		this.databaseFolder = databaseFolder;
		this.books = new HashMap<Integer, File>();
		this.supportedFileExtensions = new ArrayList<String>(supportedFileExtensions);
		this.filterSubExtensions = new ArrayList<String>(filterSubExtensions);
	}

	public FileDatabase(File databaseFolder, List<String> filterSubExtensions) {
		this(databaseFolder, Arrays.asList("png", "jpg", "jpeg", "tif", "tiff"), filterSubExtensions);
	}
	
	public FileDatabase(File databaseFolder) {
		this(databaseFolder, new ArrayList<>());
	}
	
	@Override
	public Map<Integer, String> listBooks() {
		Map<Integer, String> booknames = new HashMap<>();

		// Extract book names from book files
		for(Entry<Integer, File> bookEntry: listBookFiles().entrySet()) {
			booknames.put(bookEntry.getKey(), bookEntry.getValue().getName());
		}
		return booknames;
	}
	
	private Map<Integer, File> listBookFiles() {
		File[] files = databaseFolder.listFiles();

		// sort book files/folders
		ArrayList<File> sortedFiles = new ArrayList<File>(Arrays.asList(files));
		sortedFiles.sort(new FileNameComparator());// Because lambda throws exception

		for (File bookFile : sortedFiles) {
			if (bookFile.isDirectory()) {
				int bookHash = bookFile.getName().hashCode();
				books.put(bookHash, bookFile);
			}
		}
		return books;
	}

	@Override
	public Book getBook(int id) {
		if (books == null || !books.containsKey(id)) {
			listBookFiles();
		}
		File bookFile = books.get(id);
		
		return readBook(bookFile,id);
	}
	
	public Collection<Integer> getSegmentedPageIDs(int bookID){
		Collection<Integer> segmentedIds = new HashSet<>();
		
		Book book = getBook(bookID);
		for(Page page : book.getPages()){
			String xmlPath = databaseFolder.getPath() + File.separator + book.getName() + File.separator + page.getName()+ ".xml";
			if(new File(xmlPath).exists()) 
				segmentedIds.add(page.getId());
		}
		return segmentedIds;
	}

	@Override
	public void addBook(Book book) {
		throw new UnsupportedOperationException();
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
				String fileName = pageFile.getName();

				if (isValidImageFile(fileName) && 
						(filterSubExtensions.isEmpty() || hasValidSubExtension(fileName))) {
					int width = 0;
					int height = 0;

					try ( ImageInputStream in = ImageIO.createImageInputStream(pageFile) ){
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

					String pagePath = bookName + File.separator + fileName;
					String pageName = filterSubExtensions.isEmpty() ? fileName : removeSubExtension(fileName); 

					pages.add(new Page(pageCounter, pageName, pagePath, width, height));
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
			return supportedFileExtensions.contains(extension);
		}
		return false;
	}
	
	private Boolean hasValidSubExtension(String filepath) {
		String[] extensionArray = filepath.split("\\.");
		if (extensionArray.length > 1) {
			String extension = extensionArray[extensionArray.length - 2];
			return filterSubExtensions.contains(extension);
		}
		return false;
	}

	private String removeSubExtension(String filename) {
		if(hasValidSubExtension(filename)) {
			int extPointPos = filename.lastIndexOf(".");
			int subExtPointPos = filename.lastIndexOf(".",extPointPos-1);
			return filename.substring(0,subExtPointPos) + filename.substring(extPointPos);
		}
		return filename;
	}
	
	private class FileNameComparator implements Comparator<File> {
		@Override
		public int compare(File o1, File o2) {
			return o1.getName().compareTo(o2.getName());
		}
	}
}