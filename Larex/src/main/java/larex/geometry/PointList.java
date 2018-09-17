package larex.geometry;

import java.awt.Point;
import java.util.ArrayList;

import org.opencv.core.MatOfPoint;

import larex.regions.type.RegionType;

public class PointList {

	private ArrayList<Point> points;
	private boolean isClosed;
	private boolean isOptimized;
	private RegionType type;
	private MatOfPoint ocvPoints;
	private Polygon polygon;
	private String id;

	/**
	 * Constructor for a PointList element.
	 * 
	 * @param points A list of points.
	 */
	public PointList(ArrayList<Point> points, String id) {
		setPoints(points);
		setPolygon(new Polygon(points));
		this.id = id;
	}


	public ArrayList<Point> getPoints() {
		return points;
	}

	public void setPoints(ArrayList<Point> points) {
		this.points = points;
	}

	public boolean isClosed() {
		return isClosed;
	}

	public void setClosed(boolean isClosed) {
		this.isClosed = isClosed;
	}

	public boolean isOptimized() {
		return isOptimized;
	}

	public void setOptimized(boolean isOptimized) {
		this.isOptimized = isOptimized;
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

	public Polygon getPolygon() {
		return polygon;
	}

	public void setPolygon(Polygon polygon) {
		this.polygon = polygon;
	}

	public String getId() {
		return id;
	}
}