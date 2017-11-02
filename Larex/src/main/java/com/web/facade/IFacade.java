package com.web.facade;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.web.communication.ExportRequest;
import com.web.model.Book;
import com.web.model.BookSegmentation;
import com.web.model.BookSettings;
import com.web.model.Polygon;

/**
 * Interface to specify the functionality of a Segmenter
 * 
 */
/* A Segmenter must include the following annontations:
@Component
@Scope("session")
 */
public interface IFacade {

	public void init(Book book, String resourcepath);

	public void setBook(Book book);
	
	public Book getBook();

	public boolean isInit();

	public void clear();
	
	public BookSettings getDefaultSettings(Book book);
	
	public BookSegmentation segmentAll(BookSettings settings);
	
	public BookSegmentation segmentPages(BookSettings settings, List<Integer> pages, boolean allowLocalResults);
	
	public BookSegmentation segmentPage(BookSettings settings, int pageNr, boolean allowLocalResults);
	
	public BookSegmentation readPageXML(byte[] pageXML,int pageNr);
	
	public Polygon merge(List<String> segments,int pageNr);
	
	//TODO Change to different class? (for low coupling)
	public void prepareExport(ExportRequest exportRequest);

	public ResponseEntity<byte[]> getPageXML(String version);
	
	public void savePageXMLLocal(String saveDir, String version);
	
	public void prepareSettings(BookSettings settings);
	
	public ResponseEntity<byte[]> getSettingsXML();
		
	public BookSettings readSettings(byte[] settingsFile);

}
