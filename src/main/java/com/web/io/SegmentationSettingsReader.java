package com.web.io;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import org.opencv.core.Size;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import larex.geometry.positions.RelativePosition;
import larex.geometry.regions.Region;
import larex.geometry.regions.RegionManager;
import larex.geometry.regions.type.PAGERegionType;
import larex.geometry.regions.type.TypeConverter;
import larex.segmentation.parameters.Parameters;

/**
 * SegmentationSettingsReader is the main way to read segmentation settings xmls into 
 * Parameters for the segmentation steps.
 */
public class SegmentationSettingsReader {
	/**
	 * Read a settingsfile from a document into Parameters
	 * 
	 * @param document
	 * @param resized
	 * @return
	 */
	public static Parameters loadSettings(Document document, Size resized) {
		Parameters parameters = null;
		try {
			document.getDocumentElement().normalize();

			Element parameterElement = (Element) document.getElementsByTagName("parameters").item(0);

			NodeList regionNodes = document.getElementsByTagName("region");
			RegionManager regionmanager = extractRegions(regionNodes, resized);
			parameters = extractParameters(parameterElement, regionmanager);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Reading XML file failed!");
		}
		return parameters;
	}
	/**
	 * Extract the Position data of a position elements
	 * 
	 * @param positionElements
	 * @param resized
	 * @return
	 */
	private static Collection<RelativePosition> extractPositions(NodeList positionElements, Size resized) {
		ArrayList<RelativePosition> positions = new ArrayList<RelativePosition>();

		for (int i = 0; i < positionElements.getLength(); i++) {
			Element positionElement = (Element) positionElements.item(i);

			String fixed = positionElement.getAttribute("fixed");

			boolean isFixed = false;

			if (fixed.equals("true")) {
				isFixed = true;
			}

			double x1 = Double.parseDouble(positionElement.getAttribute("x1"));
			double x2 = Double.parseDouble(positionElement.getAttribute("x2"));
			double y1 = Double.parseDouble(positionElement.getAttribute("y1"));
			double y2 = Double.parseDouble(positionElement.getAttribute("y2"));

			RelativePosition position = new RelativePosition(x1, y1, x2, y2);
			position.setFixed(isFixed);
			position.updateRect(position.calcRect(resized), resized);
			positions.add(position);
		}

		return positions;
	}

	private static RegionManager extractRegions(NodeList regionNodes, Size resized) {
		RegionManager regionManager = new RegionManager(new HashSet<>());

		for (int i = 0; i < regionNodes.getLength(); i++) {
			Element regionElement = (Element) regionNodes.item(i);
			PAGERegionType typePAGE = TypeConverter.stringToPAGEType(regionElement.getAttribute("type"));
			String type = typePAGE.getType() != null ? typePAGE.getType().toString() : "";
			String subtype = typePAGE.getSubtype() != null ? typePAGE.getSubtype().toString() : "";

			int minSize = Integer.parseInt(regionElement.getAttribute("minSize"));
			int maxOccurances = Integer.parseInt(regionElement.getAttribute("maxOccurances"));
			String priority = regionElement.getAttribute("priority");

			NodeList positionElements = regionElement.getElementsByTagName("position");
			Collection<RelativePosition> positions = extractPositions(positionElements, resized);

			Region region = new Region(type, subtype, minSize, maxOccurances, priority, new ArrayList<RelativePosition>());
			region.setPositions(positions);
			regionManager.addArea(region);
		}

		return regionManager;
	}

	/**
	 * Extract parameters from a parameter Element
	 * 
	 * @param parameterElement
	 * @param regionmanager
	 * @return
	 */
	private static Parameters extractParameters(Element parameterElement, RegionManager regionmanager) {
		Parameters parameters = new Parameters(regionmanager,
				Integer.parseInt(parameterElement.getAttribute("verticalResolution")));
		parameters.setImageRemovalDilationX(Integer.parseInt(parameterElement.getAttribute("imageDilationX")));
		parameters.setImageRemovalDilationY(Integer.parseInt(parameterElement.getAttribute("imageDilationY")));
		parameters.setTextDilationX(Integer.parseInt(parameterElement.getAttribute("textDilationX")));
		parameters.setTextDilationY(Integer.parseInt(parameterElement.getAttribute("textDilationY")));

		return parameters;
	}

}