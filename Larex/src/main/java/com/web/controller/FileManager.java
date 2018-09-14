package com.web.controller;

import java.io.File;

import javax.servlet.ServletContext;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("session")
public class FileManager {

	private boolean isInit = false;
	private ServletContext servletContext;
	private String booksPath;

	public void init(ServletContext servletContext) {
		this.isInit = true;
		this.servletContext = servletContext;
		booksPath = servletContext.getRealPath("resources"+File.separator+"books");
	}

	public String getWebResourcesPath() {
		return  "resources" + File.separator;
	}

	public String getWebBooksPath() {
		return "images" + File.separator
				+ "books" + File.separator;
	}

	public String getResourcesPath() {
		return convertWebPathToRealPath(getWebResourcesPath());
	}

	public String getBooksPath() {
		return booksPath;
	}

	public String convertWebPathToRealPath(String path) {
		return servletContext.getRealPath(path);
	}
	
	public String getConfigurationFile() {
		return servletContext.getRealPath("WEB-INF"+File.separator+"larex.config");
	}
	
	public void setBooksPath(String booksPath) {
		this.booksPath = new File(booksPath).getAbsolutePath();
	}

	public boolean isInit() {
		return isInit;
	}
}
