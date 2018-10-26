package com.web.facade;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Size;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.web.communication.SegmentationStatus;
import com.web.controller.FileManager;
import com.web.model.Book;
import com.web.model.BookSettings;
import com.web.model.Page;
import com.web.model.PageSegmentation;
import com.web.model.Point;
import com.web.model.Polygon;
import com.web.model.database.FileDatabase;
import com.web.model.database.IDatabase;

import larex.data.export.PageXMLReader;
import larex.data.export.PageXMLWriter;
import larex.data.export.SettingsReader;
import larex.data.export.SettingsWriter;
import larex.geometry.regions.RegionSegment;
import larex.geometry.regions.type.RegionType;
import larex.operators.Contourextractor;
import larex.operators.Merger;
import larex.segmentation.SegmentationResult;
import larex.segmentation.Segmenter;
import larex.segmentation.parameters.Parameters;

/**
 * Segmenter using the Larex project/algorithm
 * 
 */
public class LarexFacade {

	public static PageSegmentation segmentPage(BookSettings settings, int pageNr, boolean allowLocalResults,
			FileManager fileManager) {
		Book book = getBook(settings.getBookID(), fileManager);

		Page page = book.getPage(pageNr);
		String xmlPath = fileManager.getBooksPath() + File.separator + book.getName() + File.separator + page.getName() + ".xml";

		if (allowLocalResults && new File(xmlPath).exists()) {
			SegmentationResult loadedResult = PageXMLReader.loadSegmentationResultFromDisc(xmlPath);
			PageSegmentation segmentation = LarexWebTranslator.translateResultRegionsToSegmentation(page.getFileName(),
					page.getWidth(), page.getHeight(), loadedResult.getRegions(), page.getId());
			segmentation.setStatus(SegmentationStatus.LOADED);
			return segmentation;
		} else {
			PageSegmentation segmentation = segment(settings, page, fileManager);
			return segmentation;
		}
	}

	public static BookSettings getDefaultSettings(Book book) {
		return LarexWebTranslator.translateParameters(new Parameters(), book);
	}

	public static Document getPageXML(PageSegmentation segmentation, String version) {
		SegmentationResult result = WebLarexTranslator.translateResult(segmentation);
		return PageXMLWriter.getPageXML(result, segmentation.getFileName(), segmentation.getWidth(),
				segmentation.getHeight(), version);
	}

	public static void savePageXMLLocal(String saveDir, String filename, Document document) {
		PageXMLWriter.saveDocument(document, filename, saveDir);
	}

	public static Document getSettingsXML(BookSettings settings) {
		Parameters parameters = WebLarexTranslator.translateSettings(settings, new Size());
		return SettingsWriter.getSettingsXML(parameters);
	}

	public static Polygon merge(List<Polygon> segments, int pageNr, int bookID, FileManager fileManager) {
		ArrayList<RegionSegment> resultRegions = new ArrayList<RegionSegment>();
		for (Polygon segment : segments)
			resultRegions.add(WebLarexTranslator.translateSegment(segment));

		Book book = getBook(bookID, fileManager);
		larex.data.Page page = getLarexPage(book.getPage(pageNr), fileManager);
		page.initPage();
		RegionSegment mergedRegion = Merger.lineMerge(resultRegions, page.getBinary().size());
		page.clean();
		System.gc();

		return LarexWebTranslator.translateResultRegionToSegment(mergedRegion);
	}

	public static Collection<List<Point>> extractContours(int pageNr, int bookID, FileManager fileManager) {
		Book book = getBook(bookID, fileManager);
		larex.data.Page page = getLarexPage(book.getPage(pageNr), fileManager);
		page.initPage();

		Collection<MatOfPoint> contours = Contourextractor.fromSource(page.getOriginal());
		page.clean();
		System.gc();

		Collection<List<Point>> contourSegments = new ArrayList<>();
		for (MatOfPoint contour : contours)
			contourSegments.add(LarexWebTranslator.translatePointsToContour(contour));

		return contourSegments;
	}

