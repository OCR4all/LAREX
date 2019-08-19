package com.web.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.primaresearch.dla.page.Page;
import org.primaresearch.dla.page.io.xml.PageXmlInputOutput;
import org.primaresearch.dla.page.io.xml.StreamTarget;
import org.primaresearch.dla.page.layout.PageLayout;
import org.primaresearch.dla.page.layout.logical.ReadingOrder;
import org.primaresearch.dla.page.layout.physical.Region;
import org.primaresearch.dla.page.layout.physical.shared.RegionType;
import org.primaresearch.dla.page.layout.physical.text.TextContent;
import org.primaresearch.dla.page.layout.physical.text.impl.TextLine;
import org.primaresearch.dla.page.layout.physical.text.impl.TextRegion;
import org.primaresearch.dla.page.metadata.MetaData;
import org.primaresearch.ident.Id;
import org.primaresearch.ident.IdRegister.InvalidIdException;
import org.primaresearch.io.UnsupportedFormatVersionException;
import org.primaresearch.io.xml.XmlFormatVersion;
import org.primaresearch.maths.geometry.Polygon;
import org.primaresearch.shared.variable.IntegerValue;
import org.primaresearch.shared.variable.IntegerVariable;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.web.model.PageAnnotations;
import com.web.model.Point;

import larex.geometry.regions.type.PAGERegionType;
import larex.geometry.regions.type.TypeConverter;

/**
 * PageXMLWriter is a converter for the PageAnnotations used in this tool 
 * to PageXML annotations.
 */
public class PageXMLWriter {

	/**
	 * Get a pageXML document out of a page
	 * 
	 * @param page
	 * @param outputFolder
	 * @param tempResult
	 * @return pageXML document or null if parse error
	 * @throws UnsupportedFormatVersionException
	 * @throws InvalidIdException
	 */
	public static Document getPageXML(PageAnnotations result, String pageXMLVersion)
			throws UnsupportedFormatVersionException  {

		// Start PAGE xml
		XmlFormatVersion version = new XmlFormatVersion(pageXMLVersion);
		Page page = new Page(PageXmlInputOutput.getSchemaModel(version));

		page.setImageFilename(result.getName()+".png");

		// Create page and meta data
		MetaData metadata = page.getMetaData();
		metadata.setCreationTime(new Date());
		// metadata ChangedTime

		// Create page layout
		PageLayout layout = page.getLayout();
		layout.setSize(result.getWidth(), result.getHeight());

		// Add Regions
		Map<String, Id> idMap = new HashMap<>();
		for (Entry<String, com.web.model.Region> regionEntry : result.getSegments().entrySet()) {
			com.web.model.Region regionSegment = regionEntry.getValue();
			final PAGERegionType type = TypeConverter.stringToPAGEType(regionSegment.getType());

			RegionType regionType = TypeConverter.enumRegionTypeToPrima(type.getType());

			Region region = layout.createRegion(regionType);

			Polygon poly = new Polygon();

			for (Point point : regionSegment.getPoints()) {
				poly.addPoint((int) point.getX(), (int) point.getY());
			}

			// Check for TextRegion
			if (regionType.getName().equals(RegionType.TextRegion.getName())) {
				final TextRegion textRegion = ((TextRegion) region);
				textRegion.setTextType(TypeConverter.subTypeToString(type.getSubtype()));

				// Add TextLines if existing
				if(regionSegment.getTextlines() != null) {
					final List<com.web.model.TextLine> textlines = new ArrayList<>(regionSegment.getTextlines().values());
					
					// Sort textlines via reading order
					List<String> readingOrder = new ArrayList<>(regionSegment.getReadingOrder());
					Collections.reverse(readingOrder);
					readingOrder.forEach(id -> {
						com.web.model.TextLine textline = regionSegment.getTextlines().get(id);
						textlines.remove(textline);
						textlines.add(0, textline);
					});
					
					// Iterate over sorted text lines and to PAGE xml
					for (com.web.model.TextLine textline : textlines) {
						final TextLine pageTextLine = textRegion.createTextLine();
						
						final Polygon coords = new Polygon();
						for (Point point : textline.getPoints()) {
							coords.addPoint((int) point.getX(), (int) point.getY());
						}
						pageTextLine.setCoords(coords);
						
						// Add Text
						for(Entry<Integer,String> content : textline.getText().entrySet()) {
							final int id = content.getKey();
							final TextContent textContent = (id >= pageTextLine.getTextContentVariantCount()) ?
									 pageTextLine.addTextContentVariant():  pageTextLine.getTextContentVariant(id);
							textContent.setText(content.getValue());
							textContent.getAttributes().add(new IntegerVariable("index",new IntegerValue(id)));
						}
					}
				}

			}

			region.setCoords(poly);

			idMap.put(regionSegment.getId(), region.getId());
		}

		// ReadingOrder
		ReadingOrder xmlReadingOrder = layout.createReadingOrder();
		xmlReadingOrder.getRoot().setOrdered(true);
		List<String> readingOrder = result.getReadingOrder();
		for (String regionID : readingOrder) {
			xmlReadingOrder.getRoot().addRegionRef(idMap.get(regionID).toString());
		}

		// Write as Document
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		PageXmlInputOutput.getWriter(version).write(page, new StreamTarget(os));
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);

		try {
			return factory.newDocumentBuilder().parse(new ByteArrayInputStream(os.toByteArray()));
		} catch (SAXException | IOException | ParserConfigurationException e) {
			e.printStackTrace();
			return null;
		}
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

			FileOutputStream output = new FileOutputStream(new File(outputPath));
			StreamResult result = new StreamResult(output);
			transformer.transform(source, result);

			output.close();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}