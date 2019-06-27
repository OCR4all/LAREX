package larex.imageProcessing;

import java.util.ArrayList;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import larex.data.MemoryCleaner;

public class ImageProcessor {

	public static Mat dilate(final Mat binary, Size kernelSize) {
		final Mat result = new Mat();
		final Mat kernel = Mat.ones(kernelSize, CvType.CV_8U);
		Imgproc.dilate(binary, result, kernel);
		MemoryCleaner.clean(kernel);
		return result;
	}

	public static Mat invertImage(final Mat binary) {
		final Mat inverted = new Mat(binary.size(), binary.type(), new Scalar(255));
		Core.subtract(inverted, binary, inverted);

		return inverted;
	}

	public static Mat calcGray(final Mat source) {
		final Mat gray = new Mat();
		Imgproc.cvtColor(source, gray, Imgproc.COLOR_BGR2GRAY);

		return gray;
	}

	public static Mat calcBinary(final Mat gray) {
		final Mat binary = new Mat();
		Imgproc.threshold(gray, binary, 0, 255, Imgproc.THRESH_OTSU);

		return binary;
	}

	public static Mat calcInvertedBinary(final Mat gray) {
		final Mat binary = new Mat();
		Imgproc.threshold(gray, binary, 0, 255, Imgproc.THRESH_OTSU);
		final Mat inverted = invertImage(binary);
		MemoryCleaner.clean(binary);
		return inverted;
	}

	public static Mat resize(final Mat source, int desiredHeight) {
		if (desiredHeight == -1) {
			return source;
		}

		final Mat result = new Mat();

		double scaleFactor = (double) source.rows() / desiredHeight;
		Imgproc.resize(source, result, new Size(source.cols() / scaleFactor, desiredHeight));

		return result;
	}

	public static Point calcCenterOfGravity(final MatOfPoint input) {
		Point[] points = input.toArray();

		double sumX = 0;
		double sumY = 0;

		for (int i = 0; i < points.length; i++) {
			sumX += points[i].x;
			sumY += points[i].y;
		}

		double avgX = sumX / points.length;
		double avgY = sumY / points.length;

		return new Point(avgX, avgY);
	}

	public static Point calcCenterOfGravityOCV(final MatOfPoint input,
			boolean forceCogInContour) {
		Point[] points = input.toArray();

		double sumX = 0;
		double sumY = 0;

		for (int i = 0; i < points.length; i++) {
			sumX += points[i].x;
			sumY += points[i].y;
		}

		double avgX = sumX / points.length;
		double avgY = sumY / points.length;

		Point cog = new Point(Math.round(avgX), Math.round(avgY));

		if (forceCogInContour) {
			if (Imgproc.pointPolygonTest(new MatOfPoint2f(input.toArray()),
					cog, false) < 0) {
				ArrayList<Point> candidates = new ArrayList<Point>();

				for (Point point : points) {
					if (point.x == cog.x || point.y == cog.y) {
						candidates.add(point);
					}
				}

				double minDist = Double.MAX_VALUE;

				for (Point candidate : candidates) {
					double dist = Math.pow(cog.x - candidate.x, 2)
							+ Math.pow(cog.y - candidate.y, 2);

					if (dist < minDist) {
						minDist = dist;
						cog = candidate;
					}
				}
			}
		}

		return cog;
	}
}