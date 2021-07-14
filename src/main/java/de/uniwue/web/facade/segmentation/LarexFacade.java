package de.uniwue.web.facade.segmentation;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import de.uniwue.web.model.*;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import de.uniwue.algorithm.data.MemoryCleaner;
import de.uniwue.algorithm.geometry.regions.RegionSegment;
import de.uniwue.algorithm.segmentation.Segmenter;
import de.uniwue.algorithm.segmentation.parameters.Parameters;
import de.uniwue.web.communication.SegmentationStatus;
import de.uniwue.web.io.FileDatabase;
import de.uniwue.web.io.FilePathManager;
import de.uniwue.web.io.ImageLoader;
import de.uniwue.web.io.SegmentationSettingsReader;
import de.uniwue.web.io.SegmentationSettingsWriter;

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
		Page page;
		if(fileManager.checkFlat()) {
			page = database.getBook(settings.getBookID()).getPage(pageNr);
		} else {
			page = database.getBook(fileManager.getNonFlatBookName(), fileManager.getNonFlatBookId(), fileManager.getLocalImageMap(), fileManager.getLocalXmlMap()).getPage(pageNr);
		}
		PageAnnotations segmentation = null;
		Collection<RegionSegment> segmentationResult = null;
		String imagePath = fileManager.getLocalBooksPath() + File.separator + page.getImages().get(0);
		if(fileManager.checkFlat()) {
			imagePath = fileManager.getLocalBooksPath() + File.separator + page.getImages().get(0);
		} else {
			try{
				List<String> imagesWithExt = new LinkedList<>();
				//TODO: remove hardcoding for extension matching everytime its used
				String extensionMatchString = "(png|jpg|jpeg|tif|tiff)";
				for(Map.Entry<String ,String> entry : fileManager.getLocalImageMap().entrySet()) {
					if(entry.getKey().matches("^" + page.getName() + "\\..*")) {
						imagesWithExt.add(entry.getValue());
					} else if(entry.getValue().matches(".*" + page.getName() + "\\." + extensionMatchString)){
						imagesWithExt.add(entry.getValue());
					}
				}
				imagePath = imagesWithExt.get(0);
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

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
		// TODO fix metadata insertion here instead of frontend (?)

		page.setOrientation(settings.getParameters().get("imageOrientation").doubleValue());
		if (segmentationResult != null) {
			segmentation = new PageAnnotations(page.getName(), page.getXmlName(), page.getWidth(), page.getHeight(),
					page.getId(), new MetaData(), segmentationResult, SegmentationStatus.SUCCESS, page.getOrientation(), true);
		} else {
			segmentation = new PageAnnotations(page.getName(), page.getXmlName(), page.getWidth(), page.getHeight(), new MetaData(),
					new HashMap<String, Region>(), SegmentationStatus.MISSINGFILE, new ArrayList<String>(), page.getOrientation(), true);
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
