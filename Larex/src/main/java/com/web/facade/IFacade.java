package com.web.facade;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.web.controller.FileManager;
import com.web.model.Book;
import com.web.model.BookSettings;
import com.web.model.PageSegmentation;
import com.web.model.Polygon;

/**
 * Interface to specify the functionality of a Segmenter
 * 
 */
/*
 * A Segmenter must include the following annontations:
 * 
 * @Component
 * 
 * @Scope("session")
 */
public interface IFacade {

	public void init(FileManager fileManager);

	public boolean isInit();

	public void clear();

	public BookSettings getDefaultSettings(Book book);

	public PageSegmentation segmentPage(BookSettings settings, int pageNr, boolean allowLocalResults);

	public PageSegmentation readPageXML(byte[] pageXML, int pageNr, int bookID);

	public Polygon merge(List<Polygon> segments, int pageNr, int bookID);

	// TODO Change to different class? (for low coupling)
	public void prepareExport(PageSegmentation segmentation, int bookID);

	public ResponseEntity<byte[]> getPageXML(String version, int bookID);

	public void savePageXMLLocal(String saveDir, String version, int bookID);

	public void prepareSettings(BookSettings settings);

	public ResponseEntity<byte[]> getSettingsXML(int bookID);

	public BookSettings readSettings(byte[] settingsFile, int bookID);

}
