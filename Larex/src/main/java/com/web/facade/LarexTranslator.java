package com.web.facade;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.web.model.Book;
import com.web.model.BookSettings;
import com.web.model.Page;
import com.web.model.Point;
import com.web.model.Polygon;
import com.web.model.PageSegmentation;

import larex.geometry.PointList;
import larex.geometry.PointListManager;
import larex.positions.Position;
import larex.positions.PriorityPosition;
import larex.regions.Region;
import larex.regions.RegionManager;
import larex.regions.colors.RegionColor;
import larex.regions.type.RegionType;
import larex.segmentation.parameters.Parameters;
import larex.segmentation.result.ResultRegion;

import org.opencv.core.Size;

/**
 * Helper Class to translate Larex Objects to GUI Objects and vise versa
 *
 */
public class LarexTranslator {

	public static Parameters translateSettingsToParameters(BookSettings settings,Parameters oldParameters, Page page, Size pagesize) {
		// TODO Regions
		Parameters parameters = oldParameters;
		if(parameters == null){
			parameters = new Parameters(new RegionManager(),(int) pagesize.height);
		}
		
		parameters.setScaleFactor((double) parameters.getDesiredImageHeight() / pagesize.height);
		RegionManager regionmanager = parameters.getRegionManager();
		
		for(Region region: regionmanager.getRegions()){
			region.setPositions(new ArrayList<Position>());
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
			if(region == null){
				region = new Region(regionType, minSize, new RegionColor("green", Color.GREEN), maxOccurances,
						priorityPosition, new ArrayList<Position>());
				
				regionmanager.addRegion(region);
			}else{
				region.setMinSize(minSize);
				region.setMaxOccurances(maxOccurances);
			}
			
			for (Polygon polygon : guiRegion.getPolygons().values()) {
				List<Point> points = polygon.getPoints();
				if (points.size() == 4) {
					Point topLeft = points.get(0);
					Point bottomRight = points.get(2);
					Position position = new Position(topLeft.getX(), topLeft.getY(), bottomRight.getX(),
							bottomRight.getY());
					region.addPosition(position);

					//Set Ignore Region to fixed
					if(region.getType().equals(RegionType.ignore)){
						position.setFixed(true);
					}
				}
			}
			
		}
		return parameters;
	}

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

	public static Polygon translateResultRegionToSegment(ResultRegion region, String segmentid) {
		LinkedList<Point> points = new LinkedList<Point>();
		for (org.opencv.core.Point regionPoint : region.getPoints().toList()) {
			points.add(new Point(regionPoint.x, regionPoint.y));
		}

		Polygon segment = new Polygon(segmentid, region.getType(), points, false);
		return segment;
	}

	public static PageSegmentation translateResultRegionsToSegmentation(ArrayList<ResultRegion> regions, int pageid) {
		Map<String, Polygon> segments = new HashMap<String, Polygon>();

		int idcount = 0;
		for (ResultRegion region : regions) {
			Polygon segment = translateResultRegionToSegment(region, pageid + "s" + idcount);
			segments.put(segment.getId(), segment);
			idcount++;
		}
		return new PageSegmentation(pageid, segments);
	}
	
	public static PointListManager translateSettingsToPointListManager(BookSettings settings, int pageid){
		PointListManager manager = new PointListManager();
		
		ArrayList<PointList> fixedSegments = new ArrayList<PointList>();
		for(Polygon fixedSegment: settings.getPage(pageid).getFixedSegments().values()){
			ArrayList<java.awt.Point> points = new ArrayList<java.awt.Point>();
			for(Point point: fixedSegment.getPoints()){
				points.add(new java.awt.Point((int) point.getX(), (int) point.getY()));
			}
			PointList fixedPointList = new PointList(points);
			fixedPointList.setType(fixedSegment.getType());
			fixedPointList.setClosed(true);
			fixedSegments.add(fixedPointList);
			manager.setPointLists(fixedSegments);
		}

		for(Polygon cuts: settings.getPage(pageid).getCuts().values()){
			ArrayList<java.awt.Point> points = new ArrayList<java.awt.Point>();
			for(Point point: cuts.getPoints()){
				points.add(new java.awt.Point((int) point.getX(), (int) point.getY()));
			}
			manager.addPointList(points);
		}
		
		return manager;
	}
}