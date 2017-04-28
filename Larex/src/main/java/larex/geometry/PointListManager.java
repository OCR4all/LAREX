package larex.geometry;

import java.awt.Point;
import java.util.ArrayList;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

public class PointListManager {

	private ArrayList<PointList> pointLists;
	private int verticalResolution;

	public PointListManager() {
		setPointLists(new ArrayList<PointList>());
	}

	public Mat drawLinesIntoImage(ArrayList<org.opencv.core.Point> points, Mat image) {
		if (points != null && points.size() > 1) {
			org.opencv.core.Point lastPoint = points.get(0);

			for (int i = 1; i < points.size(); i++) {
				org.opencv.core.Point currentPoint = points.get(i);
				Core.line(image, lastPoint, currentPoint, new Scalar(0), 2);
				lastPoint = currentPoint;
			}
		}

		return image;
	}

	public Mat drawPointListIntoImage(Mat image, double scaleFactor) {
		Mat result = image.clone();
		ArrayList<ArrayList<org.opencv.core.Point>> ocvPointLists = convertToOpenCV(scaleFactor);

		for (ArrayList<org.opencv.core.Point> ocvPoints : ocvPointLists) {
			result = drawLinesIntoImage(ocvPoints, result);
		}

		return result;
	}

	public ArrayList<ArrayList<org.opencv.core.Point>> convertToOpenCV(double scaleFactor) {
		ArrayList<ArrayList<org.opencv.core.Point>> allPoints = new ArrayList<ArrayList<org.opencv.core.Point>>();

		for (PointList pointList : pointLists) {
			ArrayList<org.opencv.core.Point> points = new ArrayList<org.opencv.core.Point>();

			for (int i = 0; i < pointList.getPoints().size(); i++) {
				Point toConvert = pointList.getPoints().get(i);

				org.opencv.core.Point point = new org.opencv.core.Point(scaleFactor * toConvert.getX(),
						scaleFactor * toConvert.getY());
				points.add(point);
			}

			allPoints.add(points);
		}

		return allPoints;
	}

	public boolean removePointListByPoint(Point point, int radius) {
		PointList toRemove = identifyPointList(point, radius);

		if (toRemove != null) {
			pointLists.remove(toRemove);
			return true;
		} else {
			return false;
		}
	}

	public boolean removePointListByArea(Point point) {
		PointList toRemove = identifyPointListByArea(point);

		if (toRemove != null) {
			pointLists.remove(toRemove);
			return true;
		} else {
			return false;
		}
	}

	public PointList identifyPointListByArea(Point toIdentify) {
		for (PointList list : pointLists) {
			if (list.isClosed()) {
				if (list.getPolygon().getPolyAwt().contains(toIdentify)) {
					return list;
				}
			}
		}

		return null;
	}

	public PointList identifyPointList(Point toIdentify, int radius) {
		int r_squared = radius * radius;

		for (PointList list : pointLists) {
			for (Point point : list.getPoints()) {
				if (calcDistSquared(point, toIdentify) < r_squared) {
					return list;
				}
			}
		}

		return null;
	}

	public Point identifyPoint(Point toIdentify, PointList pointList, int radius) {
		int r_squared = radius * radius;

		for (Point point : pointList.getPoints()) {
			if (calcDistSquared(point, toIdentify) < r_squared) {
				return point;
			}
		}

		return null;
	}

	public int calcDistSquared(Point point1, Point point2) {
		int dx = point1.x - point2.x;
		int dy = point1.y - point2.y;

		int distSquared = dx * dx + dy * dy;

		return distSquared;
	}

	public void addPointList(ArrayList<Point> points) {
		pointLists.add(new PointList(points));
	}

	public ArrayList<PointList> getPointLists() {
		return pointLists;
	}

	public void setPointLists(ArrayList<PointList> pointLists) {
		this.pointLists = pointLists;
	}

	public int getVerticalResolution() {
		return verticalResolution;
	}

	public void setVerticalResolution(int verticalResolution) {
		this.verticalResolution = verticalResolution;
	}
}