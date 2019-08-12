
package com.web.facade.segmentation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.opencv.core.Size;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.web.model.Book;
import com.web.model.Page;
import com.web.model.Point;
import com.web.model.Polygon;
import com.web.model.Region;

import larex.geometry.ExistingGeometry;
import larex.geometry.PointList;
import larex.geometry.positions.PriorityPosition;
import larex.geometry.positions.RelativePosition;
import larex.geometry.regions.RegionManager;
import larex.geometry.regions.RegionSegment;
import larex.geometry.regions.type.PAGERegionType;
import larex.geometry.regions.type.RegionSubType;
import larex.geometry.regions.type.TypeConverter;
import larex.segmentation.parameters.ImageSegType;
import larex.segmentation.parameters.Parameters;

/**
 * Handels all parameters and settings passing through the gui to the
 * segmentation algorithm
 * 
 */
public class SegmentationSettings {

	@JsonProperty("book")
	protected int bookID;
	@JsonProperty("pages")
	private LinkedList<FixedGeometry> pages;
	@JsonProperty("parameters")
	private Map<String, Integer> parameters;
	@JsonProperty("regions")
	protected Map<String, RegionArea> regions;
	@JsonProperty("combine")
	protected boolean combine;
	@JsonProperty("imageSegType")
	private ImageSegType imageSegType;

	@JsonCreator
	public SegmentationSettings(@JsonProperty("book") int bookID, @JsonProperty("pages") LinkedList<FixedGeometry> pages,
			@JsonProperty("parameters") Map<String, Integer> parameters,
			@JsonProperty("regions") Map<String, RegionArea> regions,
			@JsonProperty("combine") boolean combine,
			@JsonProperty("imageSegType") ImageSegType imageSegType) {
		this.bookID = bookID;
		this.regions = regions;
		this.pages = pages;
		this.parameters = parameters;
		this.combine = combine;
		this.imageSegType = imageSegType;
	}

	public SegmentationSettings(Parameters parameters, Book book) {
		this.bookID = book.getId();
		this.regions = new HashMap<String, RegionArea>();
		pages = new LinkedList<FixedGeometry>();
		for (Page page : book.getPages()) {
			pages.add(new FixedGeometry());
		}
		this.parameters = new HashMap<String, Integer>();

		this.parameters.put("textdilationX", parameters.getTextDilationX());
		this.parameters.put("textdilationY", parameters.getTextDilationY());
		this.parameters.put("imagedilationX", parameters.getImageRemovalDilationX());
		this.parameters.put("imagedilationY", parameters.getImageRemovalDilationY());

		this.combine = parameters.isCombineImages();
		this.imageSegType = parameters.getImageSegType();

		this.regions = new HashMap<String, RegionArea>(regions);
		RegionManager regionManager = parameters.getRegionManager();
		for (larex.geometry.regions.Region region : regionManager.getRegions()) {
			String regionType = region.getType().toString();
			int minSize = region.getMinSize();
			int maxOccurances = region.getMaxOccurances();
			PriorityPosition priorityPosition = region.getPriorityPosition();
			com.web.facade.segmentation.RegionArea guiRegion = new com.web.facade.segmentation.RegionArea(regionType, minSize, maxOccurances,
					priorityPosition);

			int regionCount = 0;
			for (RelativePosition position : region.getPositions()) {
				LinkedList<Point> points = new LinkedList<Point>();
				points.add(new Point(position.getTopLeftXPercentage(), position.getTopLeftYPercentage()));
				points.add(new Point(position.getBottomRightXPercentage(), position.getTopLeftYPercentage()));
				points.add(new Point(position.getBottomRightXPercentage(), position.getBottomRightYPercentage()));
				points.add(new Point(position.getTopLeftXPercentage(), position.getBottomRightYPercentage()));

				String id = regionType.toString() + regionCount;
				guiRegion.addPolygon(new Region(id, regionType, points, true, new HashMap<>(), new ArrayList<>()));
				regionCount++;
			}

			this.regions.put(regionType, guiRegion);
		}
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
		RegionManager regionmanager = new RegionManager(new HashSet<>());
		Parameters parameters = new Parameters(regionmanager, (int) pagesize.height);

		parameters.setTextDilationX(this.parameters.get("textdilationX"));
		parameters.setTextDilationY(this.parameters.get("textdilationY"));
		parameters.setImageRemovalDilationX(this.parameters.get("imagedilationX"));
		parameters.setImageRemovalDilationY(this.parameters.get("imagedilationY"));
		parameters.setImageSegType(this.getImageSegType());
		parameters.setCombineImages(this.isCombine());

		for (com.web.facade.segmentation.RegionArea guiRegion : regions.values()) {
			PAGERegionType regionType = TypeConverter.stringToPAGEType(guiRegion.getType());
			int minSize = guiRegion.getMinSize();
			int maxOccurances = guiRegion.getMaxOccurances();
			PriorityPosition priorityPosition = guiRegion.getPriorityPosition();

			larex.geometry.regions.Region region = new larex.geometry.regions.Region(regionType, minSize, maxOccurances, priorityPosition,
					new ArrayList<RelativePosition>());
			regionmanager.addRegion(region);

			for (Region polygon : guiRegion.getPolygons().values()) {
				List<Point> points = polygon.getPoints();
				if (points.size() == 4) {
					Point topLeft = points.get(0);
					Point bottomRight = points.get(2);
					RelativePosition position = new RelativePosition(topLeft.getX(), topLeft.getY(), bottomRight.getX(),
							bottomRight.getY());
					region.addPosition(position);

					// Set Ignore Region to fixed
					if (RegionSubType.ignore.equals(region.getType().getSubtype())) {
						position.setFixed(true);
					}
				}
			}
		}

		// Set existing Geometry
		ArrayList<RegionSegment> fixedPointLists = new ArrayList<>();
		for (Region fixedSegment : this.getPage(pageID).getFixedSegments().values()) {
			ArrayList<java.awt.Point> points = new ArrayList<java.awt.Point>();
			for (Point point : fixedSegment.getPoints()) {
				points.add(new java.awt.Point((int) point.getX(), (int) point.getY()));
			}
			RegionSegment fixedPointList = new RegionSegment(points, fixedSegment.getId());
			fixedPointList.setType(TypeConverter.stringToPAGEType(fixedSegment.getType()));
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

	public Map<String, RegionArea> getRegions() {
		return new HashMap<String, RegionArea>(regions);
	}

	public FixedGeometry getPage(int pageNr) {
		return pages.get(pageNr);
	}

	public Map<String, Integer> getParameters() {
		return parameters;
	}

	public int getBookID() {
		return bookID;
	}

	public ImageSegType getImageSegType() {
		return imageSegType;
	}

	public boolean isCombine() {
		return combine;
	}

}
