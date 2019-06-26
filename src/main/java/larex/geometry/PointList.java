package larex.geometry;

import java.util.ArrayList;
import java.util.UUID;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;

import larex.data.MemoryCleaner;

public class PointList {
	private final String id;
	private MatOfPoint points;

	public PointList(final MatOfPoint points) {
		this(points, UUID.randomUUID().toString());
	}

	public PointList(final MatOfPoint points, String id) {
		this.points = points;
		this.id = id;
	}

	public PointList(ArrayList<java.awt.Point> points, String id) {
		this(convertPoints(points), id);
	}

	public MatOfPoint getPoints() {
		return points;
	}

	/**
	 * Returns the given points with a scale correction.
	 * 
	 * @param scaleFactor Prefered_Image_Height/Original_Image_Height
	 * @return The converted and scaled points.
	 */
	public MatOfPoint getResizedPoints(double scaleFactor) {
		Point[] originalPoints = points.toArray();
		Point[] scaledPointsTemp = new Point[originalPoints.length];

		for (int i = 0; i < originalPoints.length; i++) {
			Point point = new Point(originalPoints[i].x * scaleFactor, originalPoints[i].y * scaleFactor);
			scaledPointsTemp[i] = point;
		}

		return new MatOfPoint(scaledPointsTemp);
	}

	protected static MatOfPoint convertPoints(ArrayList<java.awt.Point> points) {
		org.opencv.core.Point[] ocvPoints = new org.opencv.core.Point[points.size()];

		for (int i = 0; i < points.size(); i++) {
			java.awt.Point point = points.get(i);
			ocvPoints[i] = new org.opencv.core.Point(point.getX(), point.getY());
		}

		return new MatOfPoint(ocvPoints);
	}

	public String getId() {
		return id;
	}
	
	public void clean() {
		MemoryCleaner.clean(points);
	}
}