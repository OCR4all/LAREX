package larex.geometry;

import java.awt.Point;
import java.util.ArrayList;

import org.opencv.core.MatOfPoint;

import larex.segmentation.result.ResultRegion;

public class PointList extends ResultRegion {

	/**
	 * Constructor for a PointList element.
	 * 
	 * @param points A list of points.
	 */
	public PointList(ArrayList<java.awt.Point> points, String id) {
		super(null, convertPoints(points), id);
	}

	private static MatOfPoint convertPoints(ArrayList<java.awt.Point> points) {
		org.opencv.core.Point[] ocvPoints = new org.opencv.core.Point[points.size()];

		for (int i = 0; i < points.size(); i++) {
			Point point = points.get(i);
			ocvPoints[i] = new org.opencv.core.Point(point.getX(), point.getY());
		}

		return new MatOfPoint(ocvPoints);
	}
}