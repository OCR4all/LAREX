package com.web.controller;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;

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

import com.web.communication.ExportRequest;
import com.web.communication.SegmentationRequest;
import com.web.facade.LarexFacade;

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
	public @ResponseBody ResponseEntity<byte[]> exportXML() {
	    return facade.getPageXML();
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
	public @ResponseBody String uploadSettings(@RequestParam("file") MultipartFile file) {

		if (!file.isEmpty()) {
			try {
				byte[] bytes = file.getBytes();
				return "File has been received";
			} catch (Exception e) {
				return "File could not been received " +  " => " + e.getMessage();
			}
		} else {
			return "File is empty";
		}
	}
}
