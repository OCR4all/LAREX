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
import com.web.io.FileDatabase;
import com.web.io.FilePathManager;
import com.web.io.ImageLoader;
import com.web.io.PageXMLReader;
import com.web.io.SegmentationSettingsReader;
import com.web.io.SegmentationSettingsWriter;
import com.web.model.Book;
import com.web.model.Page;
import com.web.model.PageAnnotations;
import com.web.model.Region;

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
			FilePathManager fileManager, FileDatabase database) {
		Book book = database.getBook(settings.getBookID());

		Page page = book.getPage(pageNr);
		String xmlPath = fileManager.getLocalBooksPath() + File.separator + book.getName() + File.separator + page.getName()
				+ ".xml";

		if (allowLocalResults && new File(xmlPath).exists()) {
			return PageXMLReader.loadPageAnnotationsFromDisc(xmlPath);
		} else {
			PageAnnotations segmentation = segment(settings, page, fileManager);
			return segmentation;
		}
	}

	public static PageAnnotations emptySegmentPage(int bookid, int pageNr, FileDatabase database) {
		Book book = database.getBook(bookid);

		Page page = book.getPage(pageNr);

		ArrayList<RegionSegment> regions = new ArrayList<RegionSegment>();

		PageAnnotations segmentation =  new PageAnnotations(page.getName(), page.getWidth(), page.getHeight(),
				page.getId(), regions, SegmentationStatus.EMPTY);
		return segmentation;
	}

	public static Document getSettingsXML(SegmentationSettings settings) {
		Parameters parameters = settings.toParameters(new Size());
		return SegmentationSettingsWriter.getSettingsXML(parameters);
	}

	private static PageAnnotations segment(SegmentationSettings settings, Page page, FilePathManager fileManager) {
		PageAnnotations segmentation = null;
		Collection<RegionSegment> segmentationResult = segmentLarex(settings, page, fileManager);

		if (segmentationResult != null) {

			segmentation = new PageAnnotations(page.getName(), page.getWidth(), page.getHeight(),
					page.getId(), segmentationResult, SegmentationStatus.SUCCESS);
		} else {
			segmentation = new PageAnnotations(page.getName(), page.getWidth(), page.getHeight(),
					new HashMap<String, Region>(), SegmentationStatus.MISSINGFILE, new ArrayList<String>());
		}
		return segmentation;
	}

	private static Collection<RegionSegment> segmentLarex(SegmentationSettings settings, Page page, FilePathManager fileManager) {
		String imagePath = fileManager.getLocalBooksPath() + File.separator + page.getImages().get(0);

		File imageFile = new File(imagePath);
		if (imageFile.exists()) {
			Mat original = ImageLoader.readOriginal(imageFile);

			Parameters parameters = settings.toParameters(original.size());

			Collection<RegionSegment> result = Segmenter.segment(original,parameters);
			MemoryCleaner.clean(original);
			return result;
		} else {
			System.err.println(
					"Warning: Image file could not be found. Segmentation result will be empty. File: " + imagePath);
			return null;
		}
	}

	public static SegmentationSettings readSettings(byte[] settingsFile, int bookID, FilePathManager fileManager, FileDatabase database) {
		SegmentationSettings settings = null;

		try(ByteArrayInputStream stream = new ByteArrayInputStream(settingsFile)){
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document document = dBuilder.parse(stream);

			Book book = database.getBook(bookID);
			Page page = book.getPage(0);

			Parameters parameters = SegmentationSettingsReader.loadSettings(document, new Size(page.getWidth(),page.getHeight()));

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
}
