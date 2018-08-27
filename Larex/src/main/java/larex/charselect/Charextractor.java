package larex.charselect;

import java.util.ArrayList;
import java.util.Collection;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.imgproc.Imgproc;

public class Charextractor {
	public static Collection<MatOfPoint> extract(Mat source) { 
		Mat tempInverted = new Mat(source.size(), source.type());
		Mat inverted = new Mat(source.size(), source.type());
		Imgproc.cvtColor(source, tempInverted, Imgproc.COLOR_BGR2GRAY);
		Imgproc.threshold(tempInverted, inverted, 0, 255, Imgproc.THRESH_OTSU);
		Core.bitwise_not(inverted, inverted);
		tempInverted.release();
		
		ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
		Imgproc.findContours(inverted, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_TC89_L1);

		inverted.release();
		return contours;
	}
}
