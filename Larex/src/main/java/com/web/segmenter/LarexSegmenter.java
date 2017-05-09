package com.web.segmenter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import com.web.model.Book;
import com.web.model.BookSegmentation;
import com.web.model.BookSettings;
import com.web.model.Page;
import com.web.model.PageSegmentation;

import larex.regions.RegionManager;
import larex.segmentation.Segmenter;
import larex.segmentation.parameters.Parameters;
import larex.segmentation.result.ResultRegion;
import larex.segmentation.result.SegmentationResult;

import org.opencv.core.Size;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Segmenter using the Larex project/algorithm
 * 
 */
@Component
@Scope("session")
public class LarexSegmenter implements ISegmenter {

	private String resourcepath;
	private Book book;
	private BookSegmentation bookSegment;
	private Segmenter segmenter;
	private Parameters parameters;
	private boolean isInit = false;

	public void init(Book book, String resourcepath) {
		this.book = book;
		this.bookSegment = new BookSegmentation(book.getId());
		this.resourcepath = resourcepath;
		this.isInit = true;
	}

	public void setBook(Book book) {
		this.book = book;
		this.bookSegment = new BookSegmentation(book.getId());
	}

	public Book getBook() {
		return book;
	}

	public boolean isInit() {
		return isInit;
	}

	@Override
	public BookSegmentation segmentAll(BookSettings settings) {
		if (book == null || !(settings.getBookID() == book.getId())) {
			// TODO Error
		}

		// TODO Settings changed?
		//bookSegment = new BookSegmentation(book.getId());

		for (Page page : book.getPages()) {
			bookSegment.setPage(segment(settings, page), page.getId());
		}

		return bookSegment;
	}

	@Override
	public BookSegmentation segmentPages(BookSettings settings, List<Integer> pages) {
		if (book == null || !(settings.getBookID() == book.getId())) {
			// TODO Error
		}

		// TODO Settings changed?
		//bookSegment = new BookSegmentation(book.getId());
		for (int pageNr : pages) {
			Page page = book.getPage(pageNr);
			bookSegment.setPage(segment(settings, page), page.getId());
		}
		return bookSegment;
	}

	@Override
	public BookSegmentation segmentPage(BookSettings settings, int pageNr) {
		return segmentPages(settings, Arrays.asList(pageNr));
	}
	
	public BookSettings getDefaultSettings(Book book){
		RegionManager regionmanager = new RegionManager();
		Parameters parameters = new Parameters(regionmanager,0);
		return LarexTranslator.translateParametersToSettings(parameters, book);
	}

	private PageSegmentation segment(BookSettings settings, Page page) {
		// TODO Performance
		String imagePath = resourcepath + File.separator + page.getImage();
		String imageIdentifier = "" + page.getId();

		// TODO Regionmanager + GUI ? Delete?
		larex.dataManagement.Page pageSegment = new larex.dataManagement.Page(imagePath, imageIdentifier);

		pageSegment.initPage();
		
		Size pagesize = pageSegment.getOriginalSize();
		
		parameters = LarexTranslator.translateSettingsToParameters(settings, parameters ,page, pagesize);
		parameters.getRegionManager().setPointListManager(LarexTranslator.translateSettingsToPointListManager(settings, page.getId()));
		
		if(segmenter == null){
			segmenter = new Segmenter(parameters);
		}else{
			segmenter.setParameters(parameters);
		}
		SegmentationResult segmentationResult = segmenter.segment(pageSegment.getOriginal());

		ArrayList<ResultRegion> regions = segmentationResult.getRegions();

		PageSegmentation segmentation = LarexTranslator.translateResultRegionsToSegmentation(regions, page.getId());

		return segmentation;
	}
}