package larex.imageProcessing;

import java.util.ArrayList;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class Contour {
	
	public static Mat drawContours(Mat original, ArrayList<MatOfPoint> contours, Scalar color, int thickness) {
		Mat result = original.clone();

		if(color != null) {
			Imgproc.drawContours(result, contours, -1, color, thickness);
		} else {
			System.out.println("Color undefined. Please check Settings.");
			Imgproc.drawContours(result, contours, -1, new Scalar(0, 0, 0), thickness);
		}

		return result;
	}

	public static ArrayList<MatOfPoint> findContours(Mat image) {
		Mat binary = image.clone();

		ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
		Imgproc.findContours(binary, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

		return contours;
	}
}