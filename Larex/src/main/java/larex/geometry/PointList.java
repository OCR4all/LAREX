package larex.geometry;

import java.awt.Point;
import java.util.ArrayList;

import org.opencv.core.MatOfPoint;

import larex.regions.type.RegionType;

public class PointList {

	private final ArrayList<Point> points;
	private RegionType type;
	private MatOfPoint ocvPoints;
	private String id;

	/**
	 * Constructor for a PointList element.
	 * 
	 * @param points A list of points.
	 */
	public PointList(ArrayList<Point> points, String id) {
		this.points = points;
		this.id = id;
	}

	public ArrayList<Point> getPoints() {
		return points;
	}

	public RegionType getType() {
		return type;
	}

	public void setType(RegionType type) {
		this.type = type;
	}

	public MatOfPoint getOcvPoints() {
		// Calc if not exisiting (lazy eval)
		if (this.ocvPoints == null) {
			org.opencv.core.Point[] ocvPoints = new org.opencv.core.Point[points.size()];

			for (int i = 0; i < points.size(); i++) {
				Point point = points.get(i);
				ocvPoints[i] = new org.opencv.core.Point(point.getX(), point.getY());
			}

			MatOfPoint matOfPoints = new MatOfPoint(ocvPoints);
			setOcvPoints(matOfPoints);
		}
		return ocvPoints;
	}

	/**
	 * Returns the given points with a scale correction.
	 * 
	 * @param sourceHeight The actual height of the image.
	 * @param goalHeight   The desired height of the image.
	 * @return The converted and scaled points.
	 */
	public MatOfPoint getResizedMatOfPoint(int sourceHeight, int goalHeight) {
		org.opencv.core.Point[] ocvPoints = new org.opencv.core.Point[points.size()];
		double scaleFactor = (double) goalHeight / sourceHeight;

		for (int i = 0; i < points.size(); i++) {
			Point point = points.get(i);
			ocvPoints[i] = new org.opencv.core.Point(scaleFactor * point.getX(), scaleFactor * point.getY());
		}

		return new MatOfPoint(ocvPoints);
	}

	public void setOcvPoints(MatOfPoint ocvPoints) {
		this.ocvPoints = ocvPoints;
	}

	public String getId() {
		return id;
	}
}