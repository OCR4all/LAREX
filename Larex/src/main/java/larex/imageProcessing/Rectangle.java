package larex.imageProcessing;

import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class Rectangle {
	
	public static Mat drawStraightRect(Mat original, Rect boundingRect, Scalar color, int thickness) {
		Mat result = original.clone();

		Imgproc.rectangle(result, boundingRect.tl(), boundingRect.br(), color, thickness);

		return result;
	}
}