package larex.segmentation.result;

import java.util.UUID;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import larex.regions.type.RegionType;

@SuppressWarnings("rawtypes")
public class ResultRegion implements Comparable {

	private RegionType type;
	@Deprecated
	private int imageHeight;
	private MatOfPoint points;

	private String id;
	
	private Rect rect;
	private int readingOrderIndex;

	public ResultRegion(RegionType type, int imageHeight, MatOfPoint points) {
		this(type,imageHeight,points,UUID.randomUUID().toString());
	}

	public ResultRegion(RegionType type, int imageHeight, MatOfPoint points, String id) {
		setType(type);
		setPoints(points);
		setReadingOrderIndex(-1);
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
		setRect(Imgproc.boundingRect(points));
		this.points = points;
	}

	public int getReadingOrderIndex() {
		return readingOrderIndex;
	}

	public void setReadingOrderIndex(int readingOrderIndex) {
		this.readingOrderIndex = readingOrderIndex;
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

	public void setRect(Rect rect) {
		this.rect = rect;
	}

	// TODO
	public int compareTo(Object o) {
		ResultRegion toCompare = (ResultRegion) o;

		if (toCompare.getRect().y < this.getRect().y) {
			return 1;
		} else {
			return -1;
		}
	}

	public ResultRegion clone(){
		ResultRegion clone = new ResultRegion(type,imageHeight,new MatOfPoint(points));
		//clone.setContainedPoints(new ArrayList<Point>(containedPoints));
		clone.setReadingOrderIndex(readingOrderIndex);
		clone.setRect(rect);
		
		return clone;
	}
}