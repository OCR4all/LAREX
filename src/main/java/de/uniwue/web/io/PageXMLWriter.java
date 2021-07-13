package de.uniwue.web.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import de.uniwue.web.model.MetaData;
import org.primaresearch.dla.page.Page;
import org.primaresearch.dla.page.io.xml.PageXmlInputOutput;
import org.primaresearch.dla.page.io.xml.StreamTarget;
import org.primaresearch.dla.page.layout.PageLayout;
import org.primaresearch.dla.page.layout.logical.ReadingOrder;
import org.primaresearch.dla.page.layout.physical.Region;
import org.primaresearch.dla.page.layout.physical.shared.RegionType;
import org.primaresearch.dla.page.layout.physical.text.TextContent;
import org.primaresearch.dla.page.layout.physical.text.TextObject;
import org.primaresearch.dla.page.layout.physical.text.impl.TextLine;
import org.primaresearch.dla.page.layout.physical.text.impl.TextRegion;
import org.primaresearch.ident.Id;
import org.primaresearch.ident.IdRegister.InvalidIdException;
import org.primaresearch.io.UnsupportedFormatVersionException;
import org.primaresearch.io.xml.XmlFormatVersion;
import org.primaresearch.maths.geometry.Polygon;
import org.primaresearch.shared.variable.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import de.uniwue.algorithm.geometry.regions.type.PAGERegionType;
import de.uniwue.algorithm.geometry.regions.type.TypeConverter;
import de.uniwue.web.model.PageAnnotations;
import de.uniwue.web.model.Point;

/**
 * PageXMLWriter is a converter for the PageAnnotations used in this tool
 * to PageXML annotations.
 */
public class PageXMLWriter {

