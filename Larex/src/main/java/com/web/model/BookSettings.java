
package com.web.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.opencv.core.Size;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import larex.geometry.ExistingGeometry;
import larex.geometry.PointList;
import larex.geometry.positions.PriorityPosition;
import larex.geometry.positions.RelativePosition;
import larex.geometry.regions.RegionManager;
import larex.geometry.regions.RegionSegment;
import larex.geometry.regions.type.RegionType;
import larex.segmentation.parameters.ImageSegType;
import larex.segmentation.parameters.Parameters;

/**
 * Handels all parameters and settings passing through the gui to the
 * segmentation algorithm
 * 
 */
public class BookSettings {

	@JsonProperty("book")
	protected int bookID;
	@JsonProperty("pages")
	private LinkedList<PageSettings> pages;
	@JsonProperty("parameters")
	private Map<String, Integer> parameters;
	@JsonProperty("regions")
	protected Map<RegionType, Region> regions;
	@JsonProperty("global")
	protected PageSettings globalSettings;
	@JsonProperty("combine")
	protected boolean combine;
	@JsonProperty("imageSegType")
	private ImageSegType imageSegType;

	@JsonCreator
	public BookSettings(@JsonProperty("book") int bookID, @JsonProperty("pages") LinkedList<PageSettings> pages,
			@JsonProperty("parameters") Map<String, Integer> parameters,
			@JsonProperty("regions") Map<RegionType, Region> regions,
			@JsonProperty("global") PageSettings globalSettings, @JsonProperty("combine") boolean combine,
			@JsonProperty("imageSegType") ImageSegType imageSegType) {
		this.bookID = bookID;
		this.globalSettings = globalSettings;
		this.regions = regions;
		this.pages = pages;
		this.parameters = parameters;
		this.combine = combine;
		this.imageSegType = imageSegType;
	}

	public BookSettings(Parameters parameters, Book book) {
		this.bookID = book.getId();
		this.globalSettings = new PageSettings(book.getId());
		this.regions = new HashMap<RegionType, Region>();
		pages = new LinkedList<PageSettings>();
		for (Page page : book.getPages()) {
			pages.add(new PageSettings(page.getId()));
		}
		this.parameters = new HashMap<String, Integer>();

		this.parameters.put("binarythreash", parameters.getBinaryThresh());
		this.parameters.put("textdilationX", parameters.getTextDilationX());
		this.parameters.put("textdilationY", parameters.getTextDilationY());
		this.parameters.put("imagedilationX", parameters.getImageRemovalDilationX());
		this.parameters.put("imagedilationY", parameters.getImageRemovalDilationY());

		this.combine = parameters.isCombineImages();
		this.imageSegType = parameters.getImageSegType();

		this.regions = new HashMap<RegionType, Region>(regions);
		RegionManager regionManager = parameters.getRegionManager();
		for (larex.geometry.regions.Region region : regionManager.getRegions()) {
			RegionType regionType = region.getType();
			int minSize = region.getMinSize();
			int maxOccurances = region.getMaxOccurances();
			PriorityPosition priorityPosition = region.getPriorityPosition();
			com.web.model.Region guiRegion = new com.web.model.Region(regionType, minSize, maxOccurances,
					priorityPosition);

			int regionCount = 0;
			for (RelativePosition position : region.getPositions()) {
				LinkedList<Point> points = new LinkedList<Point>();
				points.add(new Point(position.getTopLeftXPercentage(), position.getTopLeftYPercentage()));
				points.add(new Point(position.getBottomRightXPercentage(), position.getTopLeftYPercentage()));
				points.add(new Point(position.getBottomRightXPercentage(), position.getBottomRightYPercentage()));
				points.add(new Point(position.getTopLeftXPercentage(), position.getBottomRightYPercentage()));

				String id = regionType.toString() + regionCount;
				guiRegion.addPolygon(new Polygon(id, regionType, points, true));
				regionCount++;
			}

			this.regions.put(regionType, guiRegion);
		}
	}

