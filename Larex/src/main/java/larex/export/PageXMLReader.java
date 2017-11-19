package larex.export;

import java.io.File;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import larex.helper.TypeConverter;
import larex.regions.type.RegionType;
import larex.segmentation.result.ResultRegion;
import larex.segmentation.result.SegmentationResult;

public class PageXMLReader {

	public static SegmentationResult getSegmentationResult(Document document) {
		SegmentationResult segResult = null;

		ArrayList<ResultRegion> resRegions = new ArrayList<ResultRegion>();

		document.getDocumentElement().normalize();

		NodeList textRegions = document.getElementsByTagName("TextRegion");
		NodeList imageRegions = document.getElementsByTagName("ImageRegion");

		for (int i = 0; i < textRegions.getLength(); i++) {
			ResultRegion newRegion = extractRegion(textRegions.item(i), true);
			resRegions.add(newRegion);
		}

		for (int i = 0; i < imageRegions.getLength(); i++) {
			ResultRegion newRegion = extractRegion(imageRegions.item(i), false);
			resRegions.add(newRegion);
		}

		segResult = new SegmentationResult(resRegions);
		ArrayList<ResultRegion> readingOrder = new ArrayList<ResultRegion>();
		NodeList readingOrderXML = document.getElementsByTagName("ReadingOrder");
		//TODO
		if(readingOrderXML.getLength() > 0) {
			for (ResultRegion region : resRegions) {
				if (!region.getType().equals(RegionType.image)) {
					readingOrder.add(region);
				}
			}
		}
		segResult.setReadingOrder(readingOrder);

		return segResult;
	}

	public static SegmentationResult loadSegmentationResultFromDisc(String pageXMLInputPath) {
		SegmentationResult segResult = null;

		try {
			File inputFile = new File(pageXMLInputPath);
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document document = dBuilder.parse(inputFile);
			segResult = getSegmentationResult(document);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Reading XML file failed!");
		}

		return segResult;
	}

	private static ResultRegion extractRegion(Node regionNode, boolean isTextRegion) {
		Element regionElement = (Element) regionNode;
		RegionType type = RegionType.image;

		if (isTextRegion) {
			String typeString = regionElement.getAttribute("type");
			type = TypeConverter.stringToType(typeString);
		}

		Element coords = (Element) regionElement.getElementsByTagName("Coords").item(0);
		MatOfPoint points = null;
		if (!coords.hasAttribute("points")) {
			points = extractPoints2010(coords);
		} else {
			points = extractPoints2017(coords);
		}

		ResultRegion region = new ResultRegion(type, points);

		return region;
	}

	private static MatOfPoint extractPoints2010(Element coords) {
		ArrayList<Point> pointList = new ArrayList<Point>();
		NodeList pointNodes = coords.getElementsByTagName("Point");

		for (int i = 0; i < pointNodes.getLength(); i++) {
			Element pointElement = (Element) pointNodes.item(i);
			int x = Integer.parseInt(pointElement.getAttribute("x"));
			int y = Integer.parseInt(pointElement.getAttribute("y"));

			Point point = new Point(x, y);
			pointList.add(point);
		}

		Point[] pointArray = new Point[pointList.size()];
		MatOfPoint points = new MatOfPoint(pointList.toArray(pointArray));

		return points;
	}

	private static MatOfPoint extractPoints2017(Element coords) {
		String pointsString = coords.getAttribute("points");
		ArrayList<Point> pointList = new ArrayList<Point>();

		boolean finished = false;

		while (!finished) {
			int spacePosition = pointsString.indexOf(" ");

			if (spacePosition > 0) {
				String pointString = pointsString.substring(0, spacePosition);
				pointsString = pointsString.substring(spacePosition + 1);

				int x = Integer.parseInt(pointString.substring(0, pointString.indexOf(",")));
				int y = Integer.parseInt(pointString.substring(pointString.indexOf(",") + 1));

				Point newPoint = new Point(x, y);
				pointList.add(newPoint);
			} else {
				int x = Integer.parseInt(pointsString.substring(0, pointsString.indexOf(",")));
				int y = Integer.parseInt(pointsString.substring(pointsString.indexOf(",") + 1));

				Point newPoint = new Point(x, y);
				pointList.add(newPoint);

				finished = true;
			}
		}

		Point[] pointArray = new Point[pointList.size()];
		MatOfPoint points = new MatOfPoint(pointList.toArray(pointArray));

		return points;
	}

}
