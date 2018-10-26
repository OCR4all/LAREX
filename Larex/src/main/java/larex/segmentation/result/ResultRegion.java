package larex.segmentation.result;

import java.util.UUID;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;

import larex.regions.type.RegionType;

public class ResultRegion  {

	private final String id;
	private final RegionType type;
	private MatOfPoint points;

	public ResultRegion(RegionType type, MatOfPoint points) {
		this(type,points,UUID.randomUUID().toString());
	}

	public ResultRegion(RegionType type, MatOfPoint points, String id) {
		this.type = type;
		this.points = points;
		this.id = id;
	}

	public void rescale(double scaleFactor) {
		Point[] originalPoints = points.toArray();
		Point[] scaledPointsTemp = new Point[originalPoints.length];

		for (int i = 0; i < originalPoints.length; i++) {
			Point point = new Point(originalPoints[i].x / scaleFactor, originalPoints[i].y / scaleFactor);
			scaledPointsTemp[i] = point;
		}

		MatOfPoint scaledPoints = new MatOfPoint(scaledPointsTemp);
		this.points = scaledPoints;
	}

	public RegionType getType() {
		return type;
	}

	public MatOfPoint getPoints() {
		return points;
	}

	public String getId() {
		return id;
	}
}