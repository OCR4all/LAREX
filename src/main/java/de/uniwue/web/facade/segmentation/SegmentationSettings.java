package de.uniwue.web.facade.segmentation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.opencv.core.Size;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.uniwue.algorithm.geometry.ExistingGeometry;
import de.uniwue.algorithm.geometry.PointList;
import de.uniwue.algorithm.geometry.positions.PriorityPosition;
import de.uniwue.algorithm.geometry.positions.RelativePosition;
import de.uniwue.algorithm.geometry.regions.RegionManager;
import de.uniwue.algorithm.geometry.regions.RegionSegment;
import de.uniwue.algorithm.geometry.regions.type.PAGERegionType;
import de.uniwue.algorithm.geometry.regions.type.RegionSubType;
import de.uniwue.algorithm.geometry.regions.type.TypeConverter;
import de.uniwue.algorithm.segmentation.parameters.ImageSegType;
import de.uniwue.algorithm.segmentation.parameters.Parameters;
import de.uniwue.web.model.Book;
import de.uniwue.web.model.Point;
import de.uniwue.web.model.Polygon;
import de.uniwue.web.model.Region;

/**
 * Handles all parameters and settings passing through the gui to the
 * segmentation algorithm
 *
 */
public class SegmentationSettings {

	@JsonProperty("book")
	protected int bookID;
	@JsonProperty("fixedGeometry")
	private FixedGeometry fixedGeometry;
	@JsonProperty("parameters")
	private Map<String, Integer> parameters;
	@JsonProperty("regions")
	protected Map<String, RegionSettings> regions;
	@JsonProperty("regionTypes")
	protected Map<String, Integer> regionTypes;
	@JsonProperty("combine")
	protected boolean combine;
	@JsonProperty("imageSegType")
	private ImageSegType imageSegType;

	@JsonCreator
	public SegmentationSettings(@JsonProperty("book") int bookID, @JsonProperty("fixedGeometry") FixedGeometry fixedGeometry,
								@JsonProperty("parameters") Map<String, Integer> parameters,
								@JsonProperty("regions") Map<String, RegionSettings> regions,
								@JsonProperty("regionTypes") Map<String, Integer> regionTypes,
								@JsonProperty("combine") boolean combine,
								@JsonProperty("imageSegType") ImageSegType imageSegType) {
		this.bookID = bookID;
		this.regions = regions;
		this.fixedGeometry = fixedGeometry;
		this.parameters = parameters;
		this.combine = combine;
		this.imageSegType = imageSegType;
		this.regionTypes = new HashMap<String, Integer>();
		int i = 0;
		for (PAGERegionType type : PAGERegionType.values()) {
			regionTypes.put(type.toString(), i);
			i++;
		}
	}

	public SegmentationSettings(Book book) {
		this(new Parameters(), book);
	}

	public SegmentationSettings(Parameters parameters, Book book) {
		this.bookID = book.getId();
		this.regions = new HashMap<String, RegionSettings>();
		fixedGeometry = new FixedGeometry();
		this.parameters = new HashMap<String, Integer>();

		this.parameters.put("textdilationX", parameters.getTextDilationX());
		this.parameters.put("textdilationY", parameters.getTextDilationY());
		this.parameters.put("imagedilationX", parameters.getImageRemovalDilationX());
		this.parameters.put("imagedilationY", parameters.getImageRemovalDilationY());

		this.combine = parameters.isCombineImages();
		this.imageSegType = parameters.getImageSegType();

		this.regions = new HashMap<String, RegionSettings>(regions);
		RegionManager regionManager = parameters.getRegionManager();
		for (de.uniwue.algorithm.geometry.regions.Region region : regionManager.getRegions()) {
			String regionType = region.getType().toString();
			int minSize = region.getMinSize();
			int maxOccurances = region.getMaxOccurances();
			PriorityPosition priorityPosition = region.getPriorityPosition();
			RegionSettings guiRegion = new RegionSettings(regionType, minSize, maxOccurances,
					priorityPosition);

			int regionCount = 0;
			for (RelativePosition position : region.getPositions()) {
				Polygon coords = new Polygon(true);
				coords.addPoint(new Point(position.left(), position.top()));
				coords.addPoint(new Point(position.right(), position.top()));
				coords.addPoint(new Point(position.right(), position.bottom()));
				coords.addPoint(new Point(position.left(), position.bottom()));

				String id = regionType.toString() + regionCount;
				guiRegion.addArea(new Region(id, regionType, null, coords, new HashMap<>(), new ArrayList<>()));
				regionCount++;
			}

			this.regions.put(regionType, guiRegion);
		}
		this.regionTypes = new HashMap<>();
		int i = 0;
		for (PAGERegionType type : PAGERegionType.values()) {
			regionTypes.put(type.toString(), i);
			i++;
		}
	}

