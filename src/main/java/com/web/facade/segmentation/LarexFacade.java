package com.web.facade.segmentation;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.web.communication.SegmentationStatus;
import com.web.io.FileManager;
import com.web.io.ImageLoader;
import com.web.io.PageXMLReader;
import com.web.io.SettingsReader;
import com.web.io.SettingsWriter;
import com.web.model.Book;
import com.web.model.Page;
import com.web.model.PageAnnotations;
import com.web.model.Region;
import com.web.model.database.FileDatabase;

import larex.data.MemoryCleaner;
import larex.geometry.regions.RegionSegment;
import larex.segmentation.Segmenter;
import larex.segmentation.parameters.Parameters;

/**
 * Segmenter using the Larex project/algorithm
 * 
 */
public class LarexFacade {

	public static PageAnnotations segmentPage(SegmentationSettings settings, int pageNr, boolean allowLocalResults,
			FileManager fileManager, FileDatabase database) {
		Book book = getBook(settings.getBookID(), database);

		Page page = book.getPage(pageNr);
		String xmlPath = fileManager.getLocalBooksPath() + File.separator + book.getName() + File.separator + page.getName()
				+ ".xml";

		if (allowLocalResults && new File(xmlPath).exists()) {
			PageAnnotations segmentation = PageXMLReader.loadPageAnnotationsFromDisc(xmlPath);;
			segmentation.setStatus(SegmentationStatus.LOADED);
			return segmentation;
		} else {
			PageAnnotations segmentation = segment(settings, page, fileManager);
			return segmentation;
		}
	}

	public static PageAnnotations emptySegmentPage(int bookid, int pageNr, FileDatabase database) {
		Book book = getBook(bookid, database);

		Page page = book.getPage(pageNr);

		ArrayList<RegionSegment> regions = new ArrayList<RegionSegment>();

		PageAnnotations segmentation =  new PageAnnotations(page.getFileName(), page.getWidth(), page.getHeight(), regions,
				page.getId());
		segmentation.setStatus(SegmentationStatus.EMPTY);
		return segmentation;
	}

	public static Document getSettingsXML(SegmentationSettings settings, int page) {
		Parameters parameters = settings.toParameters(new Size(), page);
		return SettingsWriter.getSettingsXML(parameters);
	}

	private static PageAnnotations segment(SegmentationSettings settings, Page page, FileManager fileManager) {
		PageAnnotations segmentation = null;
		Collection<RegionSegment> segmentationResult = segmentLarex(settings, page, fileManager);

		if (segmentationResult != null) {

			segmentation = new PageAnnotations(page.getFileName(), page.getWidth(), page.getHeight(),
					segmentationResult, page.getId());
		} else {
			segmentation = new PageAnnotations(page.getFileName(), page.getWidth(), page.getHeight(), page.getId(),
					new HashMap<String, Region>(), SegmentationStatus.MISSINGFILE, new ArrayList<String>());
		}
		return segmentation;
	}

	private static Collection<RegionSegment> segmentLarex(SegmentationSettings settings, Page page, FileManager fileManager) {
		String imagePath = fileManager.getLocalBooksPath() + File.separator + page.getImage();

		File imageFile = new File(imagePath);
		if (imageFile.exists()) {
			Mat original = ImageLoader.readOriginal(imageFile);

			Parameters parameters = settings.toParameters(original.size(), page.getId());

			Collection<RegionSegment> result = Segmenter.segment(original,parameters);
			MemoryCleaner.clean(original);
			return result;
		} else {
			System.err.println(
					"Warning: Image file could not be found. Segmentation result will be empty. File: " + imagePath);
			return null;
		}
	}

	public static File getImagePath(Page page, FileManager fileManager) {
		return new File(fileManager.getLocalBooksPath() + File.separator + page.getImage());
	}

	public static SegmentationSettings readSettings(byte[] settingsFile, int bookID, FileManager fileManager, FileDatabase database) {
		SegmentationSettings settings = null;

		try(ByteArrayInputStream stream = new ByteArrayInputStream(settingsFile)){
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document document = dBuilder.parse(stream);

			Book book = getBook(bookID, database);
			Page page = book.getPage(0);
			String imagePath = fileManager.getLocalBooksPath() + File.separator + page.getImage();

			Parameters parameters = SettingsReader.loadSettings(document, ImageLoader.readDimensions(new File(imagePath)));

			settings = new SegmentationSettings(parameters, book);
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

			return PageXMLReader.getPageAnnotations(dBuilder.parse(stream));
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
