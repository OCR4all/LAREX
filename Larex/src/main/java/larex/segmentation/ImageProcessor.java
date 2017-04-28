package larex.segmentation;

import larex.imageProcessing.ImageContainerOLD;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

public class ImageProcessor {

	@Deprecated
	public static void initImage(ImageContainerOLD imageContainer, int desiredHeight) {
		Mat resized = resize(imageContainer.getOriginal(), desiredHeight);
		Mat gray = calcGray(resized);
		Mat binary = calcBinary(gray);
		Mat inverted = invertImage(binary);

		imageContainer.setResized(resized);
		imageContainer.setBinary(binary);
		imageContainer.setGray(gray);
		imageContainer.setInverted(inverted);
	}

	public static Mat resize(Mat source, int desiredHeight) {
		if (desiredHeight == -1) {
			return source;
		}
		Mat result = new Mat();

		double scaleFactor = (double) source.rows() / desiredHeight;
		Imgproc.resize(source, result, new Size(Math.round(source.cols() / scaleFactor), desiredHeight));

		return result;
	}

	public static Mat dilate(Mat binary, Size kernelSize) {
		Mat result = new Mat();
		Mat kernel = Mat.ones(kernelSize, CvType.CV_8U);
		Imgproc.dilate(binary, result, kernel);

		return result;
	}

	public static Mat invertImage(Mat binary) {
		Mat inverted = new Mat(binary.size(), binary.type(), new Scalar(255));
		Core.subtract(inverted, binary, inverted);

		return inverted;
	}

	public static Mat calcGray(Mat source) {
		Mat gray = new Mat();
		Imgproc.cvtColor(source, gray, Imgproc.COLOR_BGR2GRAY);

		return gray;
	}

	public static Mat calcBinary(Mat gray) {
		Mat binary = new Mat();
		Imgproc.threshold(gray, binary, 0, 255, Imgproc.THRESH_OTSU);

		return binary;
	}

	public static Mat calcBinaryFromThresh(Mat gray, int thresh) {
		Mat binary = new Mat();
		Imgproc.threshold(gray, binary, thresh, 255, Imgproc.THRESH_BINARY);

		return binary;
	}

	public static BufferedImage matToBufferedImage(Mat input) {
		MatOfByte matOfByte = new MatOfByte();
		BufferedImage bufImage = null;

		Highgui.imencode(".jpg", input, matOfByte);
		byte[] byteArray = matOfByte.toArray();
		try {
			InputStream in = new ByteArrayInputStream(byteArray);
			bufImage = ImageIO.read(in);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return bufImage;
	}

	public static void showResult(Mat img, Size size) {
		Imgproc.resize(img, img, size);

		MatOfByte matOfByte = new MatOfByte();
		Highgui.imencode(".jpg", img, matOfByte);
		byte[] byteArray = matOfByte.toArray();
		BufferedImage bufImage = null;
		try {
			InputStream in = new ByteArrayInputStream(byteArray);
			bufImage = ImageIO.read(in);
			JFrame frame = new JFrame();
			frame.getContentPane().add(new JLabel(new ImageIcon(bufImage)));
			frame.pack();
			frame.setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static java.awt.Point calcCenterOfGravity(MatOfPoint input) {
		Point[] points = input.toArray();
		
		double sumX = 0;
		double sumY = 0;

		for(int i = 0; i < points.length; i++) {
			sumX += points[i].x;
			sumY += points[i].y;
		}
		
		double avgX = sumX / points.length;
		double avgY = sumY / points.length;
		
		return new java.awt.Point(Math.round((float) avgX), Math.round((float)avgY));
	}
}