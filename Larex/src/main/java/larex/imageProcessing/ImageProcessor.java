package larex.imageProcessing;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

public class ImageProcessor {

	public static Mat erode(Mat binary, Size kernelSize) {
		Mat result = new Mat();
		Mat kernel = Mat.ones(kernelSize, CvType.CV_8U);
		Imgproc.erode(binary, result, kernel);

		return result;
	}

	public static Mat dilate(Mat binary, Size kernelSize) {
		Mat result = new Mat();
		Mat kernel = Mat.ones(kernelSize, CvType.CV_8U);
		Imgproc.dilate(binary, result, kernel);

		return result;
	}

	public static Mat calcEdges(Mat gray, int threshLow, int threshHigh) {
		Mat edges = new Mat();
		Imgproc.Canny(gray, edges, threshLow, threshHigh);

		return edges;
	}

	public static Mat calcEdgesAfterBlurring(Mat gray, int threshLow, int threshHigh, int kernel) {
		Mat blurred = new Mat();
		Imgproc.medianBlur(gray, blurred, kernel);

		Mat edges = new Mat();
		Imgproc.Canny(blurred, edges, threshLow, threshHigh);

		return edges;
	}

	public static Mat invertImage(Mat binary) {
		Mat inverted = new Mat(binary.size(), binary.type(), new Scalar(255));
		Core.subtract(inverted, binary, inverted);

		return inverted;
	}

	public static Mat histEqual(Mat gray) {
		Mat result = new Mat();
		Imgproc.equalizeHist(gray, result);

		return result;
	}

