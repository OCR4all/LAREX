package larex.segmentation.result;

import larex.geometry.Polygon;

import java.awt.Color;
import java.util.ArrayList;
import java.util.UUID;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import larex.regions.RegionManager;
import larex.regions.type.RegionType;
import larex.segmentation.ImageProcessor;

@SuppressWarnings("rawtypes")
public class ResultRegion implements Comparable {

	private RegionType type;
	@Deprecated
	private Scalar color;
	@Deprecated
	private int imageHeight;
	private MatOfPoint points;

	private String id;
	
	private Rect rect;
	@Deprecated
	private MatOfPoint scaledPoints;
	private int readingOrderIndex;
	@Deprecated
	private boolean isAssigned;
	@Deprecated
	private boolean isActive;
	@Deprecated
	private Polygon scaledPolygon;
	@Deprecated
	private java.awt.Point scaledCenterOfGravity;

	@Deprecated
	private ArrayList<Point> containedPoints;

	public ResultRegion(RegionType type, int imageHeight, MatOfPoint points) {
		this(type,imageHeight,points,UUID.randomUUID().toString());
	}

	public ResultRegion(RegionType type, int imageHeight, MatOfPoint points, String id) {
		setType(type);
		Scalar color = RegionManager.getScalarByRegionType(type);
		setColor(color);
		setImageHeight(imageHeight);
		setPoints(points);
		setReadingOrderIndex(-1);
		this.id = id;
	}
	
	// TODO
	public void calcROBinary(Mat image) {
		Mat binary = new Mat(image.size(), CvType.CV_8UC1, new Scalar(0));
		Core.fillConvexPoly(binary, scaledPoints, new Scalar(255));

		Rect rect = Imgproc.boundingRect(scaledPoints);
		ArrayList<Point> containedPoints = new ArrayList<Point>();

		for (int y = rect.y; y < rect.y + rect.height; y++) {
			for (int x = rect.x; x < rect.x + rect.width; x++) {
				if (binary.get(y, x)[0] > 0) {
					containedPoints.add(new Point(x, y));
				}
			}
		}

		binary.release();
		setContainedPoints(containedPoints);
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

	@Deprecated
	public void applyScaleCorrection(Mat original, Mat resized) {
		double scaleFactor = (double) resized.height() / imageHeight;
		Point[] originalPoints = points.toArray();
		Point[] scaledPointsTemp = new Point[originalPoints.length];

		for (int i = 0; i < originalPoints.length; i++) {
			Point point = new Point(originalPoints[i].x * scaleFactor, originalPoints[i].y * scaleFactor);
			scaledPointsTemp[i] = point;
		}

		MatOfPoint scaledPoints = new MatOfPoint(scaledPointsTemp);
		ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
		contours.add(scaledPoints);

		Mat binaryMat = new Mat(resized.size(), CvType.CV_8U, new Scalar(0));
		Imgproc.drawContours(binaryMat, contours, -1, new Scalar(255), -1);
		Imgproc.findContours(binaryMat, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

		setScaledPoints(contours.get(0));
		setScaledPolygon(new Polygon(contours.get(0)));
		setScaledCenterOfGravity(ImageProcessor.calcCenterOfGravity(scaledPoints));
	}

	// TODO: -.-
	@Deprecated
	public void scalePointsSimpleReversed(double scaleFactor) {
		Point[] scaledPointsArray = scaledPoints.toArray();
		Point[] originalPointsTemp = new Point[scaledPointsArray.length];

		for (int i = 0; i < scaledPointsArray.length; i++) {
			Point point = new Point(scaledPointsArray[i].x / scaleFactor, scaledPointsArray[i].y / scaleFactor);
			originalPointsTemp[i] = point;
		}

		MatOfPoint originalPoints = new MatOfPoint(originalPointsTemp);
		setPoints(originalPoints);
	}

	@Deprecated
	public MatOfPoint scalePointsSimple(double scaleFactor) {
		Point[] originalPoints = points.toArray();
		Point[] scaledPointsTemp = new Point[originalPoints.length];

		for (int i = 0; i < originalPoints.length; i++) {
			Point point = new Point(originalPoints[i].x * scaleFactor, originalPoints[i].y * scaleFactor);
			scaledPointsTemp[i] = point;
		}

		MatOfPoint scaledPoints = new MatOfPoint(scaledPointsTemp);

		return scaledPoints;
	}

	public RegionType getType() {
		return type;
	}

	public void setType(RegionType type) {
		this.type = type;

		Color color = RegionManager.getColorByRegionType(type);
		if (color != null) {
			setColor(new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
		} else {
			System.out.println("Color for type " + type.toString() + " undefined. Please check Settings.");
			setColor(new Scalar(0, 0, 0));
		}
	}

	@Deprecated
	public Scalar getColor() {
		return color;
	}

	@Deprecated
	public void setColor(Scalar color) {
		this.color = color;
	}

	@Deprecated
	public int getImageHeight() {
		return imageHeight;
	}

	@Deprecated
	public void setImageHeight(int imageHeight) {
		this.imageHeight = imageHeight;
	}

	public MatOfPoint getPoints() {
		return points;
	}

	public void setPoints(MatOfPoint points) {
		setRect(Imgproc.boundingRect(points));
		this.points = points;
	}

	@Deprecated
	public MatOfPoint getScaledPoints() {
		return scaledPoints;
	}

	@Deprecated
	public void setScaledPoints(MatOfPoint scaledPoints) {
		this.scaledPoints = scaledPoints;
	}

	@Deprecated
	public Polygon getScaledPolygon() {
		return scaledPolygon;
	}

	@Deprecated
	public void setScaledPolygon(Polygon scaledPolygon) {
		this.scaledPolygon = scaledPolygon;
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
	
	@Deprecated
	public boolean isActive() {
		return isActive;
	}

	@Deprecated
	public void setActive(boolean isActive) {
		this.isActive = isActive;
	}

	@Deprecated
	public boolean isAssigned() {
		return isAssigned;
	}

	@Deprecated
	public void setAssigned(boolean isAssigned) {
		this.isAssigned = isAssigned;
	}

	@Deprecated
	public java.awt.Point getScaledCenterOfGravity() {
		return scaledCenterOfGravity;
	}

	@Deprecated
	public void setScaledCenterOfGravity(java.awt.Point scaledCenterOfGravity) {
		this.scaledCenterOfGravity = scaledCenterOfGravity;
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

	@Deprecated
	public ArrayList<Point> getContainedPoints() {
		return containedPoints;
	}

	@Deprecated
	public void setContainedPoints(ArrayList<Point> containedPoints) {
		this.containedPoints = containedPoints;
	}

	public ResultRegion clone(){
		ResultRegion clone = new ResultRegion(type,imageHeight,new MatOfPoint(points));
		clone.setActive(isActive);
		clone.setAssigned(isAssigned);
		clone.setColor(color);
		//clone.setContainedPoints(new ArrayList<Point>(containedPoints));
		clone.setReadingOrderIndex(readingOrderIndex);
		clone.setRect(rect);
		clone.setScaledCenterOfGravity(scaledCenterOfGravity);
		clone.setScaledPolygon(scaledPolygon);
		
		return clone;
	}
}