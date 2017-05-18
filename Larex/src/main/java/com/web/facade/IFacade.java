package com.web.facade;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.web.model.Book;
import com.web.model.BookSegmentation;
import com.web.model.BookSettings;

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

	public BookSettings getDefaultSettings(Book book);
	
	public BookSegmentation segmentAll(BookSettings settings);
	
	public BookSegmentation segmentPages(BookSettings settings, List<Integer> pages);
	
	public BookSegmentation segmentPage(BookSettings settings, int pageNr);

	public ResponseEntity<byte[]> getPageXML(int pageID);

}
