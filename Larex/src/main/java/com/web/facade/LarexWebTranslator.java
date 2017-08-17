package com.web.facade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import com.web.model.Book;
import com.web.model.BookSettings;
import com.web.model.Point;
import com.web.model.Polygon;
import com.web.model.PageSegmentation;

import larex.positions.Position;
import larex.positions.PriorityPosition;
import larex.regions.Region;
import larex.regions.RegionManager;
import larex.regions.type.RegionType;
import larex.segmentation.parameters.Parameters;
import larex.segmentation.result.ResultRegion;

/**
 * Helper Class to translate Larex Objects to Web Objects
 *
 */
public class LarexWebTranslator {

	public static BookSettings translateParametersToSettings(Parameters parameters, Book book) {
		// TODO Regions
		BookSettings settings = new BookSettings(book);

		Map<String, Integer> settingParameters = settings.getParameters();
		settingParameters.put("binarythreash", parameters.getBinaryThresh());
		settingParameters.put("textdilationX", parameters.getTextDilationX());
		settingParameters.put("textdilationY", parameters.getTextDilationY());
		settingParameters.put("imagedilationX", parameters.getImageRemovalDilationX());
		settingParameters.put("imagedilationY", parameters.getImageRemovalDilationY());

		settings.setCombine(parameters.isCombineImages());
		settings.setImageSegType(parameters.getImageSegType());
		
		RegionManager regionManager = parameters.getRegionManager();
		for (Region region : regionManager.getRegions()) {
			/*List<Polygon> regions = translateRegionToGUIRegions(region);
			for (Polygon segment : regions) {
				settings.addRegion(segment);
			}*/
			
			RegionType regionType = region.getType();
			int minSize = region.getMinSize();
			int maxOccurances = region.getMaxOccurances();
			PriorityPosition priorityPosition = region.getPriorityPosition();
			com.web.model.Region guiRegion = new com.web.model.Region(regionType,minSize,maxOccurances,priorityPosition);

			int regionCount = 0;
			for (Position position : region.getPositions()) {
				LinkedList<Point> points = new LinkedList<Point>();
				points.add(new Point(position.getTopLeftXPercentage(), position.getTopLeftYPercentage()));
				points.add(new Point(position.getBottomRightXPercentage(), position.getTopLeftYPercentage()));
				points.add(new Point(position.getBottomRightXPercentage(), position.getBottomRightYPercentage()));
				points.add(new Point(position.getTopLeftXPercentage(), position.getBottomRightYPercentage()));

				String id = regionType.toString() + regionCount;
				guiRegion.addPolygon(new Polygon(id, regionType, points, true));
				regionCount++;
			}
			
			//TODO ? PointList -> Cut
			
			settings.addRegion(guiRegion);
		}
		return settings;
	}

	public static Polygon translateResultRegionToSegment(ResultRegion region) {
		LinkedList<Point> points = new LinkedList<Point>();
		for (org.opencv.core.Point regionPoint : region.getPoints().toList()) {
			points.add(new Point(regionPoint.x, regionPoint.y));
		}

		Polygon segment = new Polygon(region.getId(), region.getType(), points, false);
		return segment;
	}

	public static PageSegmentation translateResultRegionsToSegmentation(ArrayList<ResultRegion> regions, int pageid) {
		Map<String, Polygon> segments = new HashMap<String, Polygon>();

		for (ResultRegion region : regions) {
			Polygon segment = translateResultRegionToSegment(region);
			segments.put(segment.getId(), segment);
		}
		return new PageSegmentation(pageid, segments);
	}
}