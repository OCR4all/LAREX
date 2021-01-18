package de.uniwue.web.io;

import java.io.File;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.opencv.core.Size;

import de.uniwue.web.model.Book;
import de.uniwue.web.model.Page;

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
 * everything but 0001.bin.png and treat 0001.bin.png as 0001.png.
 * (will load and save 0001.xml etc.)
 * Multiple subextensions can be combined and images with the same base name will
 * then be grouped together.
 * ("." is a substitute for "no subextension")
 */
public class FileDatabase {

	private Map<Integer, File> books;
	private File databaseFolder;
	private List<String> supportedFileExtensions;
	private List<String> imageSubFilter;
	private Boolean isFlat;

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
	 * @param imageSubFilter     file extensions that are to be filtered
	 */
	public FileDatabase(File databaseFolder, List<String> supportedFileExtensions, List<String> imageSubFilter, Boolean isFlat) {
		this.databaseFolder = databaseFolder;
		this.books = new HashMap<Integer, File>();
		this.supportedFileExtensions = new ArrayList<String>(supportedFileExtensions);
		this.imageSubFilter = new ArrayList<String>(imageSubFilter);
		this.isFlat = isFlat;
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
	 * @param imageSubFilter     file extensions that are to be filtered
	 */
	public FileDatabase(File databaseFolder, List<String> imageSubFilter, Boolean isFlat) {
		this(databaseFolder, Arrays.asList("png", "jpg", "jpeg", "tif", "tiff"), imageSubFilter, isFlat);
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
	public FileDatabase(File databaseFolder, Boolean isFlat) {
		this(databaseFolder, new ArrayList<>(), isFlat);
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
		assert files != null;
		ArrayList<File> sortedFiles = new ArrayList<File>(Arrays.asList(files));
		sortedFiles.sort(Comparator.comparing(File::getName));

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
	 * Load a book object via Map.
	 *
	 * @param bookName name of book
	 * @param bookID Identifier of the book to load
	 * @param imageMap map of images from book
	 * @return Loaded book
	 */
	public Book getBook(String bookName, Integer bookID, Map<String, String> imageMap) {
		return readBook(bookName,imageMap, bookID);
	}
	
	/**
	 * Retrieve name of a book without loading it into ram
	 * 
	 * @param id Identifier of the book
	 * @return Name of the book
	 */
	public String getBookName(int id) {
		if (books == null || !books.containsKey(id)) {
			listBookFiles();
		}
		return books.get(id).getName();
	}

	/**
	 * Get the IDs of all book pages, for which a segmentation file exists.
	 * 
	 * @param bookID Identifier for the book of which pages are to be checked
	 * @return Collection of all book pages in the selected book with a segmentation
	 *         file
	 */
	public Collection<Integer> getPagesWithAnnotations(int bookID) {
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
	 * Get the IDs of all book pages, for which a segmentation file exists.
	 *
	 * @param bookID Identifier for the book of which pages are to be checked
	 * @return Collection of all book pages in the selected book with a segmentation
	 *         file
	 */
	public Collection<Integer> getPagesWithAnnotations(String bookName, Integer bookID, Map<String, String> imageMap, Map<String, String> xmlMap) {
		Collection<Integer> segmentedIds = new HashSet<>();
		try {
			Book book = getBook(bookName,bookID,imageMap);
			for (Page page : book.getPages()) {
				String key = page.getName() + ".xml";

				if (xmlMap.get(key) != null && new File(xmlMap.get(key)).exists())
					segmentedIds.add(page.getId());
			}
		} catch (Exception e) {
			e.printStackTrace();
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

		if(imageSubFilter.isEmpty()) {
			// Interpret every image as its own page
			List<File> imageFiles = Arrays.stream(Objects.requireNonNull(bookFile.listFiles()))
					.filter(this::isSupportedImage).sorted(Comparator.comparing(File::getName)).collect(Collectors.toList());

			for(File imageFile: imageFiles) {
				Size imageSize = ImageLoader.readDimensions(imageFile);
				String name = removeAllExtensions(imageFile.getName());
				String imageURL = bookName + File.separator + imageFile.getName();
				pages.add(new Page(pageCounter++, name, Collections.singletonList(imageURL), (int) imageSize.width, (int) imageSize.height));
			}
			
		} else {
			// Combine images with the same base name and different (sub)extensions
			Map<String, List<File>> imageFiles = Arrays.stream(Objects.requireNonNull(bookFile.listFiles()))
					.filter(f -> isSupportedImage(f) && (imageSubFilter.isEmpty() || passesSubFilter(f.getName())))
					.collect(Collectors.groupingBy(f -> removeAllExtensions(f.getName())));
			
			ArrayList<String> sortedPages = new ArrayList<>(imageFiles.keySet());
			sortedPages.sort(String::compareTo);
			
			for (String pageName : sortedPages) {
				Map<String, List<File>> groupedImages = imageFiles.get(pageName).stream()
						.collect(Collectors.groupingBy(f -> extractSubExtension(f.getName())));
				List<String> images = new ArrayList<>();
				
				Size imageSize = null;
				for(String subExtension: imageSubFilter) {
					List<File> subImages = !subExtension.equals(".") ? groupedImages.get(subExtension)
												: groupedImages.get("");
					
					if(subImages != null) {
						for(File subImage: subImages) {
							if(imageSize == null) {
								imageSize = ImageLoader.readDimensions(subImage);
							}
							images.add(bookName + File.separator + subImage.getName());
						}
					}
				}

				assert imageSize != null;
				pages.add(new Page(pageCounter++, pageName, images, (int) imageSize.width, (int) imageSize.height));
			}
		}

		return new Book(bookID, bookName, pages);
	}

	/**
	 * Read contents of a book from the folder structure.
	 *
	 * @param bookName name of book
	 * @param imagemap map of all book images
	 * @param bookID   Identifier that is to be used for the loaded book
	 * @return
	 */
	private Book readBook(String bookName, Map<String, String> imagemap, int bookID) {
		try{
			LinkedList<Page> pages = new LinkedList<Page>();
			int pageCounter = 0;

			if(imageSubFilter.isEmpty()) {
				// Interpret every image as its own page
				for(String imgPath: imagemap.values()) {
					File imageFile = new File(imgPath);
					Size imageSize = ImageLoader.readDimensions(imageFile);
					String name = removeAllExtensions(imageFile.getName());
					if(isSupportedImage(imageFile)) {
						pages.add(new Page(pageCounter++, name, Collections.singletonList(imgPath), (int) imageSize.width, (int) imageSize.height));
					}
				}
			} else {
				// Combine images with the same base name and different (sub)extensions
				List<File> imageFileList = new ArrayList<File>();
				for( String imgPath : imagemap.values()) { imageFileList.add(new File(imgPath)); }
				File[] fileArray = imageFileList.toArray(new File[imageFileList.size()]);
				Map<String, List<File>> imageFiles = Arrays.stream(Objects.requireNonNull(fileArray))
						.filter(f -> isSupportedImage(f) && (imageSubFilter.isEmpty() || passesSubFilter(f.getName())))
						.collect(Collectors.groupingBy(f -> removeAllExtensions(f.getName())));

				ArrayList<String> sortedPages = new ArrayList<>(imageFiles.keySet());
				sortedPages.sort(String::compareTo);

				for (String pageName : sortedPages) {
					Map<String, List<File>> groupedImages = imageFiles.get(pageName).stream()
							.collect(Collectors.groupingBy(f -> extractSubExtension(f.getName())));
					List<String> images = new ArrayList<>();

					Size imageSize = null;
					for(String subExtension: imageSubFilter) {
						List<File> subImages = !subExtension.equals(".") ? groupedImages.get(subExtension)
								: groupedImages.get("");

						if(subImages != null) {
							for(File subImage: subImages) {
								if(imageSize == null) {
									imageSize = ImageLoader.readDimensions(subImage);
								}
								images.add(subImage.getParentFile().getName() + File.separator + subImage.getName());
							}
						}
					}

					assert imageSize != null;
					pages.add(new Page(pageCounter++, pageName, images, (int) imageSize.width, (int) imageSize.height));
				}
			}

			return new Book(bookID, bookName, pages);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Check if a file is a supported image file
	 * 
	 * @param filepath File to be checked
	 * @return True if is supported, else False
	 */
	private Boolean isSupportedImage(File filepath) {
		if (filepath.isFile()) {
			String extension = FilenameUtils.getExtension(filepath.toString());
			return supportedFileExtensions.contains(extension);
		}
		return false;
	}

	/**
	 * Check if a file has a valid sub extension, for the filtering or
	 * has none while the filter contains "."
	 * 
	 * @param filepath File to be checked
	 * @return True if has valid sub extension, else False
	 */
	private Boolean passesSubFilter(String filepath) {
		final String extension = extractSubExtension(filepath);
		if (extension.equals("") && imageSubFilter.contains(".")) {
			return true;
		} else {
			return imageSubFilter.contains(extension);
		}
	}

	/**
	 * Extract the sub extension
	 * 
	 * @param filepath File to extract from
	 * @return sub extension
	 */
	private String extractSubExtension(String filepath) {
		String[] extensionArray = filepath.split("\\.");
		if (extensionArray.length > 2) {
			return extensionArray[extensionArray.length - 2];
		}
		return "";
	}
	
	/**
	 * Remove all extensions from a file name. e.g. 0001.bin.png = 0001
	 * 
	 * @param filename File name to be cleaned
	 * @return File name without extensions
	 */
	private String removeAllExtensions(String filename) {
		if (passesSubFilter(filename)) {
			final int extPointPos = filename.lastIndexOf(".");
			final int subExtPointPos = filename.lastIndexOf(".", extPointPos - 1);
			if(subExtPointPos > 0)
				return filename.substring(0, subExtPointPos);
			else
				return filename.substring(0, extPointPos);
		} else {
			final int extensionPointer = filename.lastIndexOf(".");
			if(extensionPointer > 0)
				return filename.substring(0, extensionPointer);

		}
		return filename;
	}
}