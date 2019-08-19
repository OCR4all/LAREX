package com.web.controller;

import java.io.File;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.web.communication.ContourCombineRequest;
import com.web.config.LarexConfiguration;
import com.web.facade.ImageProcessingFacade;
import com.web.io.FileDatabase;
import com.web.io.FilePathManager;
import com.web.model.Point;
import com.web.model.Polygon;
import com.web.model.Rectangle;
import com.web.model.Region;

/**
 * Communication Controller to handle requests for the main viewer/editor.
 * Handles requests about displaying book scans and segmentations.
 * 
 */
@Controller
@Scope("request")
public class ImageProcessingController {
	@Autowired
	private FilePathManager fileManager;
	@Autowired
	private LarexConfiguration config;

	/**
	 * Merge segments together by connecting them with lines starting from their center.
	 */
	@RequestMapping(value = "/merge", method = RequestMethod.POST, headers = "Accept=*/*", produces = "application/json", consumes = "application/json")
	public @ResponseBody Region merge(@RequestBody List<Region> segments) {
		return ImageProcessingFacade.merge(segments);
	}

	/**
	 * Combine contours from an image together, via smearing.
	 */
	@RequestMapping(value = "/combinecontours", method = RequestMethod.POST, headers = "Accept=*/*", produces = "application/json", consumes = "application/json")
	public @ResponseBody Region combinecontours(@RequestBody ContourCombineRequest combineRequest) {
		if (combineRequest.getContours().size() > 0) {
			FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
					config.getListSetting("imagefilter"));
			return ImageProcessingFacade.combineContours(combineRequest.getContours(), combineRequest.getPageWidth(), 
					combineRequest.getPageHeight(), combineRequest.getAccuracy(), fileManager, database);
		} else
			return null;
	}

	/**
	 * Extract all contours from an image 
	 * 
	 * @param bookID
	 * @param pageID
	 * @return
	 */
	@RequestMapping(value = "/extractcontours", method = RequestMethod.POST)
	public @ResponseBody Collection<List<Point>> extractcontours(@RequestParam("bookid") int bookID,
			@RequestParam("pageid") int pageID) {

		FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
				config.getListSetting("imagefilter"));
		return ImageProcessingFacade.extractContours(pageID, bookID, fileManager, database);
	}
	
	@RequestMapping(value = "/minarearect", method = RequestMethod.POST)
	public @ResponseBody Rectangle getMinAreaRect(@RequestBody Polygon polygon) {
		return ImageProcessingFacade.getMinAreaRectangle(polygon);
	}
}
