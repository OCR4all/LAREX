package larex.geometry;

import java.awt.Point;
import java.util.ArrayList;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;

import larex.regions.type.RegionType;
import larex.segmentation.ImageProcessor;

public class PointList {

	private static final int OPTIMIZING_DISTANCE = 3;

	private ArrayList<Point> points;
	private boolean isClosed;
	private boolean isOptimized;
	private RegionType type;
	private MatOfPoint ocvPoints;
	private Polygon polygon;

	/**
	 * Constructor for a PointList element.
	 * 
	 * @param points A list of points.
	 */
	public PointList(ArrayList<Point> points) {
		setPoints(points);
		setPolygon(new Polygon(points));
	}

	/**
	 * Converts the given points to OpenCV format and applies a scale correction.
	 * 
	 * @param sourceHeight The actual height of the image.
	 * @param goalHeight The desired height of the image.
	 * @return The converted and scaled points.
	 */
	public MatOfPoint calcMatOfPoint(int sourceHeight, int goalHeight) {
		System.out.print(sourceHeight + " | "+goalHeight);
		org.opencv.core.Point[] ocvPoints = new org.opencv.core.Point[points.size()];
		double scaleFactor = (double) goalHeight / sourceHeight;

		for (int i = 0; i < points.size(); i++) {
			Point point = points.get(i);
			ocvPoints[i] = new org.opencv.core.Point(scaleFactor * point.getX(), scaleFactor * point.getY());
		}

		MatOfPoint matOfPoints = new MatOfPoint(ocvPoints);
		setOcvPoints(matOfPoints);

		return matOfPoints;
	}

//	
//	/**
//	 * Checks if a line is vertical.
//	 * 
//	 * @param start
//	 * @param end
//	 * @return True if line is vertical, false otherwise.
//	 */
//	public boolean lineIsVertical(Point start, Point end) {
//		int deltaX = Math.abs(start.x - end.x);
//		int deltaY = Math.abs(start.y - end.y);
//
//		if (deltaY > deltaX) {
//			return true;
//		} else {
//			return false;
//		}
//	}
//
//	/**
//	 * Counts the number of pixels on the line.
//	 * 
//	 * @param start
//	 * @param end
//	 * @param binary A binary image: white pixels represent points on the line.
//	 * @return
//	 */
//	public int calcPixelsOnLine(Point start, Point end, Mat binary) {
//		ArrayList<Point> points = Bresenham.calcPointsOnLine(start, end);
//		int cnt = 0;
//
//		for (Point point : points) {
//			if (binary.get(point.y, point.x)[0] == 0) {
//				cnt++;
//			}
//		}
//
//		return cnt;
//	}
//
//	/**
//	 * @param start
//	 * @param end
//	 * @param binary
//	 */
//	public void optimizeHorizontal(Point start, Point end, Mat binary) {
//		int min = calcPixelsOnLine(new Point(start.x, start.y), new Point(end.x, end.y), binary);
//		int y1 = start.y;
//		int y2 = end.y;
//
//		for (int y_start = start.y - OPTIMIZING_DISTANCE; y_start <= start.y + OPTIMIZING_DISTANCE; y_start++) {
//			for (int y_end = end.y - OPTIMIZING_DISTANCE; y_end <= end.y + OPTIMIZING_DISTANCE; y_end++) {
//				if (!(y_start == start.y && y_end == end.y)) {
//					if (y_start >= 0 && y_start < binary.height() && y_end >= 0 && y_end < binary.height()) {
//						int cut = calcPixelsOnLine(new Point(start.x, y_start), new Point(end.x, y_end), binary);
//						if (cut < min) {
//							min = cut;
//							y1 = y_start;
//							y2 = y_end;
//						}
//					}
//				}
//			}
//		}
//
//		start.y = y1;
//		end.y = y2;
//	}
//
//	public void optimizeVertical(Point start, Point end, Mat binary) {
//		int min = calcPixelsOnLine(new Point(start.x, start.y), new Point(end.x, end.y), binary);
//		int x1 = start.x;
//		int x2 = end.x;
//
//		for (int x_start = start.x - OPTIMIZING_DISTANCE; x_start <= start.x + OPTIMIZING_DISTANCE; x_start++) {
//			for (int x_end = end.x - OPTIMIZING_DISTANCE; x_end <= end.x + OPTIMIZING_DISTANCE; x_end++) {
//				if (!(x_start == start.x && x_end == end.x)) {
//					if (x_start >= 0 && x_start < binary.width() && x_end >= 0 && x_end < binary.width()) {
//						int cut = calcPixelsOnLine(new Point(x_start, start.y), new Point(x_end, end.y), binary);
//
//						if (cut < min) {
//							min = cut;
//							x1 = x_start;
//							x2 = x_end;
//						}
//					}
//				}
//			}
//		}
//
//		start.x = x1;
//		end.x = x2;
//	}
//
//	public void optimize(Mat image) {
//		if (points.size() != 2) {
//			return;
//		}
//
//		if (image.channels() > 1) {
//			image = ImageProcessor.calcGray(image);
//		}
//
//		Mat binary = ImageProcessor.calcBinary(image);
//
//		Point start = points.get(0);
//		Point end = points.get(1);
//		boolean isVertical = lineIsVertical(start, end);
//
//		if (isVertical) {
//			optimizeVertical(start, end, binary);
//		} else {
//			optimizeHorizontal(start, end, binary);
//		}
//	}

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
		return ocvPoints;
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
}