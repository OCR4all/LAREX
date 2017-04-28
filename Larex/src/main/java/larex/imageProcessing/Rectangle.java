package larex.imageProcessing;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class Rectangle {
	
	
	
	public static Mat drawStraightRect(Mat original, Rect boundingRect, Scalar color, int thickness) {
		Mat result = original.clone();

		Core.rectangle(result, boundingRect.tl(), boundingRect.br(), color, thickness);

		return result;
	}

	public static Mat drawStraightRect(Mat original, MatOfPoint contour, Scalar color, int thickness) {
		Mat result = original.clone();

		Rect boundingRect = Imgproc.boundingRect(contour);
		Core.rectangle(result, boundingRect.tl(), boundingRect.br(), color, thickness);

		return result;
	}

	public static Mat drawStraightRect(Mat original, MatOfPoint contour, Scalar color, int thickness, int offSet) {
		Mat result = original.clone();

		Rect boundingRect = Imgproc.boundingRect(contour);

		boundingRect.x += offSet;
		boundingRect.y += offSet;
		boundingRect.width -= 2 * offSet;
		boundingRect.height -= 2 * offSet;

		Core.rectangle(result, boundingRect.tl(), boundingRect.br(), color, thickness);

		return result;
	}

	public static Mat drawRotatedRect(Mat original, MatOfPoint contour, Scalar color, int thickness) {
		RotatedRect rect = Imgproc.minAreaRect(new MatOfPoint2f(contour.toArray()));

		Mat result = original.clone();
		Point[] points = new Point[4];
		rect.points(points);

		for (int i = 1; i < points.length; i++) {
			Core.line(result, points[i - 1], points[i], color, thickness);
		}
		Core.line(result, points[points.length - 1], points[0], color, thickness);

		return result;
	}

	public static Mat drawRotatedRect(Mat original, RotatedRect rect, Scalar color, int thickness) {
		Mat result = original.clone();
		Point[] points = new Point[4];
		rect.points(points);

		for (int i = 1; i < points.length; i++) {
			Core.line(result, points[i - 1], points[i], new Scalar(255, 0, 0), thickness);
		}
		Core.line(result, points[points.length - 1], points[0], new Scalar(255, 0, 0), thickness);

		return result;
	}
}