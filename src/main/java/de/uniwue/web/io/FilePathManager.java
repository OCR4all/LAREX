package de.uniwue.web.io;

import java.io.File;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;

import de.uniwue.web.communication.DirectRequest;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import de.uniwue.web.model.Page;

@Component
@Scope("session")
/**
 * FilePathManager is used to manage essential resources paths, locally and in the url.
 * This manager must be initialized with a current ServletContext to provide correct url paths.
 */
public class FilePathManager {

	private boolean isInit = false;
	private ServletContext servletContext;
	private String booksPath;
	private String saveDir;
	private Map<String, String> xmlMap;
	private Map<String, List<String>> imageMap;
	private Boolean isFlat;
	private String nonFlatBookName;
	private int nonFlatBookId;
	private Boolean customFlag;
	private String customFolder;
	private DirectRequest directRequest = null;

	/**
	 * Init FileManager with servletContext in order to provide f
	 *
	 * @param servletContext ServletContext of the current web application, in order
	 *                       to get abs disc paths
	 */
	public void init(ServletContext servletContext) {
		this.isInit = true;
		this.servletContext = servletContext;
		this.booksPath = servletContext.getRealPath("resources" + File.separator + "books");
		this.isFlat = true;
	}

	/**
	 * Init FileManager with servletContext in order to provide f
	 *
	 * @param servletContext ServletContext of the current web application, in order
	 *                       to get abs disc paths
	 */
	public void init(ServletContext servletContext, Boolean isFlat) {
		this.isInit = true;
		this.servletContext = servletContext;
		this.booksPath = servletContext.getRealPath("resources" + File.separator + "books");
		this.isFlat = false;
	}

	/**
	 * Get the local disc path to the books folder
	 *
	 * @return disc path to the books folder
	 */
	public String getLocalBooksPath() {
		return booksPath;
	}

	/**
	 * Get the local map to all pagexml paths
	 *
	 * @return map to all local xml paths
	 */
	public Map<String, String> getLocalXmlMap() {
		return xmlMap;
	}

	/**
	 * Get the local disc path to the books folder
	 *
	 * @return disc path to the books folder
	 */
	public Map<String, List<String>> getLocalImageMap() {
		return imageMap;
	}

	/**
	 * Get the local disc path to the savedir
	 *
	 * @return disc path to the savedir
	 */
	public String getSaveDir() {
		return saveDir;
	}

	/**
	 * Get the path to the larex configuration path. Take LAREX_CONFIG system
	 * variable if exists and points to an existing file, else take default path
	 *
	 * @return local Larex configuration path
	 */
	public String getConfigurationFile() {
		String configPathVariable = System.getenv("LAREX_CONFIG");
		if (configPathVariable != null && !configPathVariable.equals("") && new File(configPathVariable).exists())
			return configPathVariable;
		else
			return servletContext.getRealPath("WEB-INF" + File.separator + "larex.properties");
	}

	/**
	 * Get the path to the default virtual keyboard.
	 *
	 * @return local default virtual keyboard path
	 */
	public String getVirtualKeyboardFile() {
		return servletContext.getRealPath("WEB-INF" + File.separator + "virtual_keyboards" + File.separator + "default.txt");
	}

	public String getVirtualKeyboardFile(String language) {
		return servletContext.getRealPath("WEB-INF" + File.separator + "virtual_keyboards" + File.separator + language + ".txt");
	}

	/**
	 * Change the local disc books path
	 *
	 * @param booksPath path the bookspath is about to point to
	 */
	public void setLocalBooksPath(String booksPath) {
		this.booksPath = new File(booksPath).getAbsolutePath();
	}

	/**
	 * Change the local disc savedir
	 *
	 * @param saveDir path the savedir is about to point to
	 */
	public void setSaveDir(String saveDir) {
		this.saveDir = new File(saveDir).getAbsolutePath();
	}

	/**
	 * Change the local disc book map structure
	 *
	 * @param xmlmap Map linking every pageID to a path for its pagexml
	 * @param imagemap Map linking every pageID to a path for its image
	 */
	public void setLocalBookMap(Map<String, String> xmlmap,Map<String, List<String>> imagemap) {
		this.xmlMap = xmlmap;
		this.imageMap = imagemap;
	}

	/**
	 * Find the local disc path to a page image
	 *
	 * @param page
	 * @return
	 */
	public File getImagePath(Page page) {
		return new File(this.getLocalBooksPath() + File.separator + page.getImages().get(0));
	}

	/**
	 * Find the local disc path to a page annotations file
	 *
	 * @param bookname
	 * @param pagename
	 * @return
	 */
	public File getAnnotationPath(String bookname, String pagename) {
		File annotationPathBookPath = new File(this.getLocalBooksPath() + File.separator + bookname + File.separator + pagename + ".xml");
		if (this.getSaveDir() != null) {
			File annotationPathSaveDir = new File(this.getSaveDir() + File.separator + bookname + File.separator + pagename + ".xml");
			if (annotationPathSaveDir.exists()) {
				return annotationPathSaveDir;
			} else {
				return annotationPathBookPath;
			}
		} else {
			return annotationPathBookPath;
		}
	}

	/**
	 * Check if the FileManager is initialized
	 *
	 * @return true if has been initialized, else false
	 */
	public boolean isInit() {
		return isInit;
	}

	/**
	 * Set file managar structure flag
	 *
	 * @return true if has been initialized, else false
	 */
	public void setIsFlat(Boolean isFlat) {
		this.isFlat = isFlat;
	}

	/**
	 * Set bookname for nonflat
	 *
	 * @return true if has been initialized, else false
	 */
	public void setNonFlatBookName(String bookName) {
		this.nonFlatBookName = bookName;
	}

	/**
	 * Get bookname for nonflat
	 *
	 * @return true if has been initialized, else false
	 */
	public String getNonFlatBookName() {
		return this.nonFlatBookName;
	}

	/**
	 * Set bookname for nonflat
	 *
	 * @return true if has been initialized, else false
	 */
	public void setNonFlatBookId(int bookId) {
		this.nonFlatBookId = bookId;
	}

	/**
	 * Get bookname for nonflat
	 *
	 * @return true if has been initialized, else false
	 */
	public int getNonFlatBookId() {
		return this.nonFlatBookId;
	}

	/**
	 * Check if the FileManager is flat
	 *
	 * @return true if has been initialized, else false
	 */
	public Boolean checkFlat() {
		return this.isFlat;
	}

	/**
	 * Get customFolder
	 * @return customFolder
	 */
	public String getCustomFolder() {
		return customFolder;
	}

	public DirectRequest getDirectRequest() {
		return directRequest;
	}

	public void setDirectRequest(DirectRequest directRequest) {
		this.directRequest = directRequest;
	}

}
