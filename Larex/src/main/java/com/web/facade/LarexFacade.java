package com.web.facade;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.tomcat.util.http.fileupload.ByteArrayOutputStream;
import org.opencv.core.Size;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.web.communication.ExportRequest;
import com.web.communication.SegmentationStatus;
import com.web.model.Book;
import com.web.model.BookSegmentation;
import com.web.model.BookSettings;
import com.web.model.Page;
import com.web.model.PageSegmentation;
import com.web.model.Polygon;

import larex.export.PageXMLWriter;
import larex.export.SettingsReader;
import larex.export.SettingsWriter;
import larex.regionOperations.Merge;
import larex.regions.RegionManager;
import larex.regions.type.RegionType;
import larex.segmentation.Segmenter;
import larex.segmentation.parameters.Parameters;
import larex.segmentation.result.ResultRegion;
import larex.segmentation.result.SegmentationResult;

/**
 * Segmenter using the Larex project/algorithm
 * 
 */
@Component
@Scope("session")
public class LarexFacade implements IFacade {

	private larex.dataManagement.Page exportPage;
	private String resourcepath;
	private Book book;
	private BookSegmentation bookSegment;
	private Segmenter segmenter;
	private Parameters parameters;
	private Document exportSettings;
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
	public void clear() {
		this.resourcepath = "";
		this.book = null;
		this.bookSegment = null;
		this.segmenter = null;
		this.parameters = null;
		this.isInit = false;
		this.segmentedLarexPages = null;
	}

	@Override
	public BookSegmentation segmentAll(BookSettings settings) {
		if (book == null || !(settings.getBookID() == book.getId())) {
			System.err.println("Warning: Book and settings do not match.");
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
		return LarexWebTranslator.translateParametersToSettings(parameters, book);
	}

	@Override
	public void prepareExport(ExportRequest exportRequest) {
		// shallow clown page (ResultRegions are not cloned)
		exportPage = segmentedLarexPages.get(exportRequest.getPage()).clone();
		SegmentationResult result = exportPage.getSegmentationResult();

		// Deleted
		for (String segmentID : exportRequest.getSegmentsToIgnore()) {
			result.removeRegionByID(segmentID);
		}

		// Merged TODO delete?
		Map<String, ArrayList<String>> segmentsToMerge = exportRequest.getSegmentsToMerge();
		if (segmentsToMerge != null) {
			for (String mergedSegmentID : segmentsToMerge.keySet()) {
				String id = "";
				ArrayList<ResultRegion> regionsToMerge = new ArrayList<ResultRegion>();

				for (String segmentID : exportRequest.getSegmentsToMerge().get(mergedSegmentID)) {
					id += segmentID;
					regionsToMerge.add(result.removeRegionByID(segmentID));
				}

				ResultRegion mergedRegions = Merge.merge(regionsToMerge, exportPage.getBinary());
				mergedRegions.setId(id);
				result.addRegion(mergedRegions);
			}
		}

		// ChangedTypes
		for (Map.Entry<String, RegionType> changeType : exportRequest.getChangedTypes().entrySet()) {
			// clone ResultRegion before changing it
			if (result.getRegionByID(changeType.getKey()) != null) {
				ResultRegion clone = result.removeRegionByID(changeType.getKey()).clone();
				clone.setType(changeType.getValue());
				result.addRegion(clone);
			}
		}

		// FixedSegments
		Map<String, Polygon> fixedSegments = exportRequest.getFixedRegions();
		if (fixedSegments != null) {
			for (String fixedSegmentID : fixedSegments.keySet()) {
				// Replace Region with fixed Region if exists
				result.removeRegionByID(fixedSegmentID);
				result.addRegion(WebLarexTranslator.translateSegmentToResultRegion(fixedSegments.get(fixedSegmentID)));
			}
		}
		
		// Reading Order
		ArrayList<ResultRegion> readingOrder = new ArrayList<ResultRegion>();
		List<String> readingOrderStrings = exportRequest.getReadingOrder();
		for(String regionID : readingOrderStrings) {
			readingOrder.add(result.getRegionByID(regionID));
		}
	}

	@Override
	public ResponseEntity<byte[]> getPageXML(String version) {
		if (exportPage != null) {
			Document document = PageXMLWriter.getPageXML(exportPage, version);
			return convertDocumentToByte(document,exportPage.getFileName());
		} else {
			// TODO Error
			return null;
		}
	}

	@Override
	public void prepareSettings(BookSettings settings) {
		Parameters parameters = WebLarexTranslator.translateSettingsToParameters(settings, null, new Size());
		exportSettings = SettingsWriter.getSettingsXML(parameters);
	}

	@Override
	public ResponseEntity<byte[]> getSettingsXML() {
		if (exportSettings != null) {
			return convertDocumentToByte(exportSettings,"settings_"+book.getName());
		} else {
			// TODO Error
			return null;
		}
	}

	private ResponseEntity<byte[]> convertDocumentToByte(Document document, String filename) {
		// convert document to bytes
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

		// create ResponseEntry
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("application/xml"));
		headers.setContentDispositionFormData(filename, filename+ ".xml");
		headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

		return new ResponseEntity<byte[]>(documentbytes, headers, HttpStatus.OK);
	}

