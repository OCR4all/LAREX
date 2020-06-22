package de.uniwue.algorithm.segmentation;


import java.util.ArrayList;
import java.util.Collection;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import de.uniwue.algorithm.data.MemoryCleaner;
import de.uniwue.algorithm.operators.Contourextractor;
import de.uniwue.algorithm.segmentation.parameters.ImageSegType;

public class ImageSegmentation {
	
	public static Collection<MatOfPoint> combineContours(Collection<MatOfPoint> contours, final Mat image, ImageSegType type) {
		final Mat binary = new Mat(image.size(), CvType.CV_8U, new Scalar(0));
		
		for (final MatOfPoint contour : contours) {
			if(type.equals(ImageSegType.STRAIGHT_RECT)) {
				Rect rect = Imgproc.boundingRect(contour);
				Imgproc.rectangle(binary, rect.tl(), rect.br(), new Scalar(255), -1);
			} else if(type.equals(ImageSegType.ROTATED_RECT)) {
				RotatedRect rect = Imgproc.minAreaRect(new MatOfPoint2f(contour.toArray()));
				Point[] points = new Point[4];
				rect.points(points);
				Imgproc.fillConvexPoly(binary, new MatOfPoint(points), new Scalar(255));
			}
		}
		
		ArrayList<MatOfPoint> results = new ArrayList<>(Contourextractor.fromInverted(binary));
		MemoryCleaner.clean(binary);
		
		return results;
	}

	public static Collection<MatOfPoint> detectTextContours(final Mat binary, int minSize) {
		Collection<MatOfPoint> contours = Contourextractor.fromInverted(binary);
		ArrayList<MatOfPoint> results = new ArrayList<MatOfPoint>();

		for (final MatOfPoint contour : contours) {
			Rect rect = Imgproc.boundingRect(contour);

			if (rect.area() > minSize) {
				results.add(contour);
			} else {
				MemoryCleaner.clean(contour);
			}
		}
		
		return results;
	}
	
	public static Collection<MatOfPoint> detectImageContours(final Mat binary, int minSize, ImageSegType type, boolean combine) {
		Collection<MatOfPoint> contours = Contourextractor.fromInverted(binary);
		ArrayList<MatOfPoint> results = new ArrayList<MatOfPoint>();

		for (final MatOfPoint contour : contours) {
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

				return combineContours(results, binary, type);
			}
		}
		
		return results;
	}
}