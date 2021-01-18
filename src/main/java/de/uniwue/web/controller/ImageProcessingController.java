package de.uniwue.web.controller;

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

import de.uniwue.web.communication.ContourCombineRequest;
import de.uniwue.web.config.LarexConfiguration;
import de.uniwue.web.facade.ImageProcessingFacade;
import de.uniwue.web.io.FileDatabase;
import de.uniwue.web.io.FilePathManager;
import de.uniwue.web.model.Point;
import de.uniwue.web.model.Polygon;
import de.uniwue.web.model.Rectangle;
import de.uniwue.web.model.Region;

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
	@RequestMapping(value = "process/regions/merge", method = RequestMethod.POST, headers = "Accept=*/*", produces = "application/json", consumes = "application/json")
	public @ResponseBody Region merge(@RequestBody List<Region> segments) {
		return ImageProcessingFacade.merge(segments);
	}

	/**
	 * Combine contours from an image together, via smearing.
	 */
	@RequestMapping(value = "process/contours/combine", method = RequestMethod.POST, headers = "Accept=*/*", produces = "application/json", consumes = "application/json")
	public @ResponseBody Region combinecontours(@RequestBody ContourCombineRequest combineRequest) {
		if (combineRequest.getContours().size() > 0) {
			FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
					config.getListSetting("imagefilter"), fileManager.checkFlat());
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
	@RequestMapping(value = "process/contours/extract", method = RequestMethod.POST)
	public @ResponseBody Collection<List<Point>> extractcontours(@RequestParam("bookid") int bookID,
			@RequestParam("pageid") int pageID) {

		FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
				config.getListSetting("imagefilter"), fileManager.checkFlat());
		return ImageProcessingFacade.extractContours(pageID, bookID, fileManager, database);
	}
	
	@RequestMapping(value = "process/polygons/minarearect", method = RequestMethod.POST)
	public @ResponseBody Rectangle getMinAreaRect(@RequestBody Polygon polygon) {
		return ImageProcessingFacade.getMinAreaRectangle(polygon);
	}
}
