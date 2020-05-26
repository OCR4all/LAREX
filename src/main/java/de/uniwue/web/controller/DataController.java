package de.uniwue.web.controller;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import de.uniwue.web.config.LarexConfiguration;
import de.uniwue.web.io.FileDatabase;
import de.uniwue.web.io.FilePathManager;
import de.uniwue.web.io.PageXMLReader;
import de.uniwue.web.model.Book;
import de.uniwue.web.model.Page;
import de.uniwue.web.model.PageAnnotations;

/**
 * Communication Controller to handle project data requests.
 * Provide information about books, pages and more.
 */
@Controller
@Scope("request")
public class DataController {
	@Autowired
	private ServletContext servletContext;
	@Autowired
	private FilePathManager fileManager;
	@Autowired
	private LarexConfiguration config;

	/**
	 * Initialize the controller by loading the fileManager and settings if not
	 * loaded already.
	 **/
	@PostConstruct
	private void init() {
		if (!fileManager.isInit()) {
			fileManager.init(servletContext);
		}
		if (!config.isInitiated()) {
			config.read(new File(fileManager.getConfigurationFile()));
			String bookFolder = config.getSetting("bookpath");
			if (!bookFolder.equals("")) {
				fileManager.setLocalBooksPath(bookFolder);
			}
		}
	}
	
	/**
	 * Return informations about a book
	 * 
	 * @param bookID
	 * @return
	 */
	@RequestMapping(value = "data/book", method = RequestMethod.POST)
	public @ResponseBody Book getBook(@RequestParam("bookid") int bookID) {
		FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
				config.getListSetting("imagefilter"));

		return database.getBook(bookID);
	}

	/**
	 * Return the annotations of a page if exists or empty annotations 
	 *  
	 * @param bookID
	 * @param pageID
	 * @return
	 */
	@RequestMapping(value = "data/page/annotations", method = RequestMethod.POST)
	public @ResponseBody PageAnnotations getAnnotations(@RequestParam("bookid") int bookID, @RequestParam("pageid") int pageID) {
		FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
				config.getListSetting("imagefilter"));

		final Book book = database.getBook(bookID);
		final Page page = book.getPage(pageID);
		final File annotationsPath = fileManager.getAnnotationPath(book.getName(), page.getName());

		if (annotationsPath.exists()) {
			return PageXMLReader.loadPageAnnotationsFromDisc(annotationsPath);
		} else {
			return new PageAnnotations(page.getName(), page.getWidth(), page.getHeight(),
					page.getId());
		}
	}

	/**
	 * Return if page annotations for the pages of a book exist
	 * 
	 * @param bookID
	 * @return Map of PageNr -> Boolean : True if annotations file exist on the server
	 */
	@RequestMapping(value = "data/status/all/annotations", method = RequestMethod.POST)
	public @ResponseBody Collection<Integer> getAnnotationAllStatus(@RequestParam("bookid") int bookID) {
		FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
				config.getListSetting("imagefilter"));
		return database.getPagesWithAnnotations(bookID);
	}

	/**
	 * Retrieve the default virtual keyboard.
	 * 
	 * @param file
	 * @param bookID
	 * @return
	 */
	@RequestMapping(value = "data/virtualkeyboard", method = RequestMethod.POST)
	public @ResponseBody List<String[]> virtualKeyboard() {
		File virtualKeyboard = new File(fileManager.getVirtualKeyboardFile());

		List<String[]> keyboard = new ArrayList<>();
		try(BufferedReader br = new BufferedReader(new FileReader(virtualKeyboard))) {
			String st; 
			while ((st = br.readLine()) != null) 
				if(st.replace("\\s+", "").length() > 0) 
					keyboard.add(st.split("\\s+"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} 
		return keyboard;
	}

	/**
	 * Retrieve a preset virtual keyboard.
	 *
	 * @param language
	 * @return vk
	 */
	@RequestMapping(value = "data/virtualkeyboardPreset", method = RequestMethod.POST)
	public @ResponseBody List<String[]> virtualKeyboardPreset(String language) {
		File virtualKeyboard = new File(fileManager.getVirtualKeyboardFile(language));

		List<String[]> keyboard = new ArrayList<>();
		try(BufferedReader br = new BufferedReader(new FileReader(virtualKeyboard))) {
			String st;
			while ((st = br.readLine()) != null)
				if(st.replace("\\s+", "").length() > 0)
					keyboard.add(st.split("\\s+"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return keyboard;
	}

	/**
	 * Returns whether LAREX is configured to be used in conjunction with OCR4all or not.
	 */
	@RequestMapping(value = "config/ocr4all", method = RequestMethod.POST, headers = "Accept=*/*",
			produces = "application/json")
	public @ResponseBody Boolean isOCR4allMode() {
		String ocr4allMode = config.getSetting("ocr4all");
		return ocr4allMode.equals("enable");
	}
}