package com.web.controller;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.web.communication.FullBookResponse;
import com.web.communication.SegmentationRequest;
import com.web.communication.SegmentationResult;
import com.web.communication.SegmentationStatus;
import com.web.facade.LarexFacade;
import com.web.model.Book;
import com.web.model.BookSegmentation;
import com.web.model.BookSettings;
import com.web.model.database.IDatabase;
import com.web.model.database.FileDatabase;

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
	private FileController fileController;
	
	@RequestMapping(value = "/viewer", method = RequestMethod.GET)
	public String viewer(Model model, @RequestParam(value = "book", required = false) Integer bookID) throws IOException {
		if(!fileController.isInit()){
			fileController.init(servletContext);
		}
		if(bookID == null){
			return "redirect:/404";
		}
		
		segmenter.clear();
		prepareSegmenter(bookID);
		Book book = segmenter.getBook();
		
		if(book == null){
			return "redirect:/404";
		}
		
		model.addAttribute("book", book);
		model.addAttribute("segmenttypes", getSegmentTypes());
		model.addAttribute("imageSegTypes",getImageSegmentTypes());
		model.addAttribute("bookPath", fileController.getWebBooksPath());
		
		return "editor";
	}

	@RequestMapping(value = "/book", method = RequestMethod.POST)
	public @ResponseBody FullBookResponse getBook(@RequestParam("bookid") int bookID, @RequestParam("pageid") int pageID ) {
		prepareSegmenter(bookID);
		Book book = segmenter.getBook();
		BookSettings settings = segmenter.getDefaultSettings(book);
		BookSegmentation segmentation = segmenter.segmentPage(settings, pageID);

		FullBookResponse bookview = new FullBookResponse(book, segmentation, settings);
		return bookview;
	}

	@RequestMapping(value = "/segment", method = RequestMethod.POST, headers = "Accept=*/*", produces = "application/json", consumes = "application/json")
	public @ResponseBody SegmentationResult segment(@RequestBody SegmentationRequest segmentationRequest) {
		
		BookSegmentation segmentation = segmenter.segmentPages(segmentationRequest.getSettings(), segmentationRequest.getPages());
		SegmentationResult result = new SegmentationResult(segmentation, SegmentationStatus.SUCCESS);
		return result;
	}

	@RequestMapping(value = "/exportXML", method = RequestMethod.GET)//, headers = "Accept=*/*", consumes = "application/json"*/)
	public @ResponseBody ResponseEntity<byte[]> exportXML(@RequestParam("page") int pageID) {
	    return segmenter.getPageXML(pageID);
	}
	
	private LarexFacade prepareSegmenter(int bookID) {
		if(!fileController.isInit()){
			fileController.init(servletContext);
		}
		IDatabase database = new FileDatabase(new File(fileController.getBooksPath()));

		if (!segmenter.isInit()) {
			String resourcepath = fileController.getBooksPath();
			segmenter.init(database.getBook(bookID), resourcepath);
		} else if (bookID != segmenter.getBook().getId()) {
			segmenter.setBook(database.getBook(bookID));
		}
		return segmenter;
	}

	private Map<RegionType, Integer> getSegmentTypes() {
		Map<RegionType, Integer> segmentTypes = new HashMap<RegionType, Integer>();

		int i = 0;
		for (RegionType type : RegionType.values()) {
			segmentTypes.put(type, i);
			i++;
		}
		return segmentTypes;
	}
	
	private Map<ImageSegType, String> getImageSegmentTypes() {
		Map<ImageSegType, String> segmentTypes = new HashMap<ImageSegType, String>();
		segmentTypes.put(ImageSegType.NONE, "None");
		segmentTypes.put(ImageSegType.CONTOUR_ONLY, "Contour only");
		segmentTypes.put(ImageSegType.STRAIGHT_RECT, "Straight rectangle");
		segmentTypes.put(ImageSegType.ROTATED_RECT, "Rotated rectangle");
		/*int i = 0;
		for (ImageSegType type : ImageSegType.values()) {
			segmentTypes.put(type, i);
			i++;
		}*/
		return segmentTypes;
	}
}
