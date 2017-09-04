package larex.segmentation.result;

import java.util.UUID;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import larex.regions.type.RegionType;

public class ResultRegion  {

	private RegionType type;
	private MatOfPoint points;

	private String id;
	
	private Rect rect;

	public ResultRegion(RegionType type, MatOfPoint points) {
		this(type,points,UUID.randomUUID().toString());
	}

	public ResultRegion(RegionType type, MatOfPoint points, String id) {
		setType(type);
		setPoints(points);
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
		setPoints(scaledPoints);
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

	public void setPoints(MatOfPoint points) {
		this.rect = Imgproc.boundingRect(points);
		this.points = points;
	}

	public String getId() {
		return id;
	}
	
	public void setId(String id) {
		this.id = id;
	}
	
	public Rect getRect() {
		return rect;
	}

	public ResultRegion clone(){
		ResultRegion clone = new ResultRegion(type,new MatOfPoint(points));
		
		return clone;
	}
}