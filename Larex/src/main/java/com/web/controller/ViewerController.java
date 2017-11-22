package com.web.controller;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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

import com.web.communication.FullBookResponse;
import com.web.communication.SegmentationRequest;
import com.web.config.FileConfiguration;
import com.web.facade.LarexFacade;
import com.web.model.Book;
import com.web.model.BookSettings;
import com.web.model.PageSegmentation;
import com.web.model.Polygon;
import com.web.model.database.FileDatabase;
import com.web.model.database.IDatabase;

import larex.regions.type.RegionType;
import larex.segmentation.parameters.ImageSegType;

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
	private LarexFacade segmenter;
	@Autowired
	private FileManager fileManager;
	@Autowired
	private FileConfiguration config;

	@RequestMapping(value = "/viewer", method = RequestMethod.GET)
	public String viewer(Model model, @RequestParam(value = "book", required = false) Integer bookID)
			throws IOException {
		init();
		if (bookID == null) {
			return "redirect:/404";
		}

		segmenter.clear();
		prepareSegmenter(bookID);
		Book book = segmenter.getBook();

		if (book == null) {
			return "redirect:/404";
		}

		model.addAttribute("book", book);
		model.addAttribute("segmenttypes", getSegmentTypes());
		model.addAttribute("imageSegTypes", getImageSegmentTypes());
		model.addAttribute("bookPath", fileManager.getWebBooksPath());
		model.addAttribute("globalSettings", config);

		return "editor";
	}

	@RequestMapping(value = "/direct", method = RequestMethod.POST)
	public String direct(Model model, @RequestParam(value = "bookpath", required = true) String bookpath,
			@RequestParam(value = "bookname", required = true) String bookname,
			@RequestParam(value = "localsave", required = false) String localsave,
			@RequestParam(value = "savedir", required = false) String savedir,
			@RequestParam(value = "websave", required = false) String websave)
			throws IOException {
		init();
		if (!config.getSetting("directrequest").equals("enable")) {
			return "redirect:/403";
		}
		if(!new File(bookpath+File.separator+bookname).exists()) {
			return "redirect:/400";
		}
		fileManager.setBooksPath(bookpath);
		int bookID = bookname.hashCode();

		if(localsave != null) {
			config.setSetting("localsave", localsave);
		}
		if(savedir != null) {
			config.setSetting("savedir", savedir);
		}
		if(websave != null) {
			config.setSetting("websave", websave);
		}
		return viewer(model, bookID);
	}

	@RequestMapping(value = "/book", method = RequestMethod.POST)
	public @ResponseBody FullBookResponse getBook(@RequestParam("bookid") int bookID,
			@RequestParam("pageid") int pageID) {
		prepareSegmenter(bookID);
		Book book = segmenter.getBook();
		BookSettings settings = segmenter.getDefaultSettings(book);
		Map<Integer, PageSegmentation> segmentations = new HashMap<Integer, PageSegmentation>();
		segmentations.put(pageID, segmenter.segmentPage(settings, pageID, false));

		FullBookResponse bookview = new FullBookResponse(book, segmentations, settings);
		return bookview;
	}

	@RequestMapping(value = "/segment", method = RequestMethod.POST, headers = "Accept=*/*", produces = "application/json", consumes = "application/json")
	public @ResponseBody PageSegmentation segment(@RequestBody SegmentationRequest segmentationRequest) {
		return segmenter.segmentPage(segmentationRequest.getSettings(), segmentationRequest.getPages(),
				segmentationRequest.isAllowToLoadLocal());
	}

	@RequestMapping(value = "/merge", method = RequestMethod.POST)
	public @ResponseBody Polygon segment(@RequestParam("segmentids[]") List<String> segmentIDs,
			@RequestParam("pageid") int pageID) {
		Polygon merged = segmenter.merge(segmentIDs, pageID);
		return merged;
	}

	private LarexFacade prepareSegmenter(int bookID) {
		init();
		IDatabase database = new FileDatabase(new File(fileManager.getBooksPath()));

		if (!segmenter.isInit()) {
			String resourcepath = fileManager.getBooksPath();
			segmenter.init(database.getBook(bookID), resourcepath);
		} else if (bookID != segmenter.getBook().getId()) {
			segmenter.setBook(database.getBook(bookID));
		}
		return segmenter;
	}

	private Map<RegionType, Integer> getSegmentTypes() {
		Comparator<RegionType> compareAlphabetically = new Comparator<RegionType>() {
			@Override
			public int compare(RegionType o1, RegionType o2) {
				return o1.toString().toLowerCase().compareTo(o2.toString().toLowerCase());
			}
		};
		Map<RegionType, Integer> segmentTypes = new TreeMap<RegionType, Integer>(compareAlphabetically);

		int i = 0;
		for (RegionType type : RegionType.values()) {
			segmentTypes.put(type, i);
			i++;
		}
		return segmentTypes;
	}

	private Map<ImageSegType, String> getImageSegmentTypes() {
		Map<ImageSegType, String> segmentTypes = new TreeMap<ImageSegType, String>();
		segmentTypes.put(ImageSegType.NONE, "None");
		segmentTypes.put(ImageSegType.CONTOUR_ONLY, "Contour only");
		segmentTypes.put(ImageSegType.STRAIGHT_RECT, "Straight rectangle");
		segmentTypes.put(ImageSegType.ROTATED_RECT, "Rotated rectangle");
		return segmentTypes;
	}

	private void init() {
		if (!fileManager.isInit()) {
			fileManager.init(servletContext);
		}
		if (!config.isInitiated()) {
			config.read(new File(fileManager.getConfigurationFile()));
			String bookFolder = config.getSetting("bookpath");
			if (!bookFolder.equals("")) {
				fileManager.setBooksPath(bookFolder);
			}
		}
	}
}