	@Override
	public Polygon merge(List<String> segments, int pageNr) {
		SegmentationResult resultPage = segmentedLarexPages.get(pageNr).getSegmentationResult();

		ArrayList<ResultRegion> resultRegions = new ArrayList<ResultRegion>();
		for (String segmentID : segments) {
			resultRegions.add(resultPage.getRegionByID(segmentID));
		}
		System.out.println(
				segmentedLarexPages.get(pageNr).getBinary() + " " + segmentedLarexPages.get(pageNr).getOriginal());
		ResultRegion mergedRegion = Merge.merge(resultRegions, segmentedLarexPages.get(pageNr).getBinary());

		return LarexWebTranslator.translateResultRegionToSegment(mergedRegion);
	}

	private PageSegmentation segment(BookSettings settings, Page page) {
		PageSegmentation segmentation = null;
		larex.dataManagement.Page currentLarexPage = segmentLarex(settings, page);

		if (currentLarexPage != null) {
			SegmentationResult segmentationResult = currentLarexPage.getSegmentationResult();
			currentLarexPage.setSegmentationResult(segmentationResult);

			ArrayList<ResultRegion> regions = segmentationResult.getRegions();

			segmentation = LarexWebTranslator.translateResultRegionsToSegmentation(regions, page.getId());
		} else {
			segmentation = new PageSegmentation(page.getId(), new HashMap<String, Polygon>(),
					SegmentationStatus.MISSINGFILE);
		}

		return segmentation;
	}

	private larex.dataManagement.Page segmentLarex(BookSettings settings, Page page) {
		// TODO Performance
		String imagePath = resourcepath + File.separator + page.getImage();

		if (new File(imagePath).exists()) {
			larex.dataManagement.Page currentLarexPage = new larex.dataManagement.Page(imagePath);

			currentLarexPage.initPage();

			Size pagesize = currentLarexPage.getOriginal().size();

			parameters = WebLarexTranslator.translateSettingsToParameters(settings, parameters, pagesize);
			parameters.getRegionManager().setPointListManager(
					WebLarexTranslator.translateSettingsToPointListManager(settings, page.getId()));

			if (segmenter == null) {
				segmenter = new Segmenter(parameters);
			} else {
				segmenter.setParameters(parameters);
			}
			SegmentationResult segmentationResult = segmenter.segment(currentLarexPage.getOriginal());
			currentLarexPage.setSegmentationResult(segmentationResult);

			segmentedLarexPages.put(page.getId(), currentLarexPage.clone());
			return currentLarexPage;
		} else {
			System.err.println(
					"Warning: Image file could not be found. Segmentation result will be empty. File: " + imagePath);
			return null;
		}
	}

	@Override
	public BookSettings readSettings(byte[] settingsFile) {
		BookSettings settings = null;
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document document = dBuilder.parse(new ByteArrayInputStream(settingsFile));
			
			Page page = book.getPage(0);
			String imagePath = resourcepath + File.separator + page.getImage();
			larex.dataManagement.Page currentLarexPage = new larex.dataManagement.Page(imagePath);
			currentLarexPage.initPage();
			
			Parameters parameters = SettingsReader.loadSettings(document, currentLarexPage.getBinary());
			settings = LarexWebTranslator.translateParametersToSettings(parameters, book);
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		}
		return settings;
	}
}