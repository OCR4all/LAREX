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
 * File Database for loading book data from a folder structure starting by a
 * databaseFolder. Every folder contained in the databaseFolder is seen as a
 * single book with images representing its pages and xml files representing a
 * segmentation for that page.
 * 
 * @formatter:off
 * databaseFolder/
 * ├── <book_name>/ 
 * │    ├── <page_name>.png 
 * │    └── <page_name>.xml
 * └── <book2_name>/
 *      └── …
 * @formatter:on
 * 
 * Page images can be filtered by subExtensions.
 * <page_name>.<sub_extension>.<image_extension>
 * e.g. 0001.png 0001.bin.png, 0001.nrm.png with the filter "bin" will ignore 
 * everything but 0001.bin.png and treat 0001.bin.png as 0001.png 
 * (will load and save 0001.xml etc.)
 */
public class FileDatabase {

	private Map<Integer, File> books;
	private File databaseFolder;
	private List<String> supportedFileExtensions;
	private List<String> filterSubExtensions;

	/**
	 * Initialize a FileDatabase with its root databaseFolder, all supported image
	 * file extensions and filtered sub extensions.
	 * 
	 * @formatter:off
	 * databaseFolder/
	 * ├── <book_name>/ 
	 * └── <book2_name>/
	 * └── ...
	 * @formatter:on
	 * 
	 * 
	 * Filtering:
	 * 
	 * <page_name>.<sub_extension>.<image_extension>
	 * 
	 * e.g. 0001.png, 0001.bin.png, 0001.nrm.png with the filter "bin" will ignore
	 * everything but 0001.bin.png and treat 0001.bin.png as 0001.png (will load and
	 * save 0001.xml etc.)
	 * 
	 * @param databaseFolder          Root database folder containing all books
	 * @param supportedFileExtensions supported image types to load
	 * @param filterSubExtensions     file extensions that are to be filtered
	 */
	public FileDatabase(File databaseFolder, List<String> supportedFileExtensions, List<String> filterSubExtensions) {
		this.databaseFolder = databaseFolder;
		this.books = new HashMap<Integer, File>();
		this.supportedFileExtensions = new ArrayList<String>(supportedFileExtensions);
		this.filterSubExtensions = new ArrayList<String>(filterSubExtensions);
	}

	/**
	 * Initialize a FileDatabase with its root databaseFolder and filtered sub extensions.
	 * 
	 * @formatter:off
	 * databaseFolder/
	 * ├── <book_name>/ 
	 * └── <book2_name>/
	 * └── ...
	 * @formatter:on
	 * 
	 * 
	 * Filtering:
	 * 
	 * <page_name>.<sub_extension>.<image_extension>
	 * 
	 * e.g. 0001.png 0001.bin.png, 0001.nrm.png with the filter "bin" will ignore
	 * everything but 0001.bin.png and treat 0001.bin.png as 0001.png (will load and
	 * save 0001.xml etc.)
	 * 
	 * @param databaseFolder          Root database folder containing all books
	 * @param filterSubExtensions     file extensions that are to be filtered
	 */
	public FileDatabase(File databaseFolder, List<String> filterSubExtensions) {
		this(databaseFolder, Arrays.asList("png", "jpg", "jpeg", "tif", "tiff"), filterSubExtensions);
	}

	/**
	 * Initialize a FileDatabase with its root databaseFolder.
	 * 
	 * @formatter:off
	 * databaseFolder/
	 * ├── <book_name>/ 
	 * └── <book2_name>/
	 * └── ...
	 * @formatter:on
	 * 
	 * @param databaseFolder          Root database folder containing all books
	 */
	public FileDatabase(File databaseFolder) {
		this(databaseFolder, new ArrayList<>());
	}

	/**
	 * List all books by their id and name.
	 * 
	 * @return Map of <id> -> <book_name>
	 */
	public Map<Integer, String> listBooks() {
		Map<Integer, String> booknames = new HashMap<>();

		// Extract book names from book files
		for (Entry<Integer, File> bookEntry : listBookFiles().entrySet()) {
			booknames.put(bookEntry.getKey(), bookEntry.getValue().getName());
		}
		return booknames;
	}

	/**
	 * List all book files by their id.
	 * 
	 * @return Map of <id> -> <book_file>
	 */
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

	/**
	 * Load a book object via its id.
	 * 
	 * @param id Identifier of the book to load
	 * @return Loaded book
	 */
	public Book getBook(int id) {
		if (books == null || !books.containsKey(id)) {
			listBookFiles();
		}
		File bookFile = books.get(id);

		return readBook(bookFile, id);
	}

	/**
	 * Get the IDs of all book pages, for which a segmentation file exists.
	 * 
	 * @param bookID Identifier for the book of which pages are to be checked
	 * @return Collection of all book pages in the selected book with a segmentation
	 *         file
	 */
	public Collection<Integer> getSegmentedPageIDs(int bookID) {
		Collection<Integer> segmentedIds = new HashSet<>();

		Book book = getBook(bookID);
		for (Page page : book.getPages()) {
			String xmlPath = databaseFolder.getPath() + File.separator + book.getName() + File.separator
					+ page.getName() + ".xml";
			if (new File(xmlPath).exists())
				segmentedIds.add(page.getId());
		}
		return segmentedIds;
	}

	/**
	 * Read contents of a book from the folder structure.
	 * 
	 * @param bookFile File pointing to the folder of the book that is to be loaded
	 * @param bookID   Identifier that is to be used for the loaded book
	 * @return
	 */
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

				if (hasSupportedImageFile(fileName)
						&& (filterSubExtensions.isEmpty() || hasValidSubExtension(fileName))) {
					int width = 0;
					int height = 0;

					try (ImageInputStream in = ImageIO.createImageInputStream(pageFile)) {
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

	/**
	 * Check if a file is a supported image file
	 * 
	 * @param filepath File to be checked
	 * @return True if is supported, else False
	 */
	private Boolean hasSupportedImageFile(String filepath) {
		String[] extensionArray = filepath.split("\\.");
		if (extensionArray.length > 0) {
			String extension = extensionArray[extensionArray.length - 1];
			return supportedFileExtensions.contains(extension);
		}
		return false;
	}

	/**
	 * Check if a file has a valid sub extension, for the filtering
	 * 
	 * @param filepath File to be checked
	 * @return True if has valid sub extension, else False
	 */
	private Boolean hasValidSubExtension(String filepath) {
		String[] extensionArray = filepath.split("\\.");
		if (extensionArray.length > 1) {
			String extension = extensionArray[extensionArray.length - 2];
			return filterSubExtensions.contains(extension);
		}
		return false;
	}

	/**
	 * Remove a sub extension from a file name. e.g. 0001.bin.png = 0001.png
	 * 
	 * @param filename File name to be cleaned
	 * @return File name without sub extension
	 */
	private String removeSubExtension(String filename) {
		if (hasValidSubExtension(filename)) {
			int extPointPos = filename.lastIndexOf(".");
			int subExtPointPos = filename.lastIndexOf(".", extPointPos - 1);
			return filename.substring(0, subExtPointPos) + filename.substring(extPointPos);
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