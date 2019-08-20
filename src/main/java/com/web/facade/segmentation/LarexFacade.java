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
 *  Facade between the LAREX Segmentation Algorithm and the Web GUI
 */
public class LarexFacade {

	/**
	 * Segment a page with the LAREX segmentation algorithm
	 * 
	 * @param settings Segmentation settings from the web gui
	 * @param pageNr Page to segment
	 * @param fileManager filePathManager to find a corresponding image path ĺocally
	 * @param database database with all books and pages
	 * @return
	 */
	public static PageAnnotations segmentPage(SegmentationSettings settings, int pageNr,
			FilePathManager fileManager, FileDatabase database) {
		final Page page = database.getBook(settings.getBookID()).getPage(pageNr);
		
		PageAnnotations segmentation = null;
		Collection<RegionSegment> segmentationResult = null;
		String imagePath = fileManager.getLocalBooksPath() + File.separator + page.getImages().get(0);

		File imageFile = new File(imagePath);
		if (imageFile.exists()) {
			Mat original = ImageLoader.readOriginal(imageFile);

			Parameters parameters = settings.toParameters(original.size());

			Collection<RegionSegment> result = Segmenter.segment(original,parameters);
			MemoryCleaner.clean(original);
			segmentationResult = result;
		} else {
			System.err.println(
					"Warning: Image file could not be found. Segmentation result will be empty. File: " + imagePath);
		}

		if (segmentationResult != null) {
			segmentation = new PageAnnotations(page.getName(), page.getWidth(), page.getHeight(),
					page.getId(), segmentationResult, SegmentationStatus.SUCCESS);
		} else {
			segmentation = new PageAnnotations(page.getName(), page.getWidth(), page.getHeight(),
					new HashMap<String, Region>(), SegmentationStatus.MISSINGFILE, new ArrayList<String>());
		}
		return segmentation;
	}

	/**
	 * Retrieve the settings document of the segmentation setting
	 * 
	 * @param settings Segmentation settings from the web gui
	 * @return
	 */
	public static Document getSettingsXML(SegmentationSettings settings) {
		return SegmentationSettingsWriter.getSettingsXML(settings.toParameters(new Size()));
	}

	/**
	 * Read the segmentation settings from byte format into Web Segmentation Settings
	 * 
	 * @param settingsFile bytes of a segmentation settings file
	 * @param bookID book from with to take an example page (page size)
	 * @param fileManager filePathManager to find a corresponding image path ĺocally
	 * @param database database with all books and pages
	 * @return
	 */
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
		} catch (SAXException | IOException | ParserConfigurationException e) {
			e.printStackTrace();
		}
		return settings;
	}
}
