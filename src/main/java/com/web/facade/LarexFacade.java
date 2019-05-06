package com.web.facade;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Size;
import org.primaresearch.ident.IdRegister.InvalidIdException;
import org.primaresearch.io.UnsupportedFormatVersionException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.web.communication.SegmentationStatus;
import com.web.controller.FileManager;
import com.web.model.Book;
import com.web.model.Page;
import com.web.model.PageAnnotations;
import com.web.model.Point;
import com.web.model.Polygon;
import com.web.model.database.FileDatabase;

import larex.data.export.PageXMLReader;
import larex.data.export.PageXMLWriter;
import larex.data.export.SettingsReader;
import larex.data.export.SettingsWriter;
import larex.geometry.regions.RegionSegment;
import larex.geometry.regions.type.RegionSubType;
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

	public static PageAnnotations segmentPage(BookSettings settings, int pageNr, boolean allowLocalResults,
			FileManager fileManager, FileDatabase database) {
		Book book = getBook(settings.getBookID(), database);

		Page page = book.getPage(pageNr);
		String xmlPath = fileManager.getLocalBooksPath() + File.separator + book.getName() + File.separator + page.getName()
				+ ".xml";

		if (allowLocalResults && new File(xmlPath).exists()) {
			SegmentationResult loadedResult = PageXMLReader.loadSegmentationResultFromDisc(xmlPath);
			PageAnnotations segmentation = new PageAnnotations(page.getFileName(), page.getWidth(), page.getHeight(),
					loadedResult.getRegions(), page.getId());
			segmentation.setStatus(SegmentationStatus.LOADED);
			return segmentation;
		} else {
			PageAnnotations segmentation = segment(settings, page, fileManager);
			return segmentation;
		}
	}

	public static PageAnnotations emptySegmentPage(BookSettings settings, int pageNr, FileDatabase database) {
		Book book = getBook(settings.getBookID(), database);

		Page page = book.getPage(pageNr);

		ArrayList<RegionSegment> regions = new ArrayList<RegionSegment>();

		PageAnnotations segmentation =  new PageAnnotations(page.getFileName(), page.getWidth(), page.getHeight(), regions,
				page.getId());
		segmentation.setStatus(SegmentationStatus.EMPTY);
		return segmentation;
	}

	public static BookSettings getDefaultSettings(Book book) {
		return new BookSettings(new Parameters(), book);
	}

	public static Document getPageXML(PageAnnotations segmentation, String version) {
		SegmentationResult result = segmentation.toSegmentationResult();
		try {
			return PageXMLWriter.getPageXML(result, segmentation.getFileName(), segmentation.getWidth(),
					segmentation.getHeight(), version);
		} catch (UnsupportedFormatVersionException e) {
			System.out.println(e.toString());
			e.printStackTrace();
			return null;
		} catch (InvalidIdException e) {
			System.out.println(e.toString());
			e.printStackTrace();
			return null;
		}
	}

	public static void savePageXMLLocal(String saveDir, String filename, Document document) {
		PageXMLWriter.saveDocument(document, filename, saveDir);
	}

	public static Document getSettingsXML(BookSettings settings) {
		Parameters parameters = settings.toParameters(new Size());
		return SettingsWriter.getSettingsXML(parameters);
	}

	public static Polygon merge(List<Polygon> segments, int pageNr, int bookID, FileManager fileManager, FileDatabase database) {
		ArrayList<RegionSegment> resultRegions = new ArrayList<RegionSegment>();
		for (Polygon segment : segments)
			resultRegions.add(segment.toRegionSegment());

		Book book = getBook(bookID, database);
		larex.data.Page page = getLarexPage(book.getPage(pageNr), fileManager);
		page.initPage();
		RegionSegment mergedRegion = Merger.lineMerge(resultRegions, page.getBinary().size());
		page.clean();
		System.gc();

		return new Polygon(mergedRegion);
	}

	public static Collection<List<Point>> extractContours(int pageNr, int bookID, FileManager fileManager, FileDatabase database) {
		Book book = getBook(bookID, database);
		larex.data.Page page = getLarexPage(book.getPage(pageNr), fileManager);
		page.initPage();

		Collection<MatOfPoint> contours = Contourextractor.fromSource(page.getOriginal());
		page.clean();
		System.gc();

		Collection<List<Point>> contourSegments = new ArrayList<>();
		for (MatOfPoint contour : contours) {
			LinkedList<Point> points = new LinkedList<>();
			for (org.opencv.core.Point regionPoint : contour.toList()) {
				points.add(new Point(regionPoint.x, regionPoint.y));
			}
			contourSegments.add(points);
		}

		return contourSegments;
	}

	/**
	 * Request to combine contours (point list) to a polygon of type paragraph.
	 * 
	 * @param contours    Contours to combine
	 * @param pageNr      Page from which the contours are from (for dimensions)
	 * @param bookID      Book from with the page is from
	 * @param accuracy    Accuracy of the combination process (between 0 and 100)
	 * @param fileManager Filemanager to load the book/page from
	 * @return Polygon that includes all contours
	 */
	public static Polygon combineContours(Collection<List<Point>> contours, int pageNr, int bookID, int accuracy,
			FileManager fileManager, FileDatabase database) {
		Book book = getBook(bookID, database);
		larex.data.Page page = getLarexPage(book.getPage(pageNr), fileManager);
		page.initPage();

		Collection<MatOfPoint> matContours = new ArrayList<>();
		for (List<Point> contour : contours) {
			org.opencv.core.Point[] matPoints = new org.opencv.core.Point[contour.size()];
			for (int index = 0; index < contour.size(); index++) {
				Point point = contour.get(index);
				matPoints[index] = new org.opencv.core.Point(point.getX(), point.getY());
			}
			matContours.add(new MatOfPoint(matPoints));
		}

		
	
		accuracy = Math.max(0,Math.max(100,accuracy));
				
		double growth = 105 - 100/(accuracy/100.0);
		
		MatOfPoint combined = Merger.smearMerge(matContours, page.getBinary(), growth, growth,10);
		page.clean();
		System.gc();

		return new Polygon(combined, UUID.randomUUID().toString(), RegionSubType.paragraph.toString());
	}

	private static PageAnnotations segment(BookSettings settings, Page page, FileManager fileManager) {
		PageAnnotations segmentation = null;
		larex.data.Page currentLarexPage = segmentLarex(settings, page, fileManager);

		if (currentLarexPage != null) {
			SegmentationResult segmentationResult = currentLarexPage.getSegmentationResult();
			currentLarexPage.setSegmentationResult(segmentationResult);

			ArrayList<RegionSegment> regions = segmentationResult.getRegions();

			segmentation = new PageAnnotations(page.getFileName(), page.getWidth(), page.getHeight(), regions,
					page.getId());
		} else {
			segmentation = new PageAnnotations(page.getFileName(), page.getWidth(), page.getHeight(), page.getId(),
					new HashMap<String, Polygon>(), SegmentationStatus.MISSINGFILE, new ArrayList<String>());
		}
		return segmentation;
	}

	private static larex.data.Page segmentLarex(BookSettings settings, Page page, FileManager fileManager) {
		String imagePath = fileManager.getLocalBooksPath() + File.separator + page.getImage();

		if (new File(imagePath).exists()) {
			larex.data.Page currentLarexPage = new larex.data.Page(imagePath);
			currentLarexPage.initPage();

			Size pagesize = currentLarexPage.getOriginal().size();

			Parameters parameters = settings.toParameters(pagesize, page.getId());

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
		String imagePath = fileManager.getLocalBooksPath() + File.separator + page.getImage();

		if (new File(imagePath).exists()) {
			return new larex.data.Page(imagePath);
		}
		return null;
	}

	public static BookSettings readSettings(byte[] settingsFile, int bookID, FileManager fileManager, FileDatabase database) {
		BookSettings settings = null;

		try(ByteArrayInputStream stream = new ByteArrayInputStream(settingsFile)){
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document document = dBuilder.parse(stream);

			Book book = getBook(bookID, database);
			Page page = book.getPage(0);
			String imagePath = fileManager.getLocalBooksPath() + File.separator + page.getImage();
			larex.data.Page currentLarexPage = new larex.data.Page(imagePath);
			currentLarexPage.initPage();

			Parameters parameters = SettingsReader.loadSettings(document, currentLarexPage.getBinary());
			settings = new BookSettings(parameters, book);

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

	public static PageAnnotations readPageXML(byte[] pageXML, int pageNr, int bookID, FileDatabase database) {
		try (ByteArrayInputStream stream = new ByteArrayInputStream(pageXML)){
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document document = dBuilder.parse(stream);
			Page page = getBook(bookID, database).getPage(pageNr);

			SegmentationResult result = PageXMLReader.getSegmentationResult(document);
			PageAnnotations pageSegmentation = new PageAnnotations(page.getFileName(), page.getWidth(),
					page.getHeight(), result.getRegions(), page.getId());

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

	public static Book getBook(int bookID, FileDatabase database) {
		return database.getBook(bookID);
	}
}
