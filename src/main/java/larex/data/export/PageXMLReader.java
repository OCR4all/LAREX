package larex.data.export;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.primaresearch.dla.page.Page;
import org.primaresearch.dla.page.io.FileInput;
import org.primaresearch.dla.page.io.xml.XmlPageReader;
import org.primaresearch.dla.page.layout.logical.Group;
import org.primaresearch.dla.page.layout.logical.GroupMember;
import org.primaresearch.dla.page.layout.logical.RegionRef;
import org.primaresearch.dla.page.layout.physical.Region;
import org.primaresearch.dla.page.layout.physical.text.LowLevelTextObject;
import org.primaresearch.dla.page.layout.physical.text.impl.TextLine;
import org.primaresearch.dla.page.layout.physical.text.impl.TextRegion;
import org.primaresearch.dla.page.layout.physical.text.impl.Word;
import org.primaresearch.io.UnsupportedFormatVersionException;
import org.primaresearch.maths.geometry.Polygon;
import org.w3c.dom.Document;

import larex.geometry.regions.RegionSegment;
import larex.geometry.regions.type.PAGERegionType;
import larex.geometry.regions.type.RegionSubType;
import larex.geometry.regions.type.RegionType;
import larex.geometry.regions.type.TypeConverter;
import larex.segmentation.SegmentationResult;

public class PageXMLReader {

	public static SegmentationResult getSegmentationResult(Document document) {
		// Convert document to PAGE xml Page
		XmlPageReader reader = new XmlPageReader(null); // null ^= without validation
		Page page = null;
		try {
			File tempPAGExml = File.createTempFile("larex_pagexml-", ".xml");
			tempPAGExml.deleteOnExit();

			// Save page xml in temp file
			FileOutputStream output = new FileOutputStream(new File(tempPAGExml.getAbsolutePath()));
			StreamResult result = new StreamResult(output);
			// Write document on disk
			TransformerFactory.newInstance().newTransformer().transform(new DOMSource(document), result);
			output.close();

			// Read into PAGE xml
			page = reader.read(new FileInput(tempPAGExml));
			tempPAGExml.delete();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (UnsupportedFormatVersionException e) {
			e.printStackTrace();
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
		} catch (TransformerException e) {
			e.printStackTrace();
		}

		// Read PAGE xml into Segmentation Result
		if (page != null) {
			Map<String, RegionSegment> resRegions = new HashMap<>();
			// Read regions
			for (Region region : page.getLayout().getRegionsSorted()) {
				// Get Type
				RegionType type = TypeConverter.stringToMainType(region.getType().getName());
				RegionSubType subtype = null;
				if (type.equals(RegionType.TextRegion)) {
					TextRegion textRegion = (TextRegion) region;
					subtype = TypeConverter.stringToSubType((textRegion).getTextType());

					// Extract Text
					for (LowLevelTextObject text : textRegion.getTextObjectsSorted()) {
						if (text instanceof TextLine) {
							TextLine textLine = (TextLine) text;

							// Get Coords of TextLine
							ArrayList<Point> pointList = new ArrayList<Point>();
							Polygon coords = textLine.getCoords();
							for (int i = 0; i < coords.getSize(); i++) {
								org.primaresearch.maths.geometry.Point point = coords.getPoint(i);
								Point newPoint = new Point(point.x, point.y);
								pointList.add(newPoint);
							}

							//for (LowLevelTextObject textChild : textLine.getTextObjectsSorted()) {
							//}
						}

						// TextLine textline = new TextLine(text.getId(),pointList,text.getText())

						// TextLine textline = new TextLine(text.getId(),pointList,text.getText())
					}
				}

				// Get Coords
				ArrayList<Point> pointList = new ArrayList<Point>();
				Polygon coords = region.getCoords();
				for (int i = 0; i < coords.getSize(); i++) {
					org.primaresearch.maths.geometry.Point point = coords.getPoint(i);
					Point newPoint = new Point(point.x, point.y);
					pointList.add(newPoint);
				}

				Point[] pointArray = new Point[pointList.size()];
				MatOfPoint points = new MatOfPoint(pointList.toArray(pointArray));

				// Id
				String id = region.getId().toString();
				if (!points.empty()) {
					resRegions.put(id, new RegionSegment(new PAGERegionType(type, subtype), points, id));
				}
			}
			SegmentationResult segResult = new SegmentationResult(new ArrayList<>(resRegions.values()));

			// Set reading order
			if (page.getLayout().getReadingOrder() != null) {
				Group readingOrder = page.getLayout().getReadingOrder().getRoot();
				ArrayList<RegionSegment> newReadingOrder = new ArrayList<RegionSegment>();
				for (int i = 0; i < readingOrder.getSize(); i++) {
					GroupMember member = readingOrder.getMember(i);
					if (member instanceof RegionRef) {
						newReadingOrder.add(resRegions.get(((RegionRef) member).getRegionId().toString()));
					}
				}
				segResult.setReadingOrder(newReadingOrder);
			}
			return segResult;
		}

		return null;
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
}
