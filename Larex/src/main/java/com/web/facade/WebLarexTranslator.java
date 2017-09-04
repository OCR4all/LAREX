package com.web.facade;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Size;

import com.web.model.BookSettings;
import com.web.model.Point;
import com.web.model.Polygon;

import larex.geometry.PointList;
import larex.geometry.PointListManager;
import larex.positions.Position;
import larex.positions.PriorityPosition;
import larex.regions.Region;
import larex.regions.RegionManager;
import larex.regions.type.RegionType;
import larex.segmentation.parameters.Parameters;
import larex.segmentation.result.ResultRegion;

/**
 * Helper Class to translate Web Objects to Larex Objects
 *
 */
public class WebLarexTranslator {

	public static Parameters translateSettingsToParameters(BookSettings settings,Parameters oldParameters, Size pagesize) {
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
				region = new Region(regionType, minSize, maxOccurances,
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

	public static PointListManager translateSettingsToPointListManager(BookSettings settings, int pageid){
		PointListManager manager = new PointListManager();
		
		ArrayList<PointList> fixedSegments = new ArrayList<PointList>();
		for(Polygon fixedSegment: settings.getPage(pageid).getFixedSegments().values()){
			ArrayList<java.awt.Point> points = new ArrayList<java.awt.Point>();
			for(Point point: fixedSegment.getPoints()){
				points.add(new java.awt.Point((int) point.getX(), (int) point.getY()));
			}
			PointList fixedPointList = new PointList(points,fixedSegment.getId());
			fixedPointList.setType(fixedSegment.getType());
			fixedPointList.setClosed(true);
			fixedSegments.add(fixedPointList);
		}
		manager.setPointLists(fixedSegments);

		for(Polygon cuts: settings.getPage(pageid).getCuts().values()){
			ArrayList<java.awt.Point> points = new ArrayList<java.awt.Point>();
			for(Point point: cuts.getPoints()){
				points.add(new java.awt.Point((int) point.getX(), (int) point.getY()));
			}
			manager.addPointList(points, cuts.getId());
		}
		
		return manager;
	}
	
	public static ResultRegion translateSegmentToResultRegion(Polygon segment) {
		LinkedList<org.opencv.core.Point> points = new LinkedList<org.opencv.core.Point>();
		
		for(Point segmentPoint: segment.getPoints()){
			points.add(new org.opencv.core.Point(segmentPoint.getX(),segmentPoint.getY()));
		}
		
		MatOfPoint resultPoints = new MatOfPoint();
		resultPoints.fromList(points);
		
		ResultRegion result = new ResultRegion(segment.getType(),resultPoints);
		return result;
	}
}