	/**
	 * Get a pageXML document out of a page
	 *
	 * @param result
	 * @param pageXMLVersion
	 * @return pageXML document or null if parse error
	 * @throws UnsupportedFormatVersionException
	 * @throws InvalidIdException
	 */
	public static Document getPageXML(PageAnnotations result, String pageXMLVersion, File xmlFile)
			throws UnsupportedFormatVersionException, InvalidIdException {

		XmlFormatVersion version = new XmlFormatVersion(pageXMLVersion);

		boolean xmlExists = xmlFile.exists() && !xmlFile.isDirectory();
		Page page = xmlExists ? editExistingPageXML(result, version, xmlFile) : createNewPageXML(result, version);
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
	 * Creates a completely new PAGE XML from scratch
	 * @param result
	 * @param version
	 * @return
	 */
	private static Page createNewPageXML(PageAnnotations result, XmlFormatVersion version) throws InvalidIdException {
		// Start PAGE xml
		Page page = new Page(PageXmlInputOutput.getSchemaModel(version));
		page.setImageFilename(String.format("%s.png", result.getName()));
		page.setImageFilename(result.getName()+".png");

		try {
			page.getAttributes().get("orientation").setValue(new DoubleValue(result.getOrientation()));
		} catch (Variable.WrongVariableTypeException e) {
			e.printStackTrace();
		}

		fillMetadata(result.getMetadata(), page, true);
		buildPageLayoutFromScratch(result, page);

		return page;
	}

	/**
	 * Edit an existing PAGE XML by merging existing data and newly created or modified data
	 * @param result
	 * @param version
	 * @param xmlFile
	 * @return
	 */
	private static Page editExistingPageXML(PageAnnotations result, XmlFormatVersion version, File xmlFile) throws UnsupportedFormatVersionException, InvalidIdException {
		Page page = de.uniwue.web.io.PageXMLReader.readPAGE(xmlFile);

		// Convert to result schema version if it differs from local schema version
		if(page.getFormatVersion() != version){
			page.setFormatVersion(PageXmlInputOutput.getInstance().getFormatModel(version));
		}

		fillMetadata(result.getMetadata(), page, false);

		if(result.isSegmented()){
			buildPageLayoutFromScratch(result, page);
		}else{
			editPageLayoutFromResults(result, page);
		}

		return page;
	}

	/**
	 * Builds Page Layout from scratch
	 * @param result
	 * @param page
	 */
	private static void buildPageLayoutFromScratch(PageAnnotations result, Page page) throws InvalidIdException {
		// Create page layout
		PageLayout layout = page.getLayout();
		// Clear layout of all existing regions
		clearLayout(layout);

		layout.setSize(result.getWidth(), result.getHeight());

		// Add Regions
		Map<String, Id> idMap = new HashMap<>();
		addNewElementsToLayout(result, layout, idMap);

		createReadingOrder(layout, result, idMap);
	}

	/**
	 * Merges the changes to the PAGE XML Layout from the frontend into the existing Layout
	 * @param result
	 * @param page
	 */
	private static void editPageLayoutFromResults(PageAnnotations result, Page page) throws InvalidIdException {
		Map<String, Id> idMap = new HashMap<>();
		PageLayout layout = page.getLayout();
		// Remove trashed elements from page layout
		removeDeletedElements(result, layout);

		// Edit existing elements
		mergeElementChangesIntoLayout(result, layout, idMap);

		addNewElementsToLayout(result, layout, idMap);

		createReadingOrder(layout, result, idMap);
	}

	/**
	 * Removes elements which were deleted in the frontend from the PAGE XML, in case they still exist there
	 * @param result
	 * @param layout
	 */
	private static void removeDeletedElements(PageAnnotations result, PageLayout layout){
		for(Map.Entry<String, de.uniwue.web.model.Region> garbage : result.getGarbage().entrySet()){
			de.uniwue.web.model.Region garbageItem = garbage.getValue();

			String idString = garbageItem.getId();
			String type = garbageItem.getType();

			if(isRegion(type, true)){
				Id id = (layout.getRegion(idString) != null) ? layout.getRegion(idString).getId() : null;
				if(id == null)
					continue;
				layout.removeRegion(id);
			}else if(type.equals("TextLine")){
				String parentId = garbageItem.getParent();
				Region parentRegion = (layout.getRegion(parentId) != null) ? layout.getRegion(parentId) : null;

				if(parentRegion == null)
					continue;

				if(parentRegion.getType() == (RegionType.TextRegion)){
					TextRegion textRegion = (TextRegion) parentRegion;
					for(int i = 0; i < textRegion.getTextObjectCount(); i++){
						TextObject textObject = textRegion.getTextObject(i);
						if(textObject instanceof TextLine){
							TextLine textLine = (TextLine) textObject;
							if(textLine.getId().toString().equals(idString)){
								textRegion.removeTextObject(i);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Merges changes of elements which already existed while reading the PAGE XML into the PAGE XML
	 * @param result
	 * @param layout
	 * @param idMap
	 */
	private static void mergeElementChangesIntoLayout(PageAnnotations result, PageLayout layout, Map<String, Id> idMap) {
		for(Map.Entry<String, de.uniwue.web.model.Region> entry : result.getSegments().entrySet()){
			de.uniwue.web.model.Region element = entry.getValue();

			String elementId = element.getId();
			String elementType = element.getType();
			Polygon elementCoords = element.getCoords().toPrimaPolygon();

			if(!isRegion(elementType, true))
				continue;

			Id id = (layout.getRegion(elementId) != null) ? layout.getRegion(elementId).getId() : null;

			if(id == null) {
				continue;
			}

			idMap.put(elementId, id);
			Region physicalRegion = layout.getRegion(id);
			String physicalRegionType = physicalRegion.getType().toString();

			if(isTextRegion(elementType) && isTextRegion(physicalRegionType)){
				TextRegion textRegion = (TextRegion) physicalRegion;
				textRegion.setCoords(elementCoords);

				if(isTextRegionSubtype(elementType)){
					String subType = TypeConverter.stringToStringSubType(elementType);
					textRegion.setTextType(subType);
				}

				Map<String, TextLine> physicalTextLines = getTextLines(textRegion);

				for(Map.Entry<String, de.uniwue.web.model.TextLine> _textLine : element.getTextlines().entrySet()){
					de.uniwue.web.model.TextLine textLine = _textLine.getValue();
					Polygon textLineCoords = textLine.getCoords().toPrimaPolygon();

					if(physicalTextLines.containsKey(textLine.getId())){
						TextLine physicalTextLine = physicalTextLines.get(textLine.getId());
						physicalTextLine.setCoords(textLineCoords);
						for(Entry<Integer,String> content : textLine.getText().entrySet()) {
							final int index = content.getKey();
							if(index == 0 || index == 1){
								physicalTextLine.getTextContentVariant(index).setText(textLine.getText().get(index));
							}
						}
					}else{
						createTextLine(textLine, textRegion);
					}
				}
			}else if(!physicalRegion.getType().toString().equals(elementType)){
				// Deletes region as it well created with the new type in the following step
				// Todo: This should be changed to e.g. allow passing existing attributes (in case they're still allowed
				//  in the new RegionType) and to keep child elements when nested regions get implemented in LAREX
				Id _oldId = physicalRegion.getId();
				layout.removeRegion(_oldId);
			}else {
				physicalRegion.setCoords(elementCoords);
			}
		}
	}

	/**
	 * Creates a new TextLine in a TextRegion
	 * @param textline
	 * @param textRegion
	 */
	private static void createTextLine(de.uniwue.web.model.TextLine textline, TextRegion textRegion){
		final TextLine pageTextLine = textRegion.createTextLine();

		final Polygon coords = new Polygon();
		for (Point point : textline.getCoords().getPoints()) {
			coords.addPoint((int) point.getX(), (int) point.getY());
		}
		pageTextLine.setCoords(coords);

		// Add Text
		for(Entry<Integer,String> content : textline.getText().entrySet()) {
			final int index = content.getKey();
			final TextContent textContent = (index >= pageTextLine.getTextContentVariantCount()) ?
					pageTextLine.addTextContentVariant():  pageTextLine.getTextContentVariant(index);
			textContent.setText(content.getValue());
			textContent.getAttributes().add(new IntegerVariable("index",new IntegerValue(index)));
		}
	}

	/**
	 * Retrieves all textlines of a given TextRegion
	 * @param textRegion
	 * @return
	 */
	private static Map<String, TextLine> getTextLines(TextRegion textRegion){
		Map<String, TextLine> physicalTextLines = new HashMap<>();
		for(int i = 0; i < textRegion.getTextObjectCount(); i++){
			TextObject textObject = textRegion.getTextObject(i);
			if(textObject instanceof TextLine) {
				TextLine _textLine = (TextLine) textObject;
				String _id = _textLine.getId().toString();

				physicalTextLines.put(_id, _textLine);
			}
		}
		return physicalTextLines;
	}

	/**
	 * Adds a single element to the PAGE layout
	 * @param regionSegment
	 * @param layout
	 * @param id
	 * @param idMap
	 * @throws InvalidIdException
	 */
	private static void addNewElementToLayout(de.uniwue.web.model.Region regionSegment,
											  PageLayout layout,
											  Id id,
											  Map<String, Id> idMap) throws InvalidIdException {
		final PAGERegionType type = TypeConverter.stringToPAGEType(regionSegment.getType());
		RegionType regionType = TypeConverter.enumRegionTypeToPrima(type.getType());
		Region region = layout.createRegion(regionType);

		Polygon poly = new Polygon();

		for (Point point : regionSegment.getCoords().getPoints()) {
			poly.addPoint((int) point.getX(), (int) point.getY());
		}

		// Check for TextRegion
		assert regionType != null;
		if (regionType.getName().equals(RegionType.TextRegion.getName())) {
			final TextRegion textRegion = ((TextRegion) region);
			if(type.getSubtype() != null) {
				textRegion.setTextType(TypeConverter.subTypeToString(type.getSubtype()));
			}

			// Add TextLines if existing
			if(regionSegment.getTextlines() != null) {
				final List<de.uniwue.web.model.TextLine> textlines = new ArrayList<>(regionSegment.getTextlines().values());

				// Sort textlines via reading order if a reading order exists
				if(regionSegment.getReadingOrder() != null) {
					List<String> readingOrder = new ArrayList<>(regionSegment.getReadingOrder());
					Collections.reverse(readingOrder);
					readingOrder.forEach(_id -> {
						de.uniwue.web.model.TextLine textline = regionSegment.getTextlines().get(_id);
						textlines.remove(textline);
						textlines.add(0, textline);
					});
				}

				// Iterate over sorted text lines and add to PAGE XML
				for (de.uniwue.web.model.TextLine textline : textlines) {
					createTextLine(textline, textRegion);
				}
			}
		}
		if(id != null){
			region.setId(id);
		}
		region.setCoords(poly);
		if(regionSegment.getOrientation() != null) {
			PrimaLibHelper.setOrientation(region, regionSegment.getOrientation());
		}
		idMap.put(regionSegment.getId(), region.getId());
	}

	/**
	 * Adds all elements from the result – which aren't already present – to the layout
	 * @param result
	 * @param layout
	 * @param idMap
	 * @throws InvalidIdException
	 */
	private static void addNewElementsToLayout(PageAnnotations result, PageLayout layout, Map<String, Id> idMap) throws InvalidIdException {
		for (Entry<String, de.uniwue.web.model.Region> regionEntry : result.getSegments().entrySet()) {
			if(layout.getRegion(regionEntry.getValue().getId()) == null){
				addNewElementToLayout(regionEntry.getValue(), layout,null, idMap);
			}
		}
	}

	/**
	 * Fills MetaData elements with corresponding values
	 * @param resultMetadata Result metadata from the LAREX frontend
	 * @param page
	 * @param isNew Whether the file is newly created or already existed
	 */
	private static void fillMetadata(MetaData resultMetadata, Page page, boolean isNew){
		org.primaresearch.dla.page.metadata.MetaData metadata = page.getMetaData();

		if (isNew || resultMetadata.getCreationTime() == null) {
			metadata.setCreationTime(new Date());
		} else {
			metadata.setCreationTime(resultMetadata.getCreationTime());
		}

		metadata.setLastModifiedTime(new Date());
		metadata.setCreator(resultMetadata.getCreator());
		metadata.setComments(resultMetadata.getComments());
		metadata.setExternalRef(resultMetadata.getExternalRef());
	}

	/**
	 * Removes all regions from an existing PageLayout
	 * @param layout
	 */
	private static void clearLayout(PageLayout layout){
		for(Region region : layout.getRegionsSorted()){
			layout.removeRegion(region.getId());
		}
	}

	/**
	 * Creates the physical reading order from the result
	 * @param layout
	 * @param result
	 * @param idMap
	 */
	private static void createReadingOrder(PageLayout layout, PageAnnotations result, Map<String, Id> idMap){
		ReadingOrder xmlReadingOrder = layout.createReadingOrder();
		xmlReadingOrder.getRoot().setOrdered(true);
		List<String> readingOrder = result.getReadingOrder();
		for (String regionID : readingOrder) {
			if (idMap.get(regionID) != null){
				xmlReadingOrder.getRoot().addRegionRef(idMap.get(regionID).toString());
			}
		}
	}


	/**
	 * Helper to determine whether a region type string is a supported PAGE XML region type
	 * @param type
	 * @return
	 */
	private static boolean isRegion(String type, boolean includeSubtypes) {
		// TODO: All type determining functions should be refactored in a way that all this can be directly determined from our class
		for (de.uniwue.algorithm.geometry.regions.type.RegionType c : de.uniwue.algorithm.geometry.regions.type.RegionType.values()) {
			if (c.name().equals(type)) {
				return true;
			}
		}
		if(includeSubtypes){
			for (de.uniwue.algorithm.geometry.regions.type.RegionSubType c : de.uniwue.algorithm.geometry.regions.type.RegionSubType.values()) {
				if (c.name().equals(type)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Returns whether a type is a TextRegion (including subtypes)
	 * @param type
	 * @return
	 */
	private static boolean isTextRegion(String type) {
		if(type.equals(RegionType.TextRegion.getName()))
			return true;
		for (de.uniwue.algorithm.geometry.regions.type.RegionSubType c : de.uniwue.algorithm.geometry.regions.type.RegionSubType.values()) {
			if (c.name().equals(type)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns whether a type is a subtype of a TextRegion
	 * @param type String representation of type
	 * @return boolean
	 */
	private static boolean isTextRegionSubtype(String type){
		for (de.uniwue.algorithm.geometry.regions.type.RegionSubType c : de.uniwue.algorithm.geometry.regions.type.RegionSubType.values()) {
			if (c.name().equals(type)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Writes the Orientation to a PageXML Document
	 *
	 * @param doc PageXML Document
	 * @param orientation orientation angle in degree
	 * @return modified Document
	 */
	private static Document setPageOrientation(Document doc, double orientation) {
		Element rootElement = doc.getDocumentElement();
		NodeList pageElements = rootElement.getElementsByTagName("Page");
		for(int i = 0; i < pageElements.getLength(); i++) {
			Element pageElement = (Element) pageElements.item(i);
			pageElement.setAttribute("orientation", Double.toString(orientation));
		}
		return doc;
	}

	/**
	 * Save a document in a outputFolder
	 *
	 * @param document
	 * @param filePath
	 */
	public static void saveDocument(Document document, File filePath, boolean prettyPrint) {
		// TODO: Add file locks (see: #251)
		try {
			// write content into xml file
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			if(prettyPrint){
				transformer.setOutputProperty(OutputKeys.INDENT, "yes");
				transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
			}
			DOMSource source = new DOMSource(document);

			FileOutputStream output = new FileOutputStream(filePath);
			StreamResult result = new StreamResult(output);
			transformer.transform(source, result);

			output.close();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
