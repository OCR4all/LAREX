package larex.segmentation;


import larex.imageProcessing.Contour;

import java.util.ArrayList;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import larex.segmentation.parameters.ImageSegType;

public class ImageSegmentation {
	
	public static ArrayList<MatOfPoint> combineContours(ArrayList<MatOfPoint> contours, Mat image, ImageSegType type) {
		Mat binary = new Mat(image.size(), CvType.CV_8U, new Scalar(0));
		
		for (MatOfPoint contour : contours) {
			if(type.equals(ImageSegType.STRAIGHT_RECT)) {
				Rect rect = Imgproc.boundingRect(contour);
				Core.rectangle(binary, rect.tl(), rect.br(), new Scalar(255), -1);
			} else if(type.equals(ImageSegType.ROTATED_RECT)) {
				RotatedRect rect = Imgproc.minAreaRect(new MatOfPoint2f(contour.toArray()));
				Point[] points = new Point[4];
				rect.points(points);
				Core.fillConvexPoly(binary, new MatOfPoint(points), new Scalar(255));
			}
		}
		
		ArrayList<MatOfPoint> results = Contour.findContours(binary);
		
		return results;
	}

	public static ArrayList<MatOfPoint> combineContoursAsRects(ArrayList<MatOfPoint> contours, Mat image) {
		Mat binary = new Mat(image.size(), CvType.CV_8U, new Scalar(0));

		for (MatOfPoint contour : contours) {
			Rect rect = Imgproc.boundingRect(contour);
			Core.rectangle(binary, rect.tl(), rect.br(), new Scalar(255), -1);
		}

		ArrayList<MatOfPoint> results = Contour.findContours(binary);

		return results;
	}

	public static ArrayList<MatOfPoint> detectTextContours(Mat binary, int minSize) {
		ArrayList<MatOfPoint> contours = Contour.findContours(binary);
		ArrayList<MatOfPoint> results = new ArrayList<MatOfPoint>();

		for (MatOfPoint contour : contours) {
			Rect rect = Imgproc.boundingRect(contour);

			if (rect.area() > minSize) {
				results.add(contour);
			}
		}
		
		return results;
	}
	
	public static ArrayList<MatOfPoint> detectImageContours(Mat binary, int minSize, ImageSegType type, boolean combine) {
		ArrayList<MatOfPoint> contours = Contour.findContours(binary);
		ArrayList<MatOfPoint> results = new ArrayList<MatOfPoint>();

		for (MatOfPoint contour : contours) {
			if(type.equals(ImageSegType.STRAIGHT_RECT)) {
				Rect rect = Imgproc.boundingRect(contour);
				
				if (rect.area() > minSize) {
					Point[] points = new Point[4];
					points[0] = rect.tl();
					points[1] = new Point(rect.br().x, rect.y);
					points[2] = rect.br();
					points[3] = new Point(rect.x, rect.br().y);
					results.add(new MatOfPoint(points));
				}
			} else if(type.equals(ImageSegType.ROTATED_RECT)) {
				RotatedRect rect = Imgproc.minAreaRect(new MatOfPoint2f(contour.toArray()));
				
				if (rect.boundingRect().area() > minSize) {
					Point[] points = new Point[4];
					rect.points(points);
					results.add(new MatOfPoint(points));
				}
			} else if(type.equals(ImageSegType.CONTOUR_ONLY)) {
				if(Imgproc.contourArea(contour) > minSize) {
					results.add(contour);
				}
			}
		}

		if (combine) {
			if(type.equals(ImageSegType.STRAIGHT_RECT) || type.equals(ImageSegType.ROTATED_RECT)) {
				ArrayList<MatOfPoint> combinedResults = combineContours(results, binary, type);
				
				return combinedResults;
			}
		}
		
		return results;
	}
}