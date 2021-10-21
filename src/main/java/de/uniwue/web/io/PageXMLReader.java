package de.uniwue.web.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;


import de.uniwue.web.model.Polygon;
import org.primaresearch.dla.page.Page;
import org.primaresearch.dla.page.io.xml.DefaultXmlNames;
import org.primaresearch.dla.page.io.xml.PageXmlInputOutput;
import org.primaresearch.dla.page.layout.logical.Group;
import org.primaresearch.dla.page.layout.logical.GroupMember;
import org.primaresearch.dla.page.layout.logical.RegionRef;
import org.primaresearch.dla.page.layout.physical.Region;
import org.primaresearch.dla.page.layout.physical.impl.CustomRegion;
import org.primaresearch.dla.page.layout.physical.impl.NoiseRegion;
import org.primaresearch.dla.page.layout.physical.text.LowLevelTextObject;
import org.primaresearch.dla.page.layout.physical.text.impl.Glyph;
import org.primaresearch.dla.page.layout.physical.text.impl.TextContentVariants.TextContentVariant;
import org.primaresearch.dla.page.layout.physical.text.impl.TextLine;
import org.primaresearch.dla.page.layout.physical.text.impl.TextRegion;
import org.primaresearch.dla.page.layout.physical.text.impl.Word;
import org.primaresearch.dla.page.metadata.MetaData;
import org.primaresearch.shared.variable.IntegerValue;
import org.primaresearch.shared.variable.IntegerVariable;
import org.primaresearch.shared.variable.Variable;

import de.uniwue.algorithm.geometry.regions.type.PAGERegionType;
import de.uniwue.algorithm.geometry.regions.type.RegionSubType;
import de.uniwue.algorithm.geometry.regions.type.RegionType;
import de.uniwue.algorithm.geometry.regions.type.TypeConverter;
import de.uniwue.web.communication.SegmentationStatus;
import de.uniwue.web.model.PageAnnotations;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * PageXMLReader is a converter for PageXML files into the PageAnnotations
 * format used in this tool.
 */
public class PageXMLReader {

