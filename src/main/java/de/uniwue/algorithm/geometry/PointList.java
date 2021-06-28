package de.uniwue.algorithm.geometry;

import java.util.ArrayList;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Size;

import de.uniwue.algorithm.data.MemoryCleaner;

public class PointList {
	private MatOfPoint points;

	public PointList(final MatOfPoint points) {
		this.points = points;
	}

	public PointList(ArrayList<java.awt.Point> points) {
		this(convertPoints(points));
	}

	public MatOfPoint getPoints() {
		return points;
	}

	/**
	 * Returns the given points with a scale correction.
	 *
	 * @param scaleFactor Prefered_Image_Height/Original_Image_Height
	 * @param origDimensions Original dimension of the image. Used to prevent overflow
	 * @return The converted and scaled points.
	 */
	public MatOfPoint getResizedPoints(double scaleFactor, Size origDimensions) {
		Point[] originalPoints = points.toArray();
		Point[] scaledPointsTemp = new Point[originalPoints.length];

		for (int i = 0; i < originalPoints.length; i++) {
			final double x = Math.min(originalPoints[i].x * scaleFactor, origDimensions.width-1);
			final double y = Math.min(originalPoints[i].y * scaleFactor, origDimensions.height-1);

			Point point = new Point(x, y);
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

	public void clean() {
		MemoryCleaner.clean(points);
	}
}
