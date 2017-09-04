package larex.export;

import java.io.File;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import larex.positions.Position;
import larex.regions.Region;
import larex.segmentation.parameters.Parameters;

public class SettingsWriter {

	public static Document getSettingsXML(Parameters parameters) {
		try {
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			Document document = docBuilder.newDocument();

			Element rootElement = document.createElement("settings");
			document.appendChild(rootElement);

			Element parametersElement = document.createElement("parameters");

			parametersElement.setAttribute("verticalResolution", "" + parameters.getDesiredImageHeight());
			parametersElement.setAttribute("binaryThresh", "" + parameters.getBinaryThresh());
			parametersElement.setAttribute("imageDilationX", "" + parameters.getImageRemovalDilationX());
			parametersElement.setAttribute("imageDilationY", "" + parameters.getImageRemovalDilationY());
			parametersElement.setAttribute("textDilationX", "" + parameters.getTextDilationX());
			parametersElement.setAttribute("textDilationY", "" + parameters.getTextDilationY());

			Element regionsElement = document.createElement("regions");

			for (Region region : parameters.getRegionManager().getRegions()) {
				Element regionElement = document.createElement("region");

				regionElement.setAttribute("type", region.getType().toString());
				regionElement.setAttribute("minSize", "" + region.getMinSize());
				regionElement.setAttribute("maxOccurances", "" + region.getMaxOccurances());
				
				if(region.getMaxOccurances() == 1) {
					regionElement.setAttribute("priority", region.getPriorityPosition().toString());
				}

				for (Position position : region.getPositions()) {
					Element positionElement = document.createElement("position");

					positionElement.setAttribute("x1", "" + position.getTopLeftXPercentage());
					positionElement.setAttribute("y1", "" + position.getTopLeftYPercentage());
					positionElement.setAttribute("x2", "" + position.getBottomRightXPercentage());
					positionElement.setAttribute("y2", "" + position.getBottomRightYPercentage());
					positionElement.setAttribute("fixed", "" + position.isFixed());

					regionElement.appendChild(positionElement);
				}

				regionsElement.appendChild(regionElement);
			}
			
			rootElement.appendChild(parametersElement);
			rootElement.appendChild(regionsElement);

			return document;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	

	public static void saveSettings(String outputPath, Parameters parameters) {
		Document document = getSettingsXML(parameters);
		try {
			// write content into xml file
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer;
			
				transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(document);

			if(!outputPath.endsWith(File.separator)) {
				outputPath += File.separator;
			}
			
			outputPath += "Settings.xml";
			
			StreamResult result = new StreamResult(new File(outputPath));
			transformer.transform(source, result);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
}