	/**
	 * Read a document to extract the PageAnnotations
	 *
	 * @param sourceFilename PageXML document
	 * @return PageAnnotations inside the document
	 */
	public static PageAnnotations getPageAnnotations(File sourceFilename){
		// Convert document to PAGE xml Page
		Page page = readPAGE(sourceFilename);

		// Accounts for an edge case in previous LAREX versions which would lead to non-valid PAGE XML by inserting an empty
		// TextEquiv-element before TextLines in a TextRegion. The related bug was fixed.
		// TODO: Remove in future versions as LAREX doesn't produce invalid PAGE XML anymore
		if(page == null){
			try {
				fixFaultyPAGE(sourceFilename);
				page = readPAGE(sourceFilename);
			}catch (ParserConfigurationException | IOException | SAXException | XPathExpressionException e) {
				e.printStackTrace();
				System.err.println("Could not load source PAGE XML file: " + sourceFilename);
			}
		}



		// Read PAGE xml into Segmentation Result
		if (page != null) {
			// Read Metadata
			de.uniwue.web.model.MetaData metaData = new de.uniwue.web.model.MetaData(page.getMetaData());

			// Read Page Orientation
			double pageOrientation = getOrientation(page);
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

				if (type != null && type.equals(RegionType.TextRegion)) {
					TextRegion textRegion = (TextRegion) region;
					if(textRegion.getAttributes().get("type").getValue() != null && textRegion.getTextType() != null) {
						subtype = TypeConverter.stringToSubType(textRegion.getTextType());
					}else{
						subtype= null;
					}

					// Extract Text
					for (LowLevelTextObject text : textRegion.getTextObjectsSorted()) {
						if (text instanceof TextLine) {
							final TextLine textLine = (TextLine) text;
							final String id = text.getId().toString();

							//get Words of TextLine if they exist
							final List<de.uniwue.web.model.Word> words = new ArrayList<>();
							// Adding empty polygon to minimize json size
							// This should  be changed if coords for glyphs are required in frontend
							// new de.uniwue.web.model.Polygon(primaGlyph.getCoords())
							// new de.uniwue.web.model.Polygon(primaWord.getCoords())
							Polygon emptyPolygon = new Polygon(new ArrayList<de.uniwue.web.model.Point>());
							if(((TextLine) text).hasTextObjects()) {
								for(int i = 0; i < ((TextLine) text).getTextObjectCount(); i++) {
									Word primaWord =(Word) ((TextLine) text).getTextObject(i);

									// get Glyphs of Word if they exist
									final List<de.uniwue.web.model.Glyph> glyphs = new ArrayList<>();
									for(int j = 0; j < primaWord.getTextObjectCount(); j++) {
										Glyph primaGlyph = (Glyph) primaWord.getTextObject(j);
										glyphs.add(new de.uniwue.web.model.Glyph(primaGlyph.getId().toString(),
														emptyPolygon,
														primaGlyph.getText(),
														primaGlyph.getConfidence()));
									}
									words.add(new de.uniwue.web.model.Word(primaWord.getId().toString(),
													emptyPolygon,
													primaWord.getText(),
													primaWord.getConfidence(),
													glyphs));
								}
							}

							// Get Coords of TextLine
							de.uniwue.web.model.Polygon textlineCoords = new de.uniwue.web.model.Polygon(textLine.getCoords());

							// Get Baseline of TextLine if it exists
							de.uniwue.web.model.Polygon textlineBaseline = (textLine.getBaseline() != null) ? new de.uniwue.web.model.Polygon(textLine.getBaseline()) : null;

							//// TextLine text content
							final Map<Integer,String> content = new HashMap<>();
							// List of all unindexed text contents
							final List<String> unindexedContent = new ArrayList<>();
							int highestIndex = -1;
							for(int i = 0; i < textLine.getTextContentVariantCount(); i++) {
								TextContentVariant textContent = (TextContentVariant) textLine.getTextContentVariant(i);

								if(textContent.getText() != null) {
									Variable indexVariable = textContent.getAttributes().get(DefaultXmlNames.ATTR_index);
									if(indexVariable instanceof IntegerVariable && indexVariable.getValue() != null) {
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

							textLines.put(id, new de.uniwue.web.model.TextLine(id, textlineCoords, content, textlineBaseline, words));
							readingOrder.add(id);
						}

					}
				}

				// Get Coords
				de.uniwue.web.model.Polygon regionCoords = new de.uniwue.web.model.Polygon(region.getCoords());

				// Id
				String id = region.getId().toString();
				if (!regionCoords.getPoints().isEmpty()) {
					resRegions.put(id, new de.uniwue.web.model.Region(id, new PAGERegionType(type, subtype).toString(),
							orientation, regionCoords, textLines, readingOrder));
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
			return new PageAnnotations(pageName, sourceFilename.getName(), width, height, metaData, resRegions,
					SegmentationStatus.LOADED, newReadingOrder, pageOrientation , false);
		}

		return null;
	}

	public static Page readPAGE(File sourceFilename){
		Page page = null;
		try{
			page = PageXmlInputOutput.readPage(String.valueOf(sourceFilename));
		}catch(Exception e){
			e.printStackTrace();
			System.err.println("Could not load source PAGE XML file: " + sourceFilename);
		}

		return page;
	}

	private static void fixFaultyPAGE(File sourceFilename) throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

		dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		DocumentBuilder db = dbf.newDocumentBuilder();

		Document doc = db.parse(sourceFilename);

		XPath xPath = XPathFactory.newInstance().newXPath();

		NodeList textregions = doc.getElementsByTagName("TextRegion");

		for (int temp = 0; temp < textregions.getLength(); temp++) {
			NodeList nodeList = (NodeList) xPath.compile("./TextEquiv").evaluate(textregions.item(temp), XPathConstants.NODESET);
			if (nodeList.getLength() == 1) {
				Node possiblyFaultyNode = nodeList.item(0);

				NodeList TextLineList = (NodeList) xPath.compile("./following-sibling::TextLine[1]").evaluate(possiblyFaultyNode, XPathConstants.NODESET);
				if (TextLineList.getLength() > 0) {
					possiblyFaultyNode.getParentNode().removeChild(possiblyFaultyNode);
				}
			}
		}

		doc.normalize();

		try (FileOutputStream output = new FileOutputStream(sourceFilename)) {
			writeXml(doc, output);
		} catch (IOException | TransformerException e) {
			e.printStackTrace();
		}
	}

	private static void writeXml(Document doc,
								 OutputStream output)
			throws TransformerException {

		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(output);

		transformer.transform(source, result);
	}

	private static HashMap<String, String> buildMetadataHashmap(MetaData metaData){
		HashMap<String, String> metaDataMap = new HashMap<>();

		metaDataMap.put("Creator", metaData.getCreator());
		metaDataMap.put("Comments", metaData.getComments());
		metaDataMap.put("ExternalRef", metaData.getExternalRef());
		metaDataMap.put("FormattedCreationTime", metaData.getFormattedCreationTime());
		metaDataMap.put("FormattedLastModificationTime", metaData.getFormattedLastModificationTime());

		return metaDataMap;
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
			segResult = getPageAnnotations(pageXMLInputPath);
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Reading XML file failed!");
		}

		return segResult;
	}

	/**
	 * Read the Orientation of a PageXML Document
	 *
	 * @param page	PageXML
	 * @return orientation skew angle of page element
	 */
	public static double getOrientation(Page page){
		if(page.getAttributes().get("orientation") != null && page.getAttributes().get("orientation").getValue() != null){
			return Double.parseDouble(page.getAttributes().get("orientation").getValue().toString());
		}
		return 0.0;
	}
}
