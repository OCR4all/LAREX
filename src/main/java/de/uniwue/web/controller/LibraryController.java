package de.uniwue.web.controller;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;

import de.uniwue.web.communication.DirectRequest;
import de.uniwue.web.config.Constants;
import de.uniwue.web.io.MetsReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import de.uniwue.web.config.LarexConfiguration;
import de.uniwue.web.io.FileDatabase;
import de.uniwue.web.io.FilePathManager;
import de.uniwue.web.model.Library;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Communication Controller to handle simple requests about the book library.
 *
 */
@Controller
@Scope("request")
public class LibraryController {

	public static final String ENV_LAREX_VERSION = "LAREX_VERSION";

	static Logger logger = LoggerFactory.getLogger(LibraryController.class);

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
			if (!bookFolder.equals("")) {
				fileManager.setLocalBooksPath(bookFolder);
			}
		}
	}

	/**
	 * Display a list of all books present in the book path. Clicking on a book will
	 * open it in the larex view.
	 *
	 * @param model
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "/")
	public String home(Model model) throws IOException {
		// Reset config
		String bookFolder = config.getSetting("bookpath");
    	String saveDir = config.getSetting("savedir");

		String timeStamp = String.valueOf(System.currentTimeMillis() / 1000L);

		if (!bookFolder.equals("")) {
			fileManager.setLocalBooksPath(bookFolder);
		}
		if (saveDir != null && !saveDir.equals("")) {
			fileManager.setSaveDir(saveDir);
		}
		File bookPath = new File(fileManager.getLocalBooksPath());
		if (!bookPath.isDirectory()) {
			logger.error("Specified bookpath {} no directory, Please set a valid bookpath in larex.properties!",
			bookPath);
			return "redirect:/500";
		}
		FileDatabase database = new FileDatabase(bookPath, config.getListSetting("imagefilter"), false);
		Library lib = new Library(database);

		model.addAttribute("library", lib);
		model.addAttribute("timeStamp", timeStamp);
		logger.info("Start LAREX using {}", this.fileManager.getConfigurationFile());
		return "lib";
	}

	/**
	 * Display a list of all books present in the book path. Clicking on a book will
	 * open it in the larex view.
	 *
	 * @param bookid
	 * @param bookpath
	 * @param type
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "library/getPageLocations", method = RequestMethod.POST, headers = "Accept=*/*")
	public @ResponseBody Map<String, List<String>> getPageLocations(@RequestParam(value = "bookid") int bookid, @RequestParam(value = "bookpath") String bookpath, @RequestParam(value = "booktype") String type) throws IOException {
		fileManager.init(servletContext);
		String booktype = "";
		try {
			booktype = java.net.URLDecoder.decode(type, StandardCharsets.UTF_8.name());
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		File baseFolder = new File(bookpath);
		if(!baseFolder.isDirectory()) {
			throw new IOException("Path is no directory, but should be in this instance");
		}
		try {
			switch (booktype) {
				case "flat":
					return getFileMap(baseFolder.getAbsolutePath(), Constants.IMG_EXTENSIONS_DOTTED);
				default:
					logger.error("Attempt open empty directory {}", baseFolder.getAbsolutePath());
					break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}
	/**
	 * Opens Mets file and returns all known filegroups and each imageLocation
	 *
	 * @param metsPath path to mets.xml
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "library/getMetsData", method = RequestMethod.POST, headers = "Accept=*/*")
	public @ResponseBody Map<String, List<List<List<String>>>> getMetsData(@RequestParam("metspath") String metsPath) throws IOException {
		if (!fileManager.isInit()) {
			fileManager.init(servletContext);
		}
		String path = "";
		try {
			path = java.net.URLDecoder.decode(metsPath, StandardCharsets.UTF_8.name());
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		File metsFile = new File(path + File.separator + "mets.xml");
		if(!metsFile.exists()) {
			metsFile = new File(path + File.separator + "data" + File.separator + "mets.xml");
			if (!metsFile.exists()) { throw new IOException("Mets file doesn't exist anymore"); }
		}
		return MetsReader.getFileGroups(metsFile.getAbsolutePath(), false);
	}
	/**
	 * Returns old Request to resend directrequest
	 *
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "library/getOldRequest", method = RequestMethod.POST, headers = "Accept=*/*")
	public @ResponseBody DirectRequest getOldRequest() {
		DirectRequest directRequest = this.fileManager.getDirectRequest();
		if (directRequest != null) {
			logger.info("pass last {}", directRequest.getMetsPath());
		}
		return directRequest;
	}
	
	/**
	 * returns each imagePath in given directory
	 * respecting the SubExtensionFilter set in properties
	 *
	 * @param baseFolder path to legacy baseFolder
	 * @param extList file extension to map
	 * @return map containing imageName and path
	 */
	public Map<String, List<String>> getFileMap(String baseFolder, List<String> extList) {
		//Moved imageSubFilter processing here
		List<String> imageSubFilter = config.getListSetting("imagefilter");
		if(!imageSubFilter.isEmpty()) {
			List<String> extListWithSub = new ArrayList<>();
			for(String ext : extList) {
				for(String subExt : imageSubFilter) {
					if(subExt.equals(".")) {
						extListWithSub.add(ext);
					} else {
						extListWithSub.add("." + subExt + ext);
					}
				}
			}
			extList = extListWithSub;
		}
		Map<String, List<String>> fileMap = new TreeMap<>();
		File directFolder = new File(baseFolder);
		File[] files = directFolder.listFiles();
		Arrays.sort(files);

		for (File file : files) {
			List<String> images = new ArrayList<>();
			String currentFileName = file.getName().split("\\.")[0];
			for (String ext : extList) {
				if(	(imageSubFilter.isEmpty() ||
						((file.getName().split("\\.").length == 3 && ext.split("\\.").length == 3) ||
						(file.getName().split("\\.").length == 2 && ext.split("\\.").length == 2))) &&
						file.getName().endsWith(ext)) {
					String path = file.getAbsolutePath();
					images.add(path);
				}
			}
			if(!images.isEmpty()) {
				if(fileMap.containsKey(currentFileName)) {
					images.addAll(fileMap.get(currentFileName));
				}
				images.sort(Comparator.naturalOrder());
				fileMap.put(currentFileName,images);
			}
		}
		return fileMap;
	}

	@RequestMapping(value ="library/getVersion" , method = RequestMethod.GET)
	public @ResponseBody
	String getVersion() {
		String larexVersion = System.getenv(ENV_LAREX_VERSION);
		if (larexVersion == null) {
			logger.warn("LAREX_VERSION unset in Env");
			larexVersion = "";
		}
		return "".equals(larexVersion) ? "UNKNOWN" : larexVersion;
	}
}
