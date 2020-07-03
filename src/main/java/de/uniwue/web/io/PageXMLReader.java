package de.uniwue.web.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
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
import org.primaresearch.dla.page.layout.physical.impl.CustomRegion;
import org.primaresearch.dla.page.layout.physical.impl.NoiseRegion;
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

import de.uniwue.algorithm.geometry.regions.type.PAGERegionType;
import de.uniwue.algorithm.geometry.regions.type.RegionSubType;
import de.uniwue.algorithm.geometry.regions.type.RegionType;
import de.uniwue.algorithm.geometry.regions.type.TypeConverter;
import de.uniwue.web.communication.SegmentationStatus;
import de.uniwue.web.model.PageAnnotations;
import de.uniwue.web.model.Point;

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
		} catch (IOException | UnsupportedFormatVersionException | TransformerException e) {
			e.printStackTrace();
		}

		// Read PAGE xml into Segmentation Result
		if (page != null) {
			Map<String, de.uniwue.web.model.Region> resRegions = new HashMap<>();
			// Read regions
			for (Region region : page.getLayout().getRegionsSorted()) {
				// Get Type
				RegionType type = TypeConverter.stringToMainType(region.getType().getName());
				RegionSubType subtype = null;
				
				Double orientation = !(region instanceof CustomRegion || region instanceof NoiseRegion || type == RegionType.UnknownRegion) ?
						PrimaLibHelper.getOrientation(region) : null;
				
				final Map<String,de.uniwue.web.model.TextLine> textLines = new HashMap<>();
				final List<String> readingOrder = new ArrayList<>();
				
				if (type.equals(RegionType.TextRegion)) {
					TextRegion textRegion = (TextRegion) region;
                                        if (textRegion.getTextType() != null) {
                                            subtype = TypeConverter.stringToSubType(textRegion.getTextType());
                                        }

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
									if(indexVariable != null && indexVariable instanceof IntegerVariable && indexVariable.getValue() != null) {
										final int index = ((IntegerValue)(indexVariable).getValue()).val;
										content.put(index, textContent.getText());
										highestIndex = Math.max(index, highestIndex);
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
							
							textLines.put(id, new de.uniwue.web.model.TextLine(id,pointList,content));
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
					resRegions.put(id, new de.uniwue.web.model.Region(id, new PAGERegionType(type, subtype).toString(),
							pointList, orientation, false, textLines, readingOrder));
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
			
			// Pagename
			final String imageName = page.getImageFilename();
			final String pageName = imageName.lastIndexOf(".") > 0 ? 
					imageName.substring(0, imageName.lastIndexOf(".")) : imageName;
			
			return new PageAnnotations(pageName, width, height, resRegions,
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
	public static PageAnnotations loadPageAnnotationsFromDisc(File pageXMLInputPath) {
		PageAnnotations segResult = null;

		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document document = dBuilder.parse(pageXMLInputPath);
			segResult = getPageAnnotations(document);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Reading XML file failed!");
		}

		return segResult;
	}
}
