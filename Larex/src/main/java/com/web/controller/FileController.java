package com.web.controller;

import java.io.File;

import javax.servlet.ServletContext;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("session")
public class FileController {

	private boolean isInit = false;
	private ServletContext servletContext;

	public void init(ServletContext servletContext) {
		this.isInit = true;
		this.servletContext = servletContext;
	}

	public String getWebResourcesPath() {
		return  "resources" + File.separator;
	}

	public String getWebBooksPath() {
		return "resources" + File.separator
				+ "books" + File.separator;
	}

	public String getResourcesPath() {
		return convertWebPathToRealPath(getWebResourcesPath());
	}

	public String getBooksPath() {
		return convertWebPathToRealPath(getWebBooksPath());
	}

	public String convertWebPathToRealPath(String path) {
		return servletContext.getRealPath(path);
	}

	public boolean isInit() {
		return isInit;
	}
}
