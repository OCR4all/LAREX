package com.web.facade;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Size;

import com.web.model.BookSettings;
import com.web.model.PageSegmentation;
import com.web.model.Point;
import com.web.model.Polygon;

import larex.geometry.ExistingGeometry;
import larex.geometry.PointList;
import larex.geometry.positions.PriorityPosition;
import larex.geometry.positions.RelativePosition;
import larex.geometry.regions.Region;
import larex.geometry.regions.RegionManager;
import larex.geometry.regions.RegionSegment;
import larex.geometry.regions.type.RegionType;
import larex.segmentation.SegmentationResult;
import larex.segmentation.parameters.Parameters;

/**
 * Helper Class to translate Web Objects to Larex Objects
 *
 */
public class WebLarexTranslator {

	public static Parameters translateSettings(BookSettings settings, Size pagesize) {
		// Set Parameters
		Parameters parameters = new Parameters(new RegionManager(), (int) pagesize.height);

		RegionManager regionmanager = parameters.getRegionManager();

		for (Region region : regionmanager.getRegions()) {
			region.setPositions(new ArrayList<RelativePosition>());
		}

		Map<String, Integer> settingParameters = settings.getParameters();
		parameters.setBinaryThresh(settingParameters.get("binarythreash"));
		parameters.setTextDilationX(settingParameters.get("textdilationX"));
		parameters.setTextDilationY(settingParameters.get("textdilationY"));
		parameters.setImageRemovalDilationX(settingParameters.get("imagedilationX"));
		parameters.setImageRemovalDilationY(settingParameters.get("imagedilationY"));
		parameters.setImageSegType(settings.getImageSegType());
		parameters.setCombineImages(settings.isCombine());

		for (com.web.model.Region guiRegion : settings.getRegions().values()) {
			RegionType regionType = guiRegion.getType();
			int minSize = guiRegion.getMinSize();
			int maxOccurances = guiRegion.getMaxOccurances();
			PriorityPosition priorityPosition = guiRegion.getPriorityPosition();

			Region region = regionmanager.getRegionByType(regionType);
			if (region == null) {
				region = new Region(regionType, minSize, maxOccurances, priorityPosition, new ArrayList<RelativePosition>());
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

		return parameters;
	}

	public static Parameters translateSettings(BookSettings settings, Size pagesize, int pageID) {
		// Set Parameters
		Parameters parameters = translateSettings(settings, pagesize);

		// Set existing Geometry
		ArrayList<RegionSegment> fixedPointLists = new ArrayList<>();
		for (Polygon fixedSegment : settings.getPage(pageID).getFixedSegments().values()) {
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
		for (Polygon cut : settings.getPage(pageID).getCuts().values()) {
			ArrayList<java.awt.Point> points = new ArrayList<java.awt.Point>();
			for (Point point : cut.getPoints()) {
				points.add(new java.awt.Point((int) point.getX(), (int) point.getY()));
			}
			cuts.add(new PointList(points, cut.getId()));
		}

		parameters.setExistingGeometry(new ExistingGeometry(fixedPointLists, cuts));

		return parameters;
	}

	public static SegmentationResult translateResult(PageSegmentation segmentation) {
		ArrayList<RegionSegment> regions = new ArrayList<RegionSegment>();

		for (String poly : segmentation.getSegments().keySet()) {
			Polygon polygon = segmentation.getSegments().get(poly);
			regions.add(translateSegment(polygon));
		}
		SegmentationResult result = new SegmentationResult(regions);

		// Reading Order
		ArrayList<RegionSegment> readingOrder = new ArrayList<RegionSegment>();
		List<String> readingOrderStrings = segmentation.getReadingOrder();
		for (String regionID : readingOrderStrings) {
			RegionSegment region = result.getRegionByID(regionID);
			if (region != null) {
				readingOrder.add(region);
			}
		}
		result.setReadingOrder(readingOrder);
		return result;
	}

	public static RegionSegment translateSegment(Polygon segment) {
		LinkedList<org.opencv.core.Point> points = new LinkedList<org.opencv.core.Point>();

		for (Point segmentPoint : segment.getPoints()) {
			points.add(new org.opencv.core.Point(segmentPoint.getX(), segmentPoint.getY()));
		}

		MatOfPoint resultPoints = new MatOfPoint();
		resultPoints.fromList(points);

		RegionSegment result = new RegionSegment(segment.getType(), resultPoints, segment.getId());
		return result;
	}

	public static MatOfPoint translateContour(List<Point> points) {

		org.opencv.core.Point[] matPoints = new org.opencv.core.Point[points.size()];

		for (int index = 0; index < points.size(); index++) {
			Point point = points.get(index);
			matPoints[index] = new org.opencv.core.Point(point.getX(), point.getY());
		}
		return new MatOfPoint(matPoints);
	}
}