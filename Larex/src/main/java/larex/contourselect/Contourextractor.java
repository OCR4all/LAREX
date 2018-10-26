package larex.contourselect;

import java.util.ArrayList;
import java.util.Collection;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.imgproc.Imgproc;

/**
 * Contourextractor to get all contours in an image
 */
public class Contourextractor {
	/**
	 * Find all contours on a binary copy of the source image
	 * 
	 * @param source Source image to search contours in
	 * @return Collection of contours that are present in a binary copy of the
	 *         source image
	 */
	public static Collection<MatOfPoint> extract(Mat source) {
		Mat inverted = new Mat(source.size(), source.type());
		if (source.type() != CvType.CV_8UC1) {
			Mat tempInverted = new Mat(source.size(), source.type());
			Imgproc.cvtColor(source, tempInverted, Imgproc.COLOR_BGR2GRAY);
			Imgproc.threshold(tempInverted, inverted, 0, 255, Imgproc.THRESH_OTSU);
			tempInverted.release();
			Core.bitwise_not(inverted, inverted);
		} else {
			inverted = source.clone();
		}

		ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
		Imgproc.findContours(inverted, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

		inverted.release();
		return contours;
	}
}
