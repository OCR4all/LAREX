package com.web.controller;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.web.communication.BasicResponse;
import com.web.communication.SegmentationRequest;
import com.web.config.FileConfiguration;
import com.web.facade.segmentation.LarexFacade;
import com.web.facade.segmentation.SegmentationSettings;
import com.web.io.FileManager;
import com.web.model.Book;
import com.web.model.PageAnnotations;
import com.web.model.database.FileDatabase;

import larex.geometry.regions.type.PAGERegionType;
import larex.segmentation.parameters.ImageSegType;
import larex.segmentation.parameters.Parameters;

/**
 * Communication Controller to handle requests for the main viewer/editor.
 * Handles requests about displaying book scans and segmentations.
 * 
 */
@Controller
@Scope("request")
public class SegmentationController {
	@Autowired
	private ServletContext servletContext;
	@Autowired
	private FileManager fileManager;
	@Autowired
	private FileConfiguration config;

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
	public String viewer(Model model, @RequestParam(value = "book", required = false) Integer bookID)
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
		model.addAttribute("imageSegTypes", getImageregionTypes());
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
	 * Return the information about a book with all default settings 
	 */
	@RequestMapping(value = "/book", method = RequestMethod.POST)
	public @ResponseBody BasicResponse getBook(@RequestParam("bookid") int bookID, @RequestParam("pageid") int pageID) {
		FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
				config.getListSetting("imagefilter"));
		Book book = database.getBook(bookID);
		SegmentationSettings settings =  new SegmentationSettings(new Parameters(), book);

		BasicResponse bookview = new BasicResponse(book, settings);
		return bookview;
	}

	@RequestMapping(value = "/segment", method = RequestMethod.POST, headers = "Accept=*/*", produces = "application/json", consumes = "application/json")
	public @ResponseBody PageAnnotations segment(@RequestBody SegmentationRequest segmentationRequest) {
		FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
				config.getListSetting("imagefilter"));
		return LarexFacade.segmentPage(segmentationRequest.getSettings(), segmentationRequest.getPage(),
				segmentationRequest.isAllowToLoadLocal(), fileManager, database);
	}

	@RequestMapping(value = "/emptysegment", method = RequestMethod.POST)
	public @ResponseBody PageAnnotations emptysegment(@RequestParam("bookid") int bookID,
			@RequestParam("pageid") int pageID) {
		FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
				config.getListSetting("imagefilter"));
		return LarexFacade.emptySegmentPage(bookID, pageID, database);
	}

	@RequestMapping(value = "/segmentedpages", method = RequestMethod.POST)
	public @ResponseBody Collection<Integer> getOnServer(@RequestParam("bookid") int bookID) {
		FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
				config.getListSetting("imagefilter"));
		return database.getSegmentedPageIDs(bookID);
	}

	private SortedMap<String, Integer> getRegionTypes() {
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

	private Map<ImageSegType, String> getImageregionTypes() {
		Map<ImageSegType, String> regionTypes = new TreeMap<ImageSegType, String>();
		regionTypes.put(ImageSegType.NONE, "None");
		regionTypes.put(ImageSegType.CONTOUR_ONLY, "Contour only");
		regionTypes.put(ImageSegType.STRAIGHT_RECT, "Straight rectangle");
		regionTypes.put(ImageSegType.ROTATED_RECT, "Rotated rectangle");
		return regionTypes;
	}

}
