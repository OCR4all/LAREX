package de.uniwue.web.controller;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.web.communication.DirectRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestBody;
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
	 * Open the viewer and display the contents of a book
	 **/
	@RequestMapping(value = "/viewer", method = RequestMethod.GET)
	public String viewer(Model model, @RequestParam(value = "book", required = true) Integer bookID) {
		if (bookID == null) {
			return "redirect:/404";
		}

		FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
				config.getListSetting("imagefilter"), true);
		Book book = database.getBook(bookID);

		if (book == null) {
			return "redirect:/404";
		}

		model.addAttribute("book", book);
		model.addAttribute("regionTypes", getRegionTypes());
		model.addAttribute("bookPath", "loadImage/");
		model.addAttribute("globalSettings", config);


		return "editor";
	}

	/**
	 * Open the viewer in direct nonflat mode and display the contents of its "book"
	 **/
	@RequestMapping(value = "/directviewer", method = RequestMethod.POST)
	public String directViewer(Model model, @RequestParam(value = "book", required = true) Integer bookID
								, @RequestParam(value = "bookname", required = false) String bookName
							    , @RequestParam(value = "imagemap", required = true) Map<String, String> imageMap) {
		if (bookID == null || imageMap.isEmpty()) {
			return "redirect:/404";
		}
		FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
				config.getListSetting("imagefilter"), false);
		Book book = database.getBook(bookName, bookID, imageMap);
		if (book == null) {
			return "redirect:/404";
		}

		model.addAttribute("book", book);
		model.addAttribute("regionTypes", getRegionTypes());
		model.addAttribute("bookPath", "loadImage/");
		model.addAttribute("globalSettings", config);


		return "editor";
	}
	
	/**
	 * Open the viewer with a direct request if direct request is enabled using hierarchical directory structures
	 * and display the contents of a selected book.
	 */
	@RequestMapping(value = "/direct", method = RequestMethod.POST)
	public String direct(Model model, @RequestParam(value = "bookpath", required = true) String bookpath,
			@RequestParam(value = "bookname", required = true) String bookname,
			@RequestParam(value = "localsave", required = false) String localsave,
			@RequestParam(value = "savedir", required = false) String savedir,
			@RequestParam(value = "websave", required = false) String websave,
			@RequestParam(value = "imagefilter", required = false) String imagefilter,
			@RequestParam(value = "modes", required = false) String modes) throws IOException {
		if (!config.getSetting("directrequest").equals("enable")) {
			return "redirect:/error/403";
		}
		if (!new File(bookpath + File.separator + bookname).exists()) {
			return "redirect:/error/400";
		}
		fileManager.setLocalBooksPath(bookpath);
		int bookID = bookname.hashCode();

		if (localsave != null) {
			config.setSetting("localsave", localsave);
		}
		if (savedir != null) {
			config.setSetting("savedir", savedir);
		}
		if (websave != null) {
			config.setSetting("websave", websave);
		}
		if (imagefilter != null) {
			config.setSetting("imagefilter", imagefilter);
		}
		if (modes != null){
			config.setSetting("modes", modes);
		}
		return viewer(model, bookID);
	}

	/**
	 * Open the viewer with a direct request if direct request is enabled using non-flat directory structures
	 * and display the contents of a selected book.
	 */
	@RequestMapping(value = "/direct2", method = RequestMethod.POST)
	public String direct(Model model,
						  @RequestParam(value = "imageMap", required = true) String imagemapString,
						  @RequestParam(value = "xmlMap", required = true) String xmlmapString,
						  @RequestParam(value = "bookname", required = true) String bookname,
						  @RequestParam(value = "localsave", required = false) String localsave,
						  @RequestParam(value = "savedir", required = false) String savedir,
						  @RequestParam(value = "websave", required = false) String websave,
						  @RequestParam(value = "imagefilter", required = false) String imagefilter,
						  @RequestParam(value = "modes", required = false) String modes) throws IOException {
		if (!config.getSetting("directrequest").equals("enable")) {
			return "redirect:/error/403";
		}
		ObjectMapper mapper = new ObjectMapper();
		Map<String, String> imagemap;
		Map<String, String> xmlmap;

		try {
			imagemap = mapper.readValue(imagemapString, Hashtable.class);
			xmlmap = mapper.readValue(xmlmapString, Hashtable.class);
		} catch (IOException e) {
			e.printStackTrace();
			return "redirect:/error/500";
		}
		File tmpBookpath = Files.createTempDirectory("tempdir").toFile();
		fileManager.setIsFlat(false);
		fileManager.setLocalBooksPath(tmpBookpath.getPath());
		fileManager.setLocalBookMap(xmlmap, imagemap);
		fileManager.setNonFlatBookName(bookname);
		int bookID = tmpBookpath.getName().hashCode();

		if (localsave != null) {
			config.setSetting("localsave", localsave);
		}
		if (savedir != null) {
			config.setSetting("savedir", savedir);
		}
		if (websave != null) {
			config.setSetting("websave", websave);
		}
		if (imagefilter != null) {
			config.setSetting("imagefilter", imagefilter);
		}
		if (modes != null){
			config.setSetting("modes", modes);
		}
		return directViewer(model,bookID,bookname,imagemap);
	}

	/**
	 * Open the viewer from library navigation.
	 */
	@RequestMapping(value = "/directLibrary", method = RequestMethod.GET)
	public String direct(Model model,
						 @RequestParam(value = "imageMap", required = true) String imagemapString,
						 @RequestParam(value = "customFlag", required = true) String customFlag,
						 @RequestParam(value = "customFolder", required = false) String customFolder) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		Map<String, String> imagemap;
		Map<String, String> xmlmap = new LinkedHashMap<>();
		DirectRequest directRequest = new DirectRequest(imagemapString, customFlag, customFolder);

		fileManager.setDirectRequest(directRequest);

		try {
			imagemap = mapper.readValue(java.net.URLDecoder.decode(imagemapString, StandardCharsets.UTF_8.name()).replaceAll("‡","\"").replaceAll("…",":"), HashMap.class);
		} catch (IOException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
			return "redirect:/error/500";
		}
		for(Map.Entry<String, String> entry : imagemap.entrySet()) {
			String imageName = entry.getKey();
			String imagePath = entry.getValue();
			String xmlName = imageName.split("\\.")[0] + ".xml";
			if(customFlag.equals("true")) {
				if(!customFolder.endsWith(File.separator)) { customFolder += File.separator; }
				xmlmap.put(xmlName,customFolder + xmlName);
			} else {
				String parentFolder = new File(imagePath).getParentFile().getAbsolutePath();
				if(!parentFolder.endsWith(File.separator)) { parentFolder += File.separator; }
				xmlmap.put(xmlName,parentFolder + xmlName);
			}
		}

		File tmpBookpath = Files.createTempDirectory("tempdir").toFile();
		fileManager.setIsFlat(false);
		fileManager.setLocalBooksPath(tmpBookpath.getPath());
		fileManager.setLocalBookMap(xmlmap, imagemap);
		fileManager.setNonFlatBookName("bookname");
		int bookID = tmpBookpath.getName().hashCode();

		FileDatabase database = new FileDatabase(new File(fileManager.getLocalBooksPath()),
				config.getListSetting("imagefilter"), false);
		Book book = database.getBook("libraryBook", bookID, imagemap);

		model.addAttribute("book", book);
		model.addAttribute("regionTypes", getRegionTypes());
		model.addAttribute("bookPath", "loadImage/");
		model.addAttribute("globalSettings", config);

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
}
