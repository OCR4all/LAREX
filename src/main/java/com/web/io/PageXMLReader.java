package com.web.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.primaresearch.dla.page.Page;
import org.primaresearch.dla.page.io.FileInput;
import org.primaresearch.dla.page.io.xml.DefaultXmlNames;
import org.primaresearch.dla.page.io.xml.XmlPageReader;
import org.primaresearch.dla.page.layout.logical.Group;
import org.primaresearch.dla.page.layout.logical.GroupMember;
import org.primaresearch.dla.page.layout.logical.RegionRef;
import org.primaresearch.dla.page.layout.physical.Region;
import org.primaresearch.dla.page.layout.physical.text.LowLevelTextObject;
import org.primaresearch.dla.page.layout.physical.text.impl.TextContentVariants.TextContentVariant;
import org.primaresearch.dla.page.layout.physical.text.impl.TextLine;
import org.primaresearch.dla.page.layout.physical.text.impl.TextRegion;
import org.primaresearch.io.UnsupportedFormatVersionException;
import org.primaresearch.maths.geometry.Polygon;
import org.primaresearch.shared.variable.IntegerValue;
import org.primaresearch.shared.variable.IntegerVariable;
import org.primaresearch.shared.variable.Variable;
import org.w3c.dom.Document;

import com.web.communication.SegmentationStatus;
import com.web.model.PageAnnotations;
import com.web.model.Point;

import larex.geometry.regions.type.PAGERegionType;
import larex.geometry.regions.type.RegionSubType;
import larex.geometry.regions.type.RegionType;
import larex.geometry.regions.type.TypeConverter;

/**
 * PageXMLReader is a converter for PageXML files into the PageAnnotations
 * format used in this tool.
 */
public class PageXMLReader {

	/**
	 * Read a document to extract the PageAnnotations
	 * 
	 * @param document PageXML document
	 * @return PageAnnotations inside the document
	 */
	public static PageAnnotations getPageAnnotations(Document document) {
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
			Map<String, com.web.model.Region> resRegions = new HashMap<>();
			// Read regions
			for (Region region : page.getLayout().getRegionsSorted()) {
				// Get Type
				RegionType type = TypeConverter.stringToMainType(region.getType().getName());
				RegionSubType subtype = null;
				final Map<String,com.web.model.TextLine> textLines = new HashMap<>();
				final List<String> readingOrder = new ArrayList<>();
				
				if (type.equals(RegionType.TextRegion)) {
					TextRegion textRegion = (TextRegion) region;
					subtype = TypeConverter.stringToSubType((textRegion).getTextType());

					// Extract Text
					for (LowLevelTextObject text : textRegion.getTextObjectsSorted()) {
						if (text instanceof TextLine) {
							final TextLine textLine = (TextLine) text;
							final String id = text.getId().toString();

							// Get Coords of TextLine
							LinkedList<Point> pointList = new LinkedList<>();
							Polygon coords = textLine.getCoords();
							for (int i = 0; i < coords.getSize(); i++) {
								org.primaresearch.maths.geometry.Point point = coords.getPoint(i);
								Point newPoint = new Point(point.x, point.y);
								pointList.add(newPoint);
							}

							//// TextLine text content
							final Map<Integer,String> content = new HashMap<>();
							// List of all unindexed text contents
							final List<String> unindexedContent = new ArrayList<>();
							int highestIndex = -1;
							for(int i = 0; i < textLine.getTextContentVariantCount(); i++) {
								TextContentVariant textContent = (TextContentVariant) textLine.getTextContentVariant(i);

								if(textContent.getText() != null) { 
									Variable indexVariable = textContent.getAttributes().get(DefaultXmlNames.ATTR_index);
									if(indexVariable != null && indexVariable instanceof IntegerVariable) {
										//TODO currently no index can be read every index is undefined / null
										final int index = ((IntegerValue)(indexVariable).getValue()).val;
										content.put(index, textContent.getText());
										highestIndex = index > highestIndex ? index : highestIndex;
									} else {
										unindexedContent.add(textContent.getText());
									}
								};
							}

							if(content.size() == 0 && unindexedContent.size() == 1) {
								content.put(1, unindexedContent.get(0));
							} else {
								// Give all unindexed content an index starting above the highest recorded index in the bunch (min 0)
								for(String contentString : unindexedContent) {
									content.put(++highestIndex, contentString);
								}
							}
							
							textLines.put(id, new com.web.model.TextLine(id,pointList,content));
							readingOrder.add(id);
						}

					}
				}

				// Get Coords
				LinkedList<Point> pointList = new LinkedList<>();
				Polygon coords = region.getCoords();
				for (int i = 0; i < coords.getSize(); i++) {
					org.primaresearch.maths.geometry.Point point = coords.getPoint(i);
					Point newPoint = new Point(point.x, point.y);
					pointList.add(newPoint);
				}

				// Id
				String id = region.getId().toString();
				if (!pointList.isEmpty()) {
					resRegions.put(id, new com.web.model.Region(id, new PAGERegionType(type, subtype).toString(),
							pointList, false, textLines, readingOrder));
				}
			}
			final int height = page.getLayout().getHeight();
			final int width = page.getLayout().getWidth();

			ArrayList<String> newReadingOrder = new ArrayList<>();
			// Set reading order
			if (page.getLayout().getReadingOrder() != null) {
				Group readingOrder = page.getLayout().getReadingOrder().getRoot();
				for (int i = 0; i < readingOrder.getSize(); i++) {
					GroupMember member = readingOrder.getMember(i);
					if (member instanceof RegionRef) {
						newReadingOrder.add(((RegionRef) member).getRegionId().toString());
					}
				}
			}
			return new PageAnnotations(page.getImageFilename(), width, height, resRegions,
					SegmentationStatus.LOADED, newReadingOrder);
		}

		return null;
	}

	/**
	 * Read the PageAnnotations of a PageXML file from the disc drive
	 * 
	 * @param pageXMLInputPath Path to the PageXML file
	 * @return PageAnnotations inside the document
	 */
	public static PageAnnotations loadPageAnnotationsFromDisc(String pageXMLInputPath) {
		PageAnnotations segResult = null;

		try {
			File inputFile = new File(pageXMLInputPath);
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document document = dBuilder.parse(inputFile);
			segResult = getPageAnnotations(document);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Reading XML file failed!");
		}

		return segResult;
	}
}
