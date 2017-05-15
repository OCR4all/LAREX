package com.web.facade;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import com.web.model.Book;
import com.web.model.BookSegmentation;
import com.web.model.BookSettings;
import com.web.model.Page;
import com.web.model.PageSegmentation;

import larex.export.PageXMLWriter;
import larex.regions.RegionManager;
import larex.segmentation.Segmenter;
import larex.segmentation.parameters.Parameters;
import larex.segmentation.result.ResultRegion;
import larex.segmentation.result.SegmentationResult;

import org.apache.tomcat.util.http.fileupload.ByteArrayOutputStream;
import org.opencv.core.Size;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Segmenter using the Larex project/algorithm
 * 
 */
@Component
@Scope("session")
public class LarexFacade implements IFacade {

	private String resourcepath;
	private Book book;
	private BookSegmentation bookSegment;
	private Segmenter segmenter;
	private Parameters parameters;
	private boolean isInit = false;
	private HashMap<Integer, larex.dataManagement.Page> segmentedLarexPages;

	@Override
	public void init(Book book, String resourcepath) {
		this.book = book;
		this.bookSegment = new BookSegmentation(book.getId());
		this.resourcepath = resourcepath;
		this.isInit = true;
		this.segmentedLarexPages = new HashMap<Integer, larex.dataManagement.Page>();
	}

	@Override
	public void setBook(Book book) {
		this.book = book;
		this.bookSegment = new BookSegmentation(book.getId());
	}

	@Override
	public Book getBook() {
		return book;
	}

	@Override
	public boolean isInit() {
		return isInit;
	}

	@Override
	public BookSegmentation segmentAll(BookSettings settings) {
		if (book == null || !(settings.getBookID() == book.getId())) {
			// TODO Error
		}

		// TODO Settings changed?
		// bookSegment = new BookSegmentation(book.getId());

		for (Page page : book.getPages()) {
			bookSegment.setPage(segment(settings, page), page.getId());
		}

		return bookSegment;
	}

	@Override
	public BookSegmentation segmentPages(BookSettings settings, List<Integer> pages) {
		if (book == null || !(settings.getBookID() == book.getId())) {
			// TODO Error
		}

		// TODO Settings changed?
		// bookSegment = new BookSegmentation(book.getId());
		for (int pageNr : pages) {
			Page page = book.getPage(pageNr);
			bookSegment.setPage(segment(settings, page), page.getId());
		}
		return bookSegment;
	}

	@Override
	public BookSegmentation segmentPage(BookSettings settings, int pageNr) {
		return segmentPages(settings, Arrays.asList(pageNr));
	}

	@Override
	public BookSettings getDefaultSettings(Book book) {
		RegionManager regionmanager = new RegionManager();
		Parameters parameters = new Parameters(regionmanager, 0);
		return LarexTranslator.translateParametersToSettings(parameters, book);
	}

	@Override
	public ResponseEntity<byte[]> getPageXML(int pageID) {
		larex.dataManagement.Page page = segmentedLarexPages.get(pageID);
		Document document = PageXMLWriter.getPageXML(page);
		
		//convert document to bytes
		byte[] documentbytes = null;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			TransformerFactory factory = TransformerFactory.newInstance();
			Transformer transformer = factory.newTransformer();
			transformer.transform(new DOMSource(document), new StreamResult(out));
			documentbytes = out.toByteArray();
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
		} catch (TransformerException e) {
			e.printStackTrace();
		}

		//create ResponseEntry
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("application/xml"));
		String filename = page.getFileName() + ".xml";
		headers.setContentDispositionFormData(filename, filename);
		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
		
		return new ResponseEntity<byte[]>(documentbytes, headers, HttpStatus.OK);
	}

	private PageSegmentation segment(BookSettings settings, Page page) {
		// TODO Performance
		String imagePath = resourcepath + File.separator + page.getImage();
		String imageIdentifier = "" + page.getId();

		// TODO Regionmanager + GUI ? Delete?
		larex.dataManagement.Page currentLarexPage = new larex.dataManagement.Page(imagePath, imageIdentifier);
		segmentedLarexPages.put(page.getId(), currentLarexPage);
		currentLarexPage.initPage();

		Size pagesize = currentLarexPage.getOriginalSize();

		parameters = LarexTranslator.translateSettingsToParameters(settings, parameters, page, pagesize);
		parameters.getRegionManager()
				.setPointListManager(LarexTranslator.translateSettingsToPointListManager(settings, page.getId()));

		if (segmenter == null) {
			segmenter = new Segmenter(parameters);
		} else {
			segmenter.setParameters(parameters);
		}
		SegmentationResult segmentationResult = segmenter.segment(currentLarexPage.getOriginal());
		currentLarexPage.setSegmentationResult(segmentationResult);

		ArrayList<ResultRegion> regions = segmentationResult.getRegions();

		PageSegmentation segmentation = LarexTranslator.translateResultRegionsToSegmentation(regions, page.getId());

		return segmentation;
	}
}