package larex.segmentation.result;

import java.util.UUID;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;

import larex.regions.type.RegionType;

public class ResultRegion {

	private final String id;
	private RegionType type;
	private MatOfPoint points;

	public ResultRegion(RegionType type, MatOfPoint points) {
		this(type, points, UUID.randomUUID().toString());
	}

	public ResultRegion(RegionType type, MatOfPoint points, String id) {
		this.type = type;
		this.points = points;
		this.id = id;
	}

	/**
	 * Returns the a copy of this region with resized points
	 * 
	 * @param scaleFactor Prefered_Image_Height/Original_Image_Height
	 * @return The converted and scaled clone.
	 */
	public ResultRegion getResized(double scaleFactor) {
		return new ResultRegion(type, getResizedPoints(scaleFactor));
	}

	public RegionType getType() {
		return type;
	}

	public void setType(RegionType type) {
		this.type = type;
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

	public String getId() {
		return id;
	}
}