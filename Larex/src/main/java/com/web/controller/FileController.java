package com.web.controller;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;

import com.web.communication.ExportRequest;
import com.web.communication.SegmentationRequest;
import com.web.facade.LarexFacade;
import com.web.model.BookSettings;

/**
 * Communication Controller to handle requests for the main viewer/editor.
 * Handles requests about displaying book scans and segmentations.
 * 
 */
@Controller
@Scope("request")
public class FileController {
	@Autowired
	private LarexFacade facade;
	
	@RequestMapping(value = "/prepareExport", method = RequestMethod.POST, headers = "Accept=*/*", produces = "application/json", consumes = "application/json")
	public @ResponseBody String prepareExport(@RequestBody ExportRequest exportRequest) {
		facade.prepareExport(exportRequest);
		return "Export has been prepared";
	}
	
	@RequestMapping(value = "/exportXML")//, method = RequestMethod.GET)//, headers = "Accept=*/*", consumes = "application/json"*/)
	public @ResponseBody ResponseEntity<byte[]> exportXML(@RequestParam("version") String version) {
	    return facade.getPageXML(version);
	}
	
	@RequestMapping(value = "/saveSettings", method = RequestMethod.POST, headers = "Accept=*/*", produces = "application/json", consumes = "application/json")
	public @ResponseBody String saveSettings(@RequestBody SegmentationRequest exportRequest) {
		facade.prepareSettings(exportRequest.getSettings());
		return "Export has been prepared";
	}
	
	@RequestMapping(value = "/downloadSettings")//, method = RequestMethod.GET)//, headers = "Accept=*/*", consumes = "application/json"*/)
	public @ResponseBody ResponseEntity<byte[]> downloadSettings() {
	    return facade.getSettingsXML();
	}

	/*@RequestMapping(value = "/uploadSettings", method = RequestMethod.POST)
	public @ResponseBody String uploadSettings(@RequestParam("file") String file) {
		System.out.println(" \n"+ file);
		return "Upload";
	}*/
	
	@RequestMapping(value = "/uploadSettings", method = RequestMethod.POST)
	public @ResponseBody BookSettings uploadSettings(@RequestParam("file") MultipartFile file) {
		BookSettings settings = facade.getDefaultSettings(facade.getBook());
		if (!file.isEmpty()) {
			try {
				byte[] bytes = file.getBytes();
				settings = facade.readSettings(bytes);
			} catch (Exception e) {
			}
		} 
		return settings;
	}
}
