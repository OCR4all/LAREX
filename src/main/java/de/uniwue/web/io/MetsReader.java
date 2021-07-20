package de.uniwue.web.io;

import java.io.File;
import java.io.IOException;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import de.uniwue.web.config.Constants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * MetsReader reads mets filegroup data and file locations from a mets.xml
 */
public class MetsReader {

    public static Map<String, List<List<String>>> getFileGroups(String metsPath, boolean useRelativePath){
        List<List<String>> pageList;
        Map<String, List<List<String>>> fileGroupMap = new LinkedHashMap<>();
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
                    pageList = new ArrayList<>();
                    Element fileGrp = (Element) fileSector.item(i);
                    String fileGrpName = fileGrp.getAttribute("USE");
                    NodeList fileGrpChilds = fileGrp.getChildNodes();
                    for(int j = 0; j < fileGrpChilds.getLength(); j++) {
                        Node fileNode = fileGrpChilds.item(j);
                        List<String> fileList = new ArrayList<>();
                        if(fileNode.getNodeType() == Node.ELEMENT_NODE){
                            Element fileElement = (Element) fileNode;
                            if(fileElement.getAttribute("MIMETYPE").startsWith("image")) {
                                for(String fileExt : Constants.IMG_EXTENSIONS_DOTTED) {
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
                            pageList.add(fileList);
                        }
                    }
                    fileGroupMap.put(fileGrpName,pageList);
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

    public static List<String> getImagePathFromPage(String pageXmlPath) {
        try {
            Document pageXml = parseXML(pageXmlPath);
            Element rootElement = pageXml.getDocumentElement();
            NodeList nodeList = rootElement.getChildNodes();
            NodeList pageNode = null;
            Element pageElement = null;
            for(int i = 0; i < nodeList.getLength() ;i++) {
                if (nodeList.item(i).getNodeName().contains("Page") || nodeList.item(i).getNodeName().equals("Page")) {
                    pageNode = nodeList.item(i).getChildNodes();
                    pageElement = (Element) nodeList.item(i);
                }
            }
            if(pageNode == null) { throw new NoSuchElementException("No page element found."); }
            List<String> imagePathList = new ArrayList<>();
            imagePathList.add(pageElement.getAttribute("imageFilename"));
            for(int i = 0; i < pageNode.getLength() ;i++) {
                if (pageNode.item(i).getNodeName().contains("AlternativeImage") && pageNode.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    Element elem = (Element) pageNode.item(i);
                    imagePathList.add(elem.getAttribute("filename"));
                }
            }
            return imagePathList;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
