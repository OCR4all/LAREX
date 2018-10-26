package larex.regionOperations;

import java.util.ArrayList;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import larex.contours.Contourextractor;
import larex.imageProcessing.ImageProcessor;
import larex.segmentation.result.RegionSegment;

public class Merge {

	public static RegionSegment merge(ArrayList<RegionSegment> toMerge, Size binarySize) {
		if (toMerge.size() < 2) {
			return null;
		}

		Mat temp = new Mat(binarySize, CvType.CV_8UC1, new Scalar(0));
		ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
		ArrayList<Point> cogs = new ArrayList<Point>();

		for (RegionSegment region : toMerge) {
			contours.add(region.getPoints());

			Point cog = ImageProcessor.calcCenterOfGravityOCV(region.getPoints(), true);
			cogs.add(cog);
		}

		Imgproc.drawContours(temp, contours, -1, new Scalar(255), -1);

		ArrayList<Point> remainingCogs = new ArrayList<Point>();
		ArrayList<Point> assignedCogs = new ArrayList<Point>();

		remainingCogs.addAll(cogs);
		assignedCogs.add(cogs.get(0));
		remainingCogs.remove(cogs.get(0));

		while (remainingCogs.size() > 0) {
			Point assignedCogTemp = null;
			Point remCogTemp = null;

			double minDist = Double.MAX_VALUE;

			for (Point assignedCog : assignedCogs) {
				for (Point remainingCog : remainingCogs) {
					double dist = Math.pow(remainingCog.x - assignedCog.x, 2)
							+ Math.pow(remainingCog.y - assignedCog.y, 2);

					if (dist < minDist) {
						assignedCogTemp = assignedCog;
						remCogTemp = remainingCog;
						minDist = dist;
					}
				}
			}

			Imgproc.line(temp, assignedCogTemp, remCogTemp, new Scalar(255), 2);
			assignedCogs.add(remCogTemp);
			remainingCogs.remove(remCogTemp);

		}

		contours = new ArrayList<>(Contourextractor.fromInverted(temp));

		temp.release();
		if (contours.size() > 0) {
			// TODO: Typvergabe!
			RegionSegment newResult = new RegionSegment(toMerge.get(0).getType(), contours.get(0));

			return newResult;
		}

		return null;
	}
}