	/**
	 * Get specific page parameters from these book settings, with Existing Geometry
	 * of the page
	 *
	 * @param pagesize
	 * @return
	 */
	public Parameters toParameters(Size pagesize) {
		// Set Parameters
		RegionManager regionmanager = new RegionManager(new HashSet<>());
		Parameters parameters = new Parameters(regionmanager, (int) pagesize.height);

		parameters.setTextDilationX(this.parameters.get("textdilationX"));
		parameters.setTextDilationY(this.parameters.get("textdilationY"));
		parameters.setImageRemovalDilationX(this.parameters.get("imagedilationX"));
		parameters.setImageRemovalDilationY(this.parameters.get("imagedilationY"));
		parameters.setImageSegType(this.getImageSegType());
		parameters.setCombineImages(this.isCombine());

		for (de.uniwue.web.facade.segmentation.RegionSettings guiRegion : regions.values()) {
			PAGERegionType regionType = TypeConverter.stringToPAGEType(guiRegion.getType());
			int minSize = guiRegion.getMinSize();
			int maxOccurances = guiRegion.getMaxOccurances();
			PriorityPosition priorityPosition = guiRegion.getPriorityPosition();


			Collection<RelativePosition> positions = new ArrayList<>();
			for (Region polygon : guiRegion.getAreas().values()) {
				List<Point> points = polygon.getCoords().getPoints();
				if (points.size() == 4) {
					Point topLeft = points.get(0);
					Point bottomRight = points.get(2);
					RelativePosition position = new RelativePosition(topLeft.getX(), topLeft.getY(), bottomRight.getX(),
							bottomRight.getY());
					positions.add(position);

					// Set Ignore Region to fixed
					if (RegionSubType.ignore.equals(regionType.getSubtype())) {
						position.setFixed(true);
					}
				}
			}
			de.uniwue.algorithm.geometry.regions.Region region = new de.uniwue.algorithm.geometry.regions.Region(
					regionType, minSize, maxOccurances, priorityPosition,
					positions);
			regionmanager.addArea(region);
		}

		// Set existing Geometry
		ArrayList<RegionSegment> fixedPointLists = new ArrayList<>();
		for (Region fixedSegment : this.fixedGeometry.getFixedSegments().values()) {
			ArrayList<java.awt.Point> points = new ArrayList<java.awt.Point>();
			for (Point point : fixedSegment.getCoords().getPoints()) {
				points.add(new java.awt.Point((int) point.getX(), (int) point.getY()));
			}
			RegionSegment fixedPointList = new RegionSegment(points, fixedSegment.getId());
			fixedPointList.setType(TypeConverter.stringToPAGEType(fixedSegment.getType()));
			fixedPointLists.add(fixedPointList);
		}

		// Set existing cuts
		ArrayList<PointList> cuts = new ArrayList<>();
		for (Polygon cut : this.fixedGeometry.getCuts().values()) {
			ArrayList<java.awt.Point> points = new ArrayList<java.awt.Point>();
			for (Point point : cut.getPoints()) {
				points.add(new java.awt.Point((int) point.getX(), (int) point.getY()));
			}
			cuts.add(new PointList(points));
		}

		parameters.setExistingGeometry(new ExistingGeometry(fixedPointLists, cuts));

		return parameters;
	}

	public Map<String, RegionSettings> getRegions() {
		return new HashMap<String, RegionSettings>(regions);
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

	public Map<String, Integer> getRegionTypes() {
		return regionTypes;
	}

}
