package larex.export;

import java.util.ArrayList;

import org.opencv.core.Mat;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import larex.positions.Position;
import larex.regions.Region;
import larex.regions.RegionManager;
import larex.segmentation.parameters.Parameters;

public class SettingsReader {
	private static ArrayList<Position> extractPositions(NodeList positionElements, Mat resized) {
		ArrayList<Position> positions = new ArrayList<Position>();

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

			Position position = new Position(x1, y1, x2, y2);
			position.setFixed(isFixed);
			position.updateRect(position.calcRect(resized), resized);
			positions.add(position);
		}

		return positions;
	}

	private static RegionManager extractRegions(NodeList regionNodes, Mat resized) {
		RegionManager regionManager = new RegionManager();
		regionManager.setRegions(new ArrayList<Region>());

		for (int i = 0; i < regionNodes.getLength(); i++) {
			Element regionElement = (Element) regionNodes.item(i);
			String type = regionElement.getAttribute("type");
			int minSize = Integer.parseInt(regionElement.getAttribute("minSize"));
			int maxOccurances = Integer.parseInt(regionElement.getAttribute("maxOccurances"));
			String priority = regionElement.getAttribute("priority");

			NodeList positionElements = regionElement.getElementsByTagName("position");
			ArrayList<Position> positions = extractPositions(positionElements, resized);

			Region region = new Region(type, minSize, maxOccurances, priority, new ArrayList<Position>());
			region.setPositions(positions);
			regionManager.addRegion(region);
		}

		return regionManager;
	}

	private static Parameters extractParameters(Element parameterElement, RegionManager regionmanager) {
		Parameters parameters = new Parameters(regionmanager,
				Integer.parseInt(parameterElement.getAttribute("verticalResolution")));
		parameters.setBinaryThresh(Integer.parseInt(parameterElement.getAttribute("binaryThresh")));
		parameters.setImageRemovalDilationX(Integer.parseInt(parameterElement.getAttribute("imageDilationX")));
		parameters.setImageRemovalDilationY(Integer.parseInt(parameterElement.getAttribute("imageDilationY")));
		parameters.setTextDilationX(Integer.parseInt(parameterElement.getAttribute("textDilationX")));
		parameters.setTextDilationY(Integer.parseInt(parameterElement.getAttribute("textDilationY")));

		return parameters;
	}

	public static Parameters loadSettings(Document document, Mat resized) {
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
}