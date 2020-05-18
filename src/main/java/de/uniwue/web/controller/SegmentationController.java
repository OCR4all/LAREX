package de.uniwue.web.controller;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;

import de.uniwue.algorithm.geometry.regions.RegionSegment;
import de.uniwue.web.io.PageXMLWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import de.uniwue.web.communication.SegmentationRequest;
import de.uniwue.web.communication.BatchSegmentationRequest;
import de.uniwue.web.config.LarexConfiguration;
import de.uniwue.web.facade.segmentation.LarexFacade;
import de.uniwue.web.facade.segmentation.SegmentationSettings;
import de.uniwue.web.io.FileDatabase;
import de.uniwue.web.io.FilePathManager;
import de.uniwue.web.model.Page;
import de.uniwue.web.model.PageAnnotations;
import org.w3c.dom.Document;

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

	@RequestMapping(value = "segmentation/segment", method = RequestMethod.POST, headers = "Accept=*/*",
									produces = "application/json", consumes = "application/json")
	public @ResponseBody PageAnnotations segment(@RequestBody SegmentationRequest segmentationRequest) {
		FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
				config.getListSetting("imagefilter"));
		return LarexFacade.segmentPage(segmentationRequest.getSettings(), segmentationRequest.getPage(), fileManager, database);
	}

	@RequestMapping(value = "segmentation/batchSegment", method = RequestMethod.POST, headers = "Accept=*/*",
			produces = "application/json", consumes = "application/json")
	public @ResponseBody List<PageAnnotations> batchSegment(@RequestBody BatchSegmentationRequest batchSegmentationRequest) {
		FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
				config.getListSetting("imagefilter"));
		List<PageAnnotations> results = new ArrayList<>();
		boolean save = batchSegmentationRequest.getSave();
		for(int page: batchSegmentationRequest.getPages()){
			PageAnnotations result = LarexFacade.segmentPage(batchSegmentationRequest.getSettings(), page, fileManager, database);
			if(save){
				try {
					final Document pageXML = PageXMLWriter.getPageXML(result, batchSegmentationRequest.getVersion());

					final String xmlName =  result.getName() + ".xml";

					switch (config.getSetting("localsave")) {
						case "bookpath":
							String bookdir = fileManager.getLocalBooksPath() + File.separator
									+ database.getBookName(batchSegmentationRequest.getBookid());
							PageXMLWriter.saveDocument(pageXML, xmlName, bookdir);
							break;
						case "savedir":
							String savedir = config.getSetting("savedir");
							if (savedir != null && !savedir.equals("")) {
								PageXMLWriter.saveDocument(pageXML, xmlName, savedir);
							} else {
								System.err.println("Warning: Save dir is not set. File could not been saved.");
							}
							break;
						case "none":
						case "default":
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			};
			results.add(result);
		}
		return results;
	}

	@RequestMapping(value = "segmentation/settings", method = RequestMethod.POST)
	public @ResponseBody SegmentationSettings getBook(@RequestParam("bookid") int bookID) {
		FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
				config.getListSetting("imagefilter"));

		return new SegmentationSettings(database.getBook(bookID));
	}
	@RequestMapping(value = "segmentation/empty", method = RequestMethod.POST)
	public @ResponseBody PageAnnotations emptysegment(@RequestParam("bookid") int bookID,
			@RequestParam("pageid") int pageID) {
		FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
				config.getListSetting("imagefilter"));

		Page page = database.getBook(bookID).getPage(pageID);
		return new PageAnnotations(page.getName(), page.getWidth(), page.getHeight(), page.getId());
	}
}
