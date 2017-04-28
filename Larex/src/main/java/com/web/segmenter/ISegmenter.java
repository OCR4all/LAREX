package com.web.segmenter;

import java.util.List;

import com.web.model.BookSegmentation;
import com.web.model.BookSettings;

/**
 * Interface to specify the functionality of a Segmenter
 * 
 */
/* A Segmenter must to include the following annontations:
@Component
@Scope("session")
 */
public interface ISegmenter {


	public BookSegmentation segmentAll(BookSettings settings);
	
	public BookSegmentation segmentPages(BookSettings settings, List<Integer> pages);
	
	public BookSegmentation segmentPage(BookSettings settings, int pageNr);
	
}
