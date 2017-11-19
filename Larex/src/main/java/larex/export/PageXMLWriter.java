package larex.export;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import larex.segmentation.result.ResultRegion;
import larex.dataManagement.Page;

public class PageXMLWriter {

	/**
	 * PageXML 2017
	 * 
	 * @param document
	 * @param coordsElement
	 * @param pointMat
	 * @param scaleFactor
	 */
	public static void addPoints2017(Document document, Element coordsElement, MatOfPoint pointMat,
			double scaleFactor) {
		Point[] points = pointMat.toArray();
		String pointCoords = "";

		if (scaleFactor == 0) {
			scaleFactor = 1;
		}

		for (int i = 0; i < points.length; i++) {
			int x = (int) (scaleFactor * points[i].x);
			int y = (int) (scaleFactor * points[i].y);

			pointCoords += x + "," + y + " ";
		}

		pointCoords = pointCoords.substring(0, pointCoords.length() - 1);
		coordsElement.setAttribute("points", pointCoords);
	}

	/**
	 * PageXML 2010
	 * 
	 * @param document
	 * @param coordsElement
	 * @param pointMat
	 * @param scaleFactor
	 */
	public static void addPoints2010(Document document, Element coordsElement, MatOfPoint pointMat,
			double scaleFactor) {
		Point[] points = pointMat.toArray();

		if (scaleFactor == 0) {
			scaleFactor = 1;
		}

		for (int i = 0; i < points.length; i++) {
			Element pointElement = document.createElement("Point");
			pointElement.setAttribute("x", "" + (int) (scaleFactor * points[i].x));
			pointElement.setAttribute("y", "" + (int) (scaleFactor * points[i].y));
			coordsElement.appendChild(pointElement);
		}
	}

	public static void addRegion(Document document, Element pageElement, Page page, ResultRegion region, int regionCnt,
			String pageXMLVersion) {
		Element regionElement = null;
		String regionType = region.getType().toString().toLowerCase();
		regionType = regionType.replace("_", "-");

		if (regionType.equals("image")) {
			regionElement = document.createElement("ImageRegion");
		} else {
			regionElement = document.createElement("TextRegion");
			regionElement.setAttribute("type", regionType);
		}

		regionElement.setAttribute("id", "r" + regionCnt);
		Element coordsElement = document.createElement("Coords");

		switch (pageXMLVersion) {
		case "2017-07-15":
			addPoints2017(document, coordsElement, region.getPoints(), page.getScaleFactor());
			break;
		case "2010-03-19":
		default:
			addPoints2010(document, coordsElement, region.getPoints(), page.getScaleFactor());
			break;
		}
		regionElement.appendChild(coordsElement);
		pageElement.appendChild(regionElement);
	}

	public static void addRegions(Document document, Element pageElement, Page page, String pageXMLVersion) {
		int regionCnt = 0;

		ArrayList<ResultRegion> readingOrder = page.getSegmentationResult().getReadingOrder();
		ArrayList<ResultRegion> allRegions = page.getSegmentationResult().getRegions();
		allRegions.removeAll(readingOrder);

		for (ResultRegion region : readingOrder) {
			addRegion(document, pageElement, page, region, regionCnt, pageXMLVersion);
			regionCnt++;
		}

		for (ResultRegion region : allRegions) {
			addRegion(document, pageElement, page, region, regionCnt, pageXMLVersion);
			regionCnt++;
		}
	}

	public static void writePageXML(Page page, String outputFolder, String pageXMLVersion) {
		Document pageXML = getPageXML(page, pageXMLVersion);
		if (pageXML != null) {
			saveDocument(pageXML, page.getFileName(), outputFolder);
		}
	}

	/**
	 * Get a pageXML document out of a page
	 * 
	 * @param page
	 * @param outputFolder
	 * @param tempResult
	 * @return pageXML document or null if parse error
	 */
	public static Document getPageXML(Page page, String pageXMLVersion) {
		if (!pageXMLVersion.equals("2017-07-15") && !pageXMLVersion.equals("2010-03-19")) {
			pageXMLVersion = "2010-03-19";
		}
		try {
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			Document document = docBuilder.newDocument();

			Element rootElement = document.createElement("PcGts");
			rootElement.setAttribute("xmlns", "http://schema.primaresearch.org/PAGE/gts/pagecontent/" + pageXMLVersion);
			rootElement.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
			rootElement.setAttribute("xsi:schemaLocation",
					"http://schema.primaresearch.org/PAGE/gts/pagecontent/" + pageXMLVersion
							+ " http://schema.primaresearch.org/PAGE/gts/pagecontent/" + pageXMLVersion
							+ "/pagecontent.xsd");

			document.appendChild(rootElement);

			Element metadataElement = document.createElement("Metadata");
			Element creatorElement = document.createElement("Creator");
			Element createdElement = document.createElement("Created");
			Element changedElement = document.createElement("LastChange");

			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
			String date = sdf.format(new Date());

			Node creatorTextNode = document.createTextNode("Christian Reul");
			Node createdTextNode = document.createTextNode(date);
			Node changedTextNode = document.createTextNode(date);

			creatorElement.appendChild(creatorTextNode);
			createdElement.appendChild(createdTextNode);
			changedElement.appendChild(changedTextNode);

			metadataElement.appendChild(creatorElement);
			metadataElement.appendChild(createdElement);
			metadataElement.appendChild(changedElement);

			rootElement.appendChild(metadataElement);

			Element pageElement = document.createElement("Page");
			pageElement.setAttribute("imageFilename",
					page.getImagePath().substring(page.getImagePath().lastIndexOf(File.separator) + 1));

			pageElement.setAttribute("imageWidth", "" + (int) page.getOriginal().size().width);
			pageElement.setAttribute("imageHeight", "" + (int) page.getOriginal().size().height);
			rootElement.appendChild(pageElement);

			// ReadingOrder
			if (page.getSegmentationResult().getReadingOrder().size() > 0) {
				Element readingOrderElement = document.createElement("ReadingOrder");
				pageElement.appendChild(readingOrderElement);

				Element orderedGroupElement = document.createElement("OrderedGroup");
				orderedGroupElement.setAttribute("id", "ro" + System.currentTimeMillis());
				readingOrderElement.appendChild(orderedGroupElement);

				ArrayList<ResultRegion> readingOrder = page.getSegmentationResult().getReadingOrder();
				for (int index = 0; index < readingOrder.size(); index++) {
					Element regionRefElement = document.createElement("RegionRefIndexed");
					regionRefElement.setAttribute("regionRef", "r" + index);
					regionRefElement.setAttribute("index", "" + index);
					orderedGroupElement.appendChild(regionRefElement);
				}
			}

			addRegions(document, pageElement, page, pageXMLVersion);

			return document;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Save a document in a outputFolder
	 * 
	 * @param document
	 * @param fileName
	 * @param outputFolder
	 */
	public static void saveDocument(Document document, String fileName, String outputFolder) {
		try {
			// write content into xml file
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(document);

			if (!outputFolder.endsWith(File.separator)) {
				outputFolder += File.separator;
			}

			String outputPath = outputFolder;

			if (!outputFolder.endsWith(".xml")) {
				outputPath += fileName + ".xml";
			}

			StreamResult result = new StreamResult(new File(outputPath));
			transformer.transform(source, result);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}