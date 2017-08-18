package larex.export;

import java.io.File;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.opencv.core.Mat;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import larex.positions.Position;
import larex.regions.Region;
import larex.regions.RegionManager;

public class SettingsReader {
	private static ArrayList<Position> extractPositions(NodeList positionElements, Mat resized) {
		ArrayList<Position> positions = new ArrayList<Position>();

		for (int i = 0; i < positionElements.getLength(); i++) {
			Element positionElement = (Element) positionElements.item(i);

			String fixed = positionElement.getAttribute("fixed");
			String permanent = positionElement.getAttribute("permanent");

			boolean isFixed = false;
			boolean isPermanent = false;

			if (fixed.equals("true")) {
				isFixed = true;
			}

			if (permanent.equals("true")) {
				isPermanent = true;
			}

			double x1 = Double.parseDouble(positionElement.getAttribute("x1"));
			double x2 = Double.parseDouble(positionElement.getAttribute("x2"));
			double y1 = Double.parseDouble(positionElement.getAttribute("y1"));
			double y2 = Double.parseDouble(positionElement.getAttribute("y2"));

			Position position = new Position(x1, y1, x2, y2);
			position.setFixed(isFixed);
			position.setPermanent(isPermanent);
			position.updateRect(position.calcRect(resized), resized);
			positions.add(position);
		}

		return positions;
	}

	private static void extractRegions(NodeList regionNodes, RegionManager regionManager, Mat resized) {
		regionManager.setRegions(new ArrayList<Region>());

		for (int i = 0; i < regionNodes.getLength(); i++) {
			Element regionElement = (Element) regionNodes.item(i);
			String type = regionElement.getAttribute("type");
			int minSize = Integer.parseInt(regionElement.getAttribute("minSize"));
			String color = regionElement.getAttribute("color");
			int maxOccurances = Integer.parseInt(regionElement.getAttribute("maxOccurances"));
			String priority = regionElement.getAttribute("priority");

			NodeList positionElements = regionElement.getElementsByTagName("position");
			ArrayList<Position> positions = extractPositions(positionElements, resized);

			Region region = new Region(type, minSize, color, maxOccurances, priority, new ArrayList<Position>());
			region.setPositions(positions);
			regionManager.addRegion(region);
		}

		PnlPostProcessing.updateItems(regionManager);
	}

	private static void extractParameters(Element parameterElement, PnlProcessParameters pnlParameters) {
		pnlParameters.getSpinBinaryThresh().setValue(Integer.parseInt(parameterElement.getAttribute("binaryThresh")));
		pnlParameters.getSpinImageDilationX()
				.setValue(Integer.parseInt(parameterElement.getAttribute("imageDilationX")));
		pnlParameters.getSpinImageDilationY()
				.setValue(Integer.parseInt(parameterElement.getAttribute("imageDilationY")));
		pnlParameters.getSpinTextDilationX().setValue(Integer.parseInt(parameterElement.getAttribute("textDilationX")));
		pnlParameters.getSpinTextDilationY().setValue(Integer.parseInt(parameterElement.getAttribute("textDilationY")));
		pnlParameters.getSpinImageSize()
				.setValue(Integer.parseInt(parameterElement.getAttribute("verticalResolution")));
	}

	public static void loadSettings(String inputFolder) {
		if (!inputFolder.endsWith(File.separator)) {
			inputFolder += File.separator;
		}

		String inputPath = inputFolder + "Settings.xml";
		File inputFile = new File(inputPath);

		if (inputFile.exists()) {
			try {
				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
				Document document = dBuilder.parse(inputFile);
				document.getDocumentElement().normalize();

				Element parameterElement = (Element) document.getElementsByTagName("parameters").item(0);

				PnlProcessParameters pnlParameters = guiManager.getGui().getPnlProcessParameters();
				extractParameters(parameterElement, pnlParameters);

				guiManager.getGui().getPnlRegionParameters().deleteAllRegions(guiManager.getGui());
				NodeList regionNodes = document.getElementsByTagName("region");
				extractRegions(regionNodes, guiManager.getRegionManager(), guiManager.getCurrentPage().getResized());
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("Reading XML file failed!");
			}
		}
	}
}