	public static Mat blurrImage(Mat gray, int kernelSize) {
		Mat result = new Mat();
		Imgproc.medianBlur(gray, result, kernelSize);

		return result;
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

	public static Mat calcBinaryFromTopAndBottom(Mat source, int percentage) {
		int topBottomHeight = source.height() * percentage / 100;

		Mat top = source.submat(new Rect(new Point(0, 0), new Point(source.width() - 1, topBottomHeight)));
		Mat bottom = source.submat(new Rect(new Point(0, source.height() - 1 - topBottomHeight),
				new Point(source.width() - 1, source.height() - 1)));

		// Imgcodecs.imwrite("C:/Users/Reul/Desktop/top.jpg", top);
		// Imgcodecs.imwrite("C:/Users/Reul/Desktop/bottom.jpg", bottom);

		Mat topBottom = new Mat(new Size(top.width(), top.height() + bottom.height()), top.type());

		for (int y = 0; y < top.rows(); y++) {
			for (int x = 0; x < top.cols(); x++) {
				topBottom.put(y, x, top.get(y, x));
				topBottom.put(top.height() + y, x, bottom.get(y, x));
			}
		}

		// Imgcodecs.imwrite("C:/Users/Reul/Desktop/topBottom.jpg", topBottom);
		Mat grayTopBottom = ImageProcessor.calcGray(topBottom);
		// Mat blurr = ImageProcessor.blurrImage(gray, 7);
		Scalar avgGray = Core.mean(grayTopBottom);
		int avg = (int) (avgGray.val[0] * 0.9);
		// int avg = (int) avgGray.val[0] - 20;
		// System.out.println(avg);

		Mat gray = ImageProcessor.calcGray(source);
		Mat binary = new Mat();
		Imgproc.threshold(gray, binary, avg, 255, Imgproc.THRESH_BINARY);

		return binary;
	}

	public static Mat resize(Mat source, int desiredHeight) {
		if (desiredHeight == -1) {
			return source;
		}

		Mat result = new Mat();

		double scaleFactor = (double) source.rows() / desiredHeight;
		Imgproc.resize(source, result, new Size(source.cols() / scaleFactor, desiredHeight));

		return result;
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

	public static Mat showNextTo(Mat mat1, Mat mat2) {
		if (mat1.type() == CvType.CV_8U && mat2.type() != CvType.CV_8U) {
			Imgproc.cvtColor(mat1, mat1, Imgproc.COLOR_GRAY2BGR);
		} else if (mat2.type() == CvType.CV_8U && mat1.type() != CvType.CV_8U) {
			Imgproc.cvtColor(mat2, mat2, Imgproc.COLOR_GRAY2BGR);
		}

		if (mat1.height() < mat2.height()) {
			mat2 = ImageProcessor.resize(mat2, mat1.height());
		} else if (mat1.height() > mat2.height()) {
			mat1 = ImageProcessor.resize(mat1, mat2.height());
		}

		Mat result = new Mat(mat1.rows(), mat1.cols() + mat2.cols(), mat1.type());
		byte[] pixel = { 0, 0, 0 };

		for (int y = 0; y < mat1.rows(); y++) {
			for (int x = 0; x < mat1.cols(); x++) {
				mat1.get(y, x, pixel);
				result.put(y, x, pixel);

				mat2.get(y, x, pixel);
				result.put(y, mat1.cols() + x, pixel);
			}
		}

		return result;
	}

	public static Mat img2Mat(BufferedImage in) {
		Mat out;
		byte[] data;
		int r, g, b;

		// if (in.getType() == BufferedImage.TYPE_INT_RGB) {
		if (in.getType() == 0) {
			out = new Mat(in.getHeight(), in.getWidth(), CvType.CV_8UC3);
			data = new byte[in.getWidth() * in.getHeight() * (int) out.elemSize()];
			int[] dataBuff = in.getRGB(0, 0, in.getWidth(), in.getHeight(), null, 0, in.getWidth());
			for (int i = 0; i < dataBuff.length; i++) {
				data[i * 3] = (byte) ((dataBuff[i] >> 16) & 0xFF);
				data[i * 3 + 1] = (byte) ((dataBuff[i] >> 8) & 0xFF);
				data[i * 3 + 2] = (byte) ((dataBuff[i] >> 0) & 0xFF);
			}
		} else if (in.getType() == BufferedImage.TYPE_BYTE_BINARY) {
			out = new Mat(in.getHeight(), in.getWidth(), CvType.CV_8UC1);
			data = new byte[in.getWidth() * in.getHeight() * (int) out.elemSize()];
			int[] dataBuff = in.getRGB(0, 0, in.getWidth(), in.getHeight(), null, 0, in.getWidth());
			for (int i = 0; i < dataBuff.length; i++) {
				if (dataBuff[i] == -1) {
					data[i] = (byte) 255;
				} else {
					data[i] = (byte) 0;
				}
			}
		} else {
			out = new Mat(in.getHeight(), in.getWidth(), CvType.CV_8UC1);
			data = new byte[in.getWidth() * in.getHeight() * (int) out.elemSize()];
			int[] dataBuff = in.getRGB(0, 0, in.getWidth(), in.getHeight(), null, 0, in.getWidth());
			for (int i = 0; i < dataBuff.length; i++) {
				r = (byte) ((dataBuff[i] >> 16) & 0xFF);
				g = (byte) ((dataBuff[i] >> 8) & 0xFF);
				b = (byte) ((dataBuff[i] >> 0) & 0xFF);
				data[i] = (byte) ((0.21 * r) + (0.71 * g) + (0.07 * b)); // luminosity
			}
		}
		out.put(0, 0, data);

		return out;
	}

	public static BufferedImage mat2Img(Mat in) {
		BufferedImage out;
		byte[] data = new byte[in.width() * in.height() * (int) in.elemSize()];
		int type;
		in.get(0, 0, data);

		if (in.channels() == 1)
			type = BufferedImage.TYPE_BYTE_GRAY;
		else
			type = BufferedImage.TYPE_3BYTE_BGR;

		out = new BufferedImage(in.width(), in.height(), type);

		out.getRaster().setDataElements(0, 0, in.width(), in.height(), data);

		return out;
	}

	public static Point calcCenterOfGravity(MatOfPoint input) {
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

	// {tl, tr, br, bl}
	public static Point[] findLinePointOrder(MatOfPoint input) {
		Point[] points = input.toArray();
		Point[] result = new Point[4];

		if (points.length == 4) {
			int mostRight = -1;
			int secondMostRight = -1;

			Point mostRightPoint = null;
			Point secondMostRightPoint = null;

			for (int i = 0; i < points.length; i++) {
				Point point = points[i];

				if (point.x > mostRight) {
					secondMostRight = mostRight;
					mostRight = (int) point.x;
					secondMostRightPoint = mostRightPoint;
					mostRightPoint = point;
				} else if (point.x > secondMostRight) {
					secondMostRight = (int) point.x;
					secondMostRightPoint = point;
				}
			}

			if (mostRightPoint.y > secondMostRightPoint.y) {
				result[1] = mostRightPoint;
				result[2] = secondMostRightPoint;
			} else {
				result[1] = secondMostRightPoint;
				result[2] = mostRightPoint;
			}

			Point leftPoint1 = null;
			Point leftPoint2 = null;

			for (int i = 0; i < points.length; i++) {
				if (!points[i].equals(mostRightPoint) && !points[i].equals(secondMostRightPoint)) {
					if (leftPoint1 == null) {
						leftPoint1 = points[i];
					} else {
						leftPoint2 = points[i];
					}
				}
			}

			if (leftPoint1.y > leftPoint2.y) {
				result[0] = leftPoint1;
				result[3] = leftPoint2;
			} else {
				result[0] = leftPoint2;
				result[3] = leftPoint1;
			}

		} else {
			System.out.println("Fail! Expected points.length to be 4!");
		}

		return result;
	}

	public static double[] calcAverageBackground(Mat original) {
		if (original.channels() == 1) {
			return calcAverageBackgroundGray(original);
		}

		Mat gray = new Mat();
		Mat binary = new Mat();
		Imgproc.threshold(gray, binary, -1, 255, Imgproc.THRESH_OTSU);

		double sumRed = 0;
		double sumGreen = 0;
		double sumBlue = 0;
		int cnt = 0;

		for (int y = 0; y < binary.rows(); y++) {
			for (int x = 0; x < binary.cols(); x++) {
				if (binary.get(y, x)[0] > 0) {
					double[] colors = original.get(y, x);
					sumRed += colors[2];
					sumGreen += colors[1];
					sumBlue += colors[0];
					cnt++;
				}
			}
		}

		double[] avgBackground = { sumBlue / cnt, sumGreen / cnt, sumRed / cnt };

		releaseAll(gray, binary);
		
		return avgBackground;
	}

	public static double[] calcAverageBackgroundGray(Mat gray) {
		Mat binary = new Mat();
		Imgproc.threshold(gray, binary, -1, 255, Imgproc.THRESH_OTSU);

		double sum = 0;
		int cnt = 0;

		for (int y = 0; y < binary.rows(); y++) {
			for (int x = 0; x < binary.cols(); x++) {
				if (binary.get(y, x)[0] > 0) {
					double[] colors = gray.get(y, x);
					sum += colors[0];
					cnt++;
				}
			}
		}

		double[] avgBackground = { sum / cnt };

		releaseAll(binary);
		
		return avgBackground;
	}
	
	public static void releaseAll(Mat... mats) {
		for(Mat mat : mats) {
			if (mat != null) {
				mat.release();
				mat = null;
			}
		}
	}
	

	public static Point calcCenterOfGravityOCV(MatOfPoint input,
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