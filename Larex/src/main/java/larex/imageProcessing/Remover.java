package larex.imageProcessing;

import java.util.ArrayList;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class Remover {

	public static Mat deletePointsWithinContour(Mat binary, MatOfPoint contour) {
		Mat result = binary.clone();

		ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
		contours.add(contour);
		Core.fillPoly(result, contours, new Scalar(255));

		return result;
	}

	public static Mat deletePointsWithinRect(Mat binary, MatOfPoint contour) {
		Rect boundingRect = Imgproc.boundingRect(contour);
		Point[] points = { boundingRect.tl(), new Point(boundingRect.tl().x + boundingRect.width, boundingRect.tl().y),
				boundingRect.br(), new Point(boundingRect.br().x - boundingRect.width, boundingRect.br().y) };

		Mat result = deletePointsWithinContour(binary, new MatOfPoint(points));

		return result;
	}

	public static Mat deletePointsWithinRotatedRect(Mat binary, MatOfPoint contour) {
		RotatedRect minAreaRect = Imgproc.minAreaRect(new MatOfPoint2f(contour.toArray()));

		Point[] points = new Point[4];
		minAreaRect.points(points);

		Mat result = deletePointsWithinContour(binary, new MatOfPoint(points));

		return result;
	}
}