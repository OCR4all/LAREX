package com.web.controller;

import java.io.File;

import javax.servlet.ServletContext;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("session")
/**
 * FileManager to manage essential resources paths.
 * Converts and provides url resources paths and local disc paths.
 */
public class FileManager {

	private boolean isInit = false;
	private ServletContext servletContext;
	private String booksPath;

	/**
	 * Init FileManager with servletContext in order to provide f
	 * 
	 * @param servletContext ServletContext of the current web application, in order
	 *                       to get abs disc paths
	 */
	public void init(ServletContext servletContext) {
		this.isInit = true;
		this.servletContext = servletContext;
		booksPath = servletContext.getRealPath("resources" + File.separator + "books");
	}

	/**
	 * Get the web url path, starting after the domain, for the resources folder
	 * 
	 * @return url path of the resources folder, starting at domain/
	 */
	public String getURLResourcesPath() {
		return "resources" + File.separator;
	}

	/**
	 * Get the web url path, starting after the domain, for book images
	 * 
	 * @return url path of the book images, starting at domain/
	 */
	public String getURLBooksPath() {
		return "images" + File.separator + "books" + File.separator;
	}

	/**
	 * Get the local disc path to the resources folder
	 * 
	 * @return disc path to the resources folder
	 */
	public String getLocalResourcesPath() {
		return convertURLPathToLocalPath(getURLResourcesPath());
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
	 * Convert a url path to a disc path
	 * 
	 * @param urlPath url path starting after the domain
	 * @return local disc path
	 */
	public String convertURLPathToLocalPath(String urlPath) {
		return servletContext.getRealPath(urlPath);
	}

	/**
	 * Get the path to the larex configuration path. Take LAREX_CONFIG system
	 * variable if exists and points to an existing file, else take default path
	 * 
	 * @return local Larex configuration path
	 */
	public String getConfigurationFile() {
		String configPathVariable = System.getenv("LAREX_CONFIG");
		File configFile = new File(configPathVariable);
		if (configPathVariable != null && !configPathVariable.equals("") && configFile.exists())
			return configPathVariable;
		else
			return servletContext.getRealPath("WEB-INF" + File.separator + "larex.config");
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
	 * Check if the FileManager is initialized
	 * 
	 * @return true if has been initialized, else false
	 */
	public boolean isInit() {
		return isInit;
	}
}
