package larex.geometry;

import java.util.ArrayList;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class PointListManager {

	private ArrayList<PointList> pointLists;

	public PointListManager() {
		setPointLists(new ArrayList<PointList>());
	}

	public Mat drawLinesIntoImage(ArrayList<Point> points, Mat image) {
		if (points != null && points.size() > 1) {
			Point lastPoint = points.get(0);

			for (int i = 1; i < points.size(); i++) {
				Point currentPoint = points.get(i);
				Imgproc.line(image, lastPoint, currentPoint, new Scalar(0), 2);
				lastPoint = currentPoint;
			}
		}

		return image;
	}

	public Mat drawPointListIntoImage(Mat image, double scaleFactor) {
		Mat result = image.clone();
		ArrayList<ArrayList<Point>> ocvPointLists = getResized(scaleFactor);

		for (ArrayList<Point> ocvPoints : ocvPointLists) {
			result = drawLinesIntoImage(ocvPoints, result);
		}

		return result;
	}

	private ArrayList<ArrayList<Point>> getResized(double scaleFactor) {
		ArrayList<ArrayList<Point>> allPoints = new ArrayList<ArrayList<Point>>();

		for (PointList pointList : pointLists)
			allPoints.add(new ArrayList<>(pointList.getResizedPoints(scaleFactor).toList()));

		return allPoints;
	}

	public ArrayList<PointList> getPointLists() {
		return pointLists;
	}

	public void setPointLists(ArrayList<PointList> pointLists) {
		this.pointLists = pointLists;
	}
}