package de.uniwue.web.io;

import java.io.File;
import java.io.IOException;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXParseException;

/**
 * MetsReader reads mets filegroup data and file locations from a mets.xml
 */
public class MetsReader {

    public static Map<String, List<String>> getFileGroups(String metsPath, boolean useRelativePath){
        List<String> fileList;
        Map<String, List<String>> fileGroupMap = new LinkedHashMap<>();
        File metsFile = new File(metsPath);
        try {
            Document mets = parseXML(metsPath);
            Element rootElement = mets.getDocumentElement();
            NodeList sectorList = rootElement.getChildNodes();
            NodeList fileSector = null;
            for(int i = 0; i < sectorList.getLength() ;i++) {
                if (sectorList.item(i).getNodeName().equals("mets:fileSec")) {
                    fileSector = sectorList.item(i).getChildNodes();
                }
            }
            if(fileSector == null) { throw new NoSuchElementException("No file sector found."); }
            for(int i = 0; i < fileSector.getLength(); i++) {
                Boolean isImgGrp = false;
                if(fileSector.item(i).getNodeType() == Node.ELEMENT_NODE){
                    fileList = new ArrayList<>();
                    Element fileGrp = (Element) fileSector.item(i);
                    String fileGrpName = fileGrp.getAttribute("USE");
                    NodeList fileGrpChilds = fileGrp.getChildNodes();
                    for(int j = 0; j < fileGrpChilds.getLength(); j++) {
                        Node fileNode = fileGrpChilds.item(j);
                        if(fileNode.getNodeType() == Node.ELEMENT_NODE){
                            Element fileElement = (Element) fileNode;
                            if(fileElement.getAttribute("MIMETYPE").startsWith("image")) {
                                List<String> supportedImageExt = Arrays.asList(".png", ".jpg", ".jpeg", ".tif", ".tiff");
                                for(String fileExt : supportedImageExt) {
                                    String ext = fileExt.replace(".","image/");
                                    if(fileElement.getAttribute("MIMETYPE").equals(ext)) {
                                        isImgGrp = true;
                                        for(int h = 0; h < fileElement.getChildNodes().getLength(); h++) {
                                            if(fileElement.getChildNodes().item(h).getNodeType() == Node.ELEMENT_NODE) {
                                                Element fileLoc = (Element) fileElement.getChildNodes().item(h);
                                                String filePath = fileLoc.getAttribute("xlink:href");
                                                if(!useRelativePath) {
                                                    filePath = metsFile.getParentFile().getAbsolutePath() + File.separator + filePath;
                                                }
                                                fileList.add(filePath);
                                            }
                                        }
                                    }
                                }
                            } else if(!isImgGrp && fileElement.getAttribute("MIMETYPE").equals("application/vnd.prima.page+xml")) {
                                for(int h = 0; h < fileElement.getChildNodes().getLength(); h++) {
                                    if(fileElement.getChildNodes().item(h).getNodeType() == Node.ELEMENT_NODE) {
                                        Element fileLoc = (Element) fileElement.getChildNodes().item(h);
                                        String filePath = fileLoc.getAttribute("xlink:href");
                                        if(!useRelativePath) {
                                            fileList.add(metsFile.getParentFile().getAbsolutePath() + File.separator + filePath);
                                        } else {
                                            fileList.add(filePath);
                                        }

                                    }
                                }
                            }
                        }
                    }
                    fileList.sort(Comparator.naturalOrder());
                    fileGroupMap.put(fileGrpName,fileList);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return fileGroupMap;
    }

    private static Document parseXML(String filePath) throws ParserConfigurationException,
            IOException,
            org.xml.sax.SAXException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(filePath);
        doc.getDocumentElement().normalize();
        return doc;
    }

    public static String getImagePathFromPage(String pageXmlPath) {
        try {
            Document pageXml = parseXML(pageXmlPath);
            Element rootElement = pageXml.getDocumentElement();
            NodeList nodeList = rootElement.getChildNodes();
            NodeList pageNode = null;
            for(int i = 0; i < nodeList.getLength() ;i++) {
                if (nodeList.item(i).getNodeName().contains("Page") || nodeList.item(i).getNodeName().equals("Page")) {
                    pageNode = nodeList.item(i).getChildNodes();
                }
            }
            if(pageNode == null) { throw new NoSuchElementException("No page element found."); }
            String imagePath = null;
            //TODO: enable using original and all alternative images
            for(int i = 0; i < pageNode.getLength() ;i++) {
                if (pageNode.item(i).getNodeName().contains("AlternativeImage") && pageNode.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    Element elem = (Element) pageNode.item(i);
                    imagePath = elem.getAttribute("filename");
                }
            }
            return imagePath;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
