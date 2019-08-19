package com.web.controller;

import java.io.File;
import java.io.IOException;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.web.config.LarexConfiguration;
import com.web.io.FileDatabase;
import com.web.io.FilePathManager;
import com.web.model.Book;

import larex.geometry.regions.type.PAGERegionType;

/**
 * Communication Controller to handle requests for the main viewer/editor.
 * Handles requests about displaying book scans and segmentations.
 * 
 */
@Controller
@Scope("request")
public class ViewerController {
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
	 * Open the viewer and display the contents of a book
	 **/
	@RequestMapping(value = "/viewer", method = RequestMethod.GET)
	public String viewer(Model model, @RequestParam(value = "book", required = true) Integer bookID)
			throws IOException {
		if (bookID == null) {
			return "redirect:/404";
		}

		FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
				config.getListSetting("imagefilter"));
		Book book = database.getBook(bookID);

		if (book == null) {
			return "redirect:/404";
		}

		model.addAttribute("book", book);
		model.addAttribute("regionTypes", getRegionTypes());
		model.addAttribute("bookPath", "images/books/");
		model.addAttribute("globalSettings", config);


		return "editor";
	}
	
	/**
	 * Open the viewer with a direct request if direct request is enabled
	 * and display the contents of a selected book.
	 */
	@RequestMapping(value = "/direct", method = RequestMethod.POST)
	public String direct(Model model, @RequestParam(value = "bookpath", required = true) String bookpath,
			@RequestParam(value = "bookname", required = true) String bookname,
			@RequestParam(value = "localsave", required = false) String localsave,
			@RequestParam(value = "savedir", required = false) String savedir,
			@RequestParam(value = "websave", required = false) String websave,
			@RequestParam(value = "imagefilter", required = false) String imagefilter,
			@RequestParam(value = "modes", required = false) String modes) throws IOException {
		if (!config.getSetting("directrequest").equals("enable")) {
			return "redirect:/403";
		}
		if (!new File(bookpath + File.separator + bookname).exists()) {
			return "redirect:/400";
		}
		fileManager.setLocalBooksPath(bookpath);
		int bookID = bookname.hashCode();

		if (localsave != null) {
			config.setSetting("localsave", localsave);
		}
		if (savedir != null) {
			config.setSetting("savedir", savedir);
		}
		if (websave != null) {
			config.setSetting("websave", websave);
		}
		if (imagefilter != null) {
			config.setSetting("imagefilter", imagefilter);
		}
		if (modes != null){
			config.setSetting("modes", modes);
		}
		return viewer(model, bookID);
	}

	/**
	 * Return informations about a book
	 */
	@RequestMapping(value = "/book", method = RequestMethod.POST)
	public @ResponseBody Book getBook(@RequestParam("bookid") int bookID) {
		FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
				config.getListSetting("imagefilter"));

		return database.getBook(bookID);
	}

	private static SortedMap<String, Integer> getRegionTypes() {
		SortedMap<String, Integer> regionTypes = new TreeMap<String, Integer>((c1, c2) -> {
			if (c1.contains("Region") && !c2.contains("Region"))
				return 1;
			else
				return c1.compareTo(c2);
		});

		int i = 0;
		for (PAGERegionType type : PAGERegionType.values()) {
			regionTypes.put(type.toString(), i);
			i++;
		}
		return regionTypes;
	}
}
