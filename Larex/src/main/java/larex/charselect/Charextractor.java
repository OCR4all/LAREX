package larex.charselect;

import java.util.ArrayList;
import java.util.Collection;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.imgproc.Imgproc;

public class Charextractor {
	public static Collection<MatOfPoint> extract(Mat invertedBinary) {
		Mat invertedClone = invertedBinary.clone();
		ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
		Imgproc.findContours(invertedClone, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

		invertedClone.release();
		return contours;
	}
}
