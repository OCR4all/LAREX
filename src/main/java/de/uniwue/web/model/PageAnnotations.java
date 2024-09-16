package de.uniwue.web.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.uniwue.algorithm.geometry.regions.RegionSegment;
import de.uniwue.web.communication.SegmentationStatus;
import de.uniwue.web.config.Constants;

/**
 * Segmentation result of a specific page. Contains a pageNr and the resulting
 * segment polygons.
 */
public class PageAnnotations {

	static Logger logger = LoggerFactory.getLogger(PageAnnotations.class);

	/**
	 * Name of the page (does not include image extensions)
	 * Does not include sub extensions if imageSubFilter is active
	 */
	@JsonProperty("name")
	private final String name;
	@JsonProperty("xmlName")
	private final String xmlName;
	@JsonProperty("width")
	private final int width;
	@JsonProperty("height")
	private final int height;
	@JsonProperty("metadata")
	private final MetaData metadata;
	@JsonProperty("segments")
	private final Map<String, Region> segments;
	@JsonProperty("readingOrder")
	private final List<String> readingOrder;
	@JsonProperty("status")
	private final SegmentationStatus status;
	@JsonProperty("orientation")
	private final double orientation;
	@JsonProperty("isSegmented")
	private final boolean isSegmented;
	@JsonProperty("garbage")
	private final Map<String, Region> garbage;

	@JsonCreator
	public PageAnnotations(@JsonProperty("name") String name,
						   @JsonProperty("xmlName") String xmlName,
						   @JsonProperty("width") int width,
						   @JsonProperty("height") int height,
						   @JsonProperty("metadata") MetaData metadata,
						   @JsonProperty("segments") Map<String, Region> segments,
						   @JsonProperty("status") SegmentationStatus status,
						   @JsonProperty("readingOrder") List<String> readingOrder,
						   @JsonProperty("orientation") double orientation,
						   @JsonProperty("isSegmented") boolean isSegmented,
						   @JsonProperty("garbage") Map<String, Region> garbage) {
		this.name = name;
		this.xmlName = xmlName;
		this.width = width;
		this.height = height;
		this.metadata = metadata;
		this.segments = segments;
		this.status = status;
		this.readingOrder = readingOrder;
		this.orientation = orientation;
		this.isSegmented = isSegmented;
		this.garbage = garbage;
		checkNameValidity(name);
	}

	public PageAnnotations(String name,
						   String xmlName,
						   int width,
						   int height,
						   MetaData metadata,
						   Map<String, Region> segments,
						   SegmentationStatus status,
						   List<String> readingOrder,
						   double orientation,
						   boolean isSegmented)
	{
		this.name = name;
		this.xmlName = xmlName;
		this.width = width;
		this.height = height;
		this.metadata = metadata;
		this.segments = segments;
		this.status = status;
		this.orientation = orientation;
		this.readingOrder = readingOrder;
		this.isSegmented = isSegmented;
		this.garbage = new HashMap<String, Region>();
	}

	public PageAnnotations(String name,
						   String xmlName,
						   int width,
						   int height,
						   int pageNr,
						   MetaData metadata,
						   Collection<RegionSegment> regions,
						   SegmentationStatus status,
						   double orientation,
						   boolean isSegmented) {
		Map<String, Region> segments = new HashMap<String, Region>();

		for (RegionSegment region : regions) {
			Polygon regionCoords = new Polygon();
			for (org.opencv.core.Point regionPoint : region.getPoints().toList()) {
				regionCoords.addPoint(new Point(regionPoint.x, regionPoint.y));
			}

			Region segment = new Region(region.getId(), regionCoords, region.getType().toString());
			segments.put(segment.getId(), segment);
		}
		this.name = name;
		this.xmlName = xmlName;
		this.width = width;
		this.height = height;
		this.orientation = orientation;
		this.metadata = metadata;
		this.segments = segments;
		this.status = status;
		this.readingOrder = new ArrayList<String>();
		this.isSegmented = isSegmented;
		this.garbage = new HashMap<String, Region>();
		checkNameValidity(name);
	}

	public PageAnnotations(String name, String xmlName, int width, int height, int pageNr, double orientation, boolean isSegmented) {
		this(name, xmlName, width, height, pageNr, new MetaData(), new ArrayList<RegionSegment>(), SegmentationStatus.EMPTY, orientation, isSegmented);
	}

	public Map<String, Region> getSegments() {
		return new HashMap<String, Region>(segments);
	}

	public MetaData getMetadata() {
		return metadata;
	}

	public SegmentationStatus getStatus() {
		return status;
	}

	public List<String> getReadingOrder() {
		return new ArrayList<String>(readingOrder);
	}

	public Map<String, Region> getGarbage() {
		return garbage;
	}

	public boolean isSegmented() {
		return isSegmented;
	}

	public String getName() {
		return name;
	}

	public String getXmlName() {
		return xmlName;
	}

	public int getHeight() {
		return height;
	}

	public int getWidth() {
		return width;
	}
	public double getOrientation() {
		return orientation;
	}
	private static void checkNameValidity(String name) {
		final List<String> imageExtensions = Constants.IMG_EXTENSIONS_DOTTED;
		for (String ext : imageExtensions) {
			if(name.toLowerCase().endsWith(ext))
				logger.error("Page {} ends with image extension ({}). This should not happen unless it's part of page name, i.e. {}.png", 
				name, ext, name);
		}

	}
}
