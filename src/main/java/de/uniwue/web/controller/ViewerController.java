package de.uniwue.web.controller;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.web.communication.DirectRequest;
import de.uniwue.web.io.MetsReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import de.uniwue.algorithm.geometry.regions.type.PAGERegionType;
import de.uniwue.web.config.LarexConfiguration;
import de.uniwue.web.io.FileDatabase;
import de.uniwue.web.io.FilePathManager;
import de.uniwue.web.model.Book;

/**
 * Communication Controller to handle requests for the main viewer/editor.
 * Handles requests about displaying book scans and segmentations.
 *
 */
@Controller
@Scope("request")
public class ViewerController {
	@Autowired
	private ServletContext servletContext;
	@Autowired
	private FilePathManager fileManager;
	@Autowired
	private LarexConfiguration config;

	/**
	 * Initialize the controller by loading the fileManager and settings if not
	 * loaded already.
	 **/
	@PostConstruct
	private void init() {
		if (!fileManager.isInit()) {
			fileManager.init(servletContext);
		}
		if (!config.isInitiated()) {
			config.read(new File(fileManager.getConfigurationFile()));
			String bookFolder = config.getSetting("bookpath");
			String saveDir = config.getSetting("savedir");
			if (!bookFolder.equals("")) {
				fileManager.setLocalBooksPath(bookFolder);
			}
			if (saveDir != null && !saveDir.equals("")) {
				fileManager.setSaveDir(saveDir);
			}
		}
	}

	/**
	 * Open the viewer from library navigation.
	 */
	@SuppressWarnings("unchecked")
	@RequestMapping(value = "/directLibrary", method = RequestMethod.POST)
	public String direct(Model model,
						 @RequestParam(value = "fileMap", required = true) String fileMapString,
						 @RequestParam(value = "mimeMap", required = true) String mimeMapString,
						 @RequestParam(value = "metsFilePath", required = true) String metsFilePath,
						 @RequestParam(value = "customFlag", required = true) String customFlag,
						 @RequestParam(value = "customFolder", required = false) String customFolder) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure( JsonParser.Feature.ALLOW_COMMENTS, true );
		String metsRoot = new File(metsFilePath.replaceAll("\"" , "")).getAbsolutePath();
		Map<String, List<String>> fileMap = new TreeMap<>();
		Map<String, String> mimeMap = new TreeMap<>();
		Map<String, List<String>> imageMap = new LinkedHashMap<>();
		Map<String, String> xmlMap = new LinkedHashMap<>();
		DirectRequest directRequest = new DirectRequest(fileMapString, mimeMapString, metsFilePath, customFlag, customFolder);
		fileManager.setDirectRequest(directRequest);

		String timeStamp = String.valueOf(System.currentTimeMillis() / 1000L);

		try {
			/*
				List of paths is mapped as an object, then cast to String and
				finally split with path separators to create desired List
			 */
			Map<String, Object> map = mapper.readValue(java.net.URLDecoder.decode(fileMapString, StandardCharsets.UTF_8.name()), TreeMap.class);
			for(Map.Entry<String, Object> entry : map.entrySet()) {
				List<String> pathList = (List<String>) entry.getValue();
				fileMap.put(entry.getKey(), pathList);
			}
			mimeMap = mapper.readValue(java.net.URLDecoder.decode(mimeMapString, StandardCharsets.UTF_8.name()), TreeMap.class);
		} catch (IOException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
			return "redirect:/error/500";
		}
		if(customFlag.equals("true") && !customFolder.endsWith(File.separator)) { customFolder += File.separator; }
		for(Map.Entry<String, List<String>> entry : fileMap.entrySet()) {
			String fileName = java.net.URLDecoder.decode(entry.getKey().replaceAll("\"" , ""));
			System.out.println(fileName);
			String fileKey = fileName.split("\\.")[0];
			List<String> filePathList = entry.getValue();
			String xmlPath;
			/*
				When images are loaded from each pagexml, instead of directly from mets( or legacy),
				the value of each imageMap.entry is a xmlPath instead of an imagePath. This value has to be changed to
				the imagePath read from the given xmlPath.
			 */
			for(String filePath : filePathList) {
				if(mimeMap.get(filePath).equals("application/vnd.prima.page+xml")) {
					xmlPath = filePath;
					List<String> pageImagePathList = MetsReader.getImagePathFromPage(xmlPath);
				/*
					Correct absolute paths for images as they are not constrained to pageXML.parent
					and described as relative to metsXml.root
				 */
					List<String> absolutePathList = new ArrayList<>();
					assert pageImagePathList != null;
					for(String imagePath : pageImagePathList) {
						if(!metsRoot.endsWith(File.separator)) { metsRoot += File.separator; }
						absolutePathList.add(metsRoot + imagePath.replaceAll("\"" , ""));
					}
					imageMap.put(fileKey,absolutePathList);
					xmlMap.put(fileKey, xmlPath);
				} else if(mimeMap.get(filePath).startsWith("image")) {
					/*
					 * PAGEs for images without PAGE in its fileGrp will be stored
					 * in same directory as the corresponding image.
					 */
					xmlPath = fileKey + ".xml";
					List<String> imgList;
					if(imageMap.containsKey(fileKey)) {
						imgList = imageMap.get(fileKey);
					} else {
						imgList = new ArrayList<>();
					}
					imgList.add(filePath);
					imageMap.put(fileKey, imgList);
					if(customFlag.equals("true")) {
						xmlMap.put(fileKey,customFolder + xmlPath);
					} else {
						String parentFolder = new File(filePathList.get(0)).getParent();
						if(!parentFolder.endsWith(File.separator)) { parentFolder += File.separator; }
						xmlMap.put(fileKey,parentFolder + xmlPath);
					}

				}
			}
		}

		File tmpBookpath = Files.createTempDirectory("tempdir").toFile();
		fileManager.setIsFlat(false);
		fileManager.setLocalBooksPath(tmpBookpath.getPath());
		fileManager.setLocalBookMap(xmlMap, imageMap);
		fileManager.setNonFlatBookName("bookname");
		int bookID = tmpBookpath.getName().hashCode();

		FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
				config.getListSetting("imagefilter"), false);
		Book book = database.getBook("libraryBook", bookID, imageMap, xmlMap);

		model.addAttribute("book", book);
		model.addAttribute("regionTypes", getRegionTypes());
		model.addAttribute("bookPath", "loadImage/");
		model.addAttribute("globalSettings", config);
		model.addAttribute("timeStamp", timeStamp);

		return "editor";
	}

	private static SortedMap<String, Integer> getRegionTypes() {
		SortedMap<String, Integer> regionTypes = new TreeMap<String, Integer>((c1, c2) -> {
			if (c1.contains("Region") && !c2.contains("Region"))
				return 1;
			else
				return c1.compareTo(c2);
		});

		int i = 0;
		for (PAGERegionType type : PAGERegionType.values()) {
			regionTypes.put(type.toString(), i);
			i++;
		}
		return regionTypes;
	}

	/**
	 * Determine if project was loaded from images or pagexml
	 * @param value
	 * @return true if pagexml type
	 */
	private static Boolean determineType(String value) {
		return value.endsWith(".xml");
	}
}