	/**
	 * Get general parameters from these book settings, without ExistingGeometry
	 * 
	 * @param pagesize
	 * @return
	 */
	public Parameters toParameters(Size pagesize) {
		// Set Parameters
		RegionManager regionmanager = new RegionManager();
		Parameters lParameters = new Parameters(new RegionManager(), (int) pagesize.height);

		for (larex.geometry.regions.Region region : regionmanager.getRegions()) {
			region.setPositions(new ArrayList<RelativePosition>());
		}

		lParameters.setBinaryThresh(parameters.get("binarythreash"));
		lParameters.setTextDilationX(parameters.get("textdilationX"));
		lParameters.setTextDilationY(parameters.get("textdilationY"));
		lParameters.setImageRemovalDilationX(parameters.get("imagedilationX"));
		lParameters.setImageRemovalDilationY(parameters.get("imagedilationY"));
		lParameters.setImageSegType(this.getImageSegType());
		lParameters.setCombineImages(this.isCombine());

		for (com.web.model.Region guiRegion : regions.values()) {
			RegionType regionType = guiRegion.getType();
			int minSize = guiRegion.getMinSize();
			int maxOccurances = guiRegion.getMaxOccurances();
			PriorityPosition priorityPosition = guiRegion.getPriorityPosition();

			larex.geometry.regions.Region region = regionmanager.getRegionByType(regionType);
			if (region == null) {
				region = new larex.geometry.regions.Region(regionType, minSize, maxOccurances, priorityPosition,
						new ArrayList<RelativePosition>());
				regionmanager.addRegion(region);
			} else {
				region.setMinSize(minSize);
				region.setMaxOccurances(maxOccurances);
			}

			for (Polygon polygon : guiRegion.getPolygons().values()) {
				List<Point> points = polygon.getPoints();
				if (points.size() == 4) {
					Point topLeft = points.get(0);
					Point bottomRight = points.get(2);
					RelativePosition position = new RelativePosition(topLeft.getX(), topLeft.getY(), bottomRight.getX(),
							bottomRight.getY());
					region.addPosition(position);

					// Set Ignore Region to fixed
					if (region.getType().equals(RegionType.ignore)) {
						position.setFixed(true);
					}
				}
			}

		}

		return lParameters;
	}

	/**
	 * Get specific page parameters from these book settings, with Existing Geometry
	 * of the page
	 * 
	 * @param pagesize
	 * @param pageID
	 * @return
	 */
	public Parameters toParameters(Size pagesize, int pageID) {
		// Set Parameters
		Parameters parameters = this.toParameters(pagesize);

		// Set existing Geometry
		ArrayList<RegionSegment> fixedPointLists = new ArrayList<>();
		for (Polygon fixedSegment : this.getPage(pageID).getFixedSegments().values()) {
			ArrayList<java.awt.Point> points = new ArrayList<java.awt.Point>();
			for (Point point : fixedSegment.getPoints()) {
				points.add(new java.awt.Point((int) point.getX(), (int) point.getY()));
			}
			RegionSegment fixedPointList = new RegionSegment(points, fixedSegment.getId());
			fixedPointList.setType(fixedSegment.getType());
			fixedPointLists.add(fixedPointList);
		}

		// Set existing cuts
		ArrayList<PointList> cuts = new ArrayList<>();
		for (Polygon cut : this.getPage(pageID).getCuts().values()) {
			ArrayList<java.awt.Point> points = new ArrayList<java.awt.Point>();
			for (Point point : cut.getPoints()) {
				points.add(new java.awt.Point((int) point.getX(), (int) point.getY()));
			}
			cuts.add(new PointList(points, cut.getId()));
		}

		parameters.setExistingGeometry(new ExistingGeometry(fixedPointLists, cuts));

		return parameters;
	}

	public Map<RegionType, Region> getRegions() {
		return new HashMap<RegionType, Region>(regions);
	}

	public void addPage(PageSettings page) {
		pages.add(page);
	}

	public LinkedList<PageSettings> getPages() {
		return new LinkedList<PageSettings>(pages);
	}

	public PageSettings getPage(int pageNr) {
		return pages.get(pageNr);
	}

	public Map<String, Integer> getParameters() {
		return parameters;
	}

	public int getBookID() {
		return bookID;
	}

	public PageSettings getGlobalSettings() {
		return globalSettings;
	}

	public ImageSegType getImageSegType() {
		return imageSegType;
	}

	public boolean isCombine() {
		return combine;
	}

}