	public static Polygon combineContours(Collection<List<Point>> contours, int pageNr, int bookID,
			FileManager fileManager) {
		Book book = getBook(bookID, fileManager);
		larex.data.Page page = getLarexPage(book.getPage(pageNr), fileManager);
		page.initPage();

		Collection<MatOfPoint> matContours = new ArrayList<>();
		for (List<Point> contour : contours) {
			matContours.add(WebLarexTranslator.translateContour(contour));
		}

		MatOfPoint combined = Merger.smearMerge(matContours, page.getBinary());
		page.clean();
		System.gc();

		return LarexWebTranslator.translatePointsToSegment(combined, UUID.randomUUID().toString(),
				RegionType.paragraph);
	}

	private static PageSegmentation segment(BookSettings settings, Page page, FileManager fileManager) {
		PageSegmentation segmentation = null;
		larex.data.Page currentLarexPage = segmentLarex(settings, page, fileManager);

		if (currentLarexPage != null) {
			SegmentationResult segmentationResult = currentLarexPage.getSegmentationResult();
			currentLarexPage.setSegmentationResult(segmentationResult);

			ArrayList<RegionSegment> regions = segmentationResult.getRegions();

			segmentation = LarexWebTranslator.translateResultRegionsToSegmentation(page.getFileName(), page.getWidth(),
					page.getHeight(), regions, page.getId());
		} else {
			segmentation = new PageSegmentation(page.getFileName(), page.getWidth(), page.getHeight(), page.getId(),
					new HashMap<String, Polygon>(), SegmentationStatus.MISSINGFILE, new ArrayList<String>());
		}
		return segmentation;
	}

	private static larex.data.Page segmentLarex(BookSettings settings, Page page, FileManager fileManager) {
		String imagePath = fileManager.getBooksPath() + File.separator + page.getImage();

		if (new File(imagePath).exists()) {
			larex.data.Page currentLarexPage = new larex.data.Page(imagePath);
			currentLarexPage.initPage();

			Size pagesize = currentLarexPage.getOriginal().size();

			Parameters parameters = WebLarexTranslator.translateSettings(settings, pagesize, page.getId());

			Segmenter segmenter = new Segmenter(parameters);
			SegmentationResult segmentationResult = segmenter.segment(currentLarexPage.getOriginal());
			currentLarexPage.setSegmentationResult(segmentationResult);

			currentLarexPage.clean();

			System.gc();
			return currentLarexPage;
		} else {
			System.err.println(
					"Warning: Image file could not be found. Segmentation result will be empty. File: " + imagePath);
			return null;
		}
	}

	private static larex.data.Page getLarexPage(Page page, FileManager fileManager) {
		String imagePath = fileManager.getBooksPath() + File.separator + page.getImage();

		if (new File(imagePath).exists()) {
			return new larex.data.Page(imagePath);
		}
		return null;
	}

	public static BookSettings readSettings(byte[] settingsFile, int bookID, FileManager fileManager) {
		BookSettings settings = null;
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document document = dBuilder.parse(new ByteArrayInputStream(settingsFile));

			Book book = getBook(bookID, fileManager);
			Page page = book.getPage(0);
			String imagePath = fileManager.getBooksPath() + File.separator + page.getImage();
			larex.data.Page currentLarexPage = new larex.data.Page(imagePath);
			currentLarexPage.initPage();

			Parameters parameters = SettingsReader.loadSettings(document, currentLarexPage.getBinary());
			settings = LarexWebTranslator.translateParameters(parameters, book);

			currentLarexPage.clean();
			System.gc();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		}
		return settings;
	}

	public static PageSegmentation readPageXML(byte[] pageXML, int pageNr, int bookID, FileManager fileManager) {
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document document = dBuilder.parse(new ByteArrayInputStream(pageXML));
			Page page = getBook(bookID, fileManager).getPage(pageNr);

			SegmentationResult result = PageXMLReader.getSegmentationResult(document);
			PageSegmentation pageSegmentation = LarexWebTranslator.translateResultRegionsToSegmentation(
					page.getFileName(), page.getWidth(), page.getHeight(), result.getRegions(), page.getId());

			List<String> readingOrder = new ArrayList<String>();
			for (RegionSegment region : result.getReadingOrder()) {
				readingOrder.add(region.getId());
			}
			pageSegmentation.setReadingOrder(readingOrder);

			return pageSegmentation;
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static Book getBook(int bookID, FileManager fileManager) {
		IDatabase database = new FileDatabase(new File(fileManager.getBooksPath()));
		return database.getBook(bookID);
	}
}
