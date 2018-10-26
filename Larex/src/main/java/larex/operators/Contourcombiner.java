package larex.operators;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * Contor combiner to combine multiple contours to one segment
 */
public class Contourcombiner {

	// Comparator since Lambda not working on some servers
	private static Comparator<Integer> COMP_DOUBLE = new Comparator<Integer>() {
		@Override
		public int compare(Integer o1, Integer o2) {
			return Integer.compare(o1, o2);
		}
	};

	/**
	 * Combine contours in a source image via smearing with default growth values
	 * 
	 * @param contours Contours to combine
	 * @param source   Source image that includes the contours
	 * @return MatOfPoint contour that includes all contours
	 */
	public static MatOfPoint combine(Collection<MatOfPoint> contours, Mat source) {
		return combine(contours, source, 2.5, 1.5);
	}

	/**
	 * Combine contours in a source image via smearing with x and y growth factors
	 * to potentially speed up on contours with a large distance inbetween
	 * 
	 * @param contours Contours to combine
	 * @param source   Source image that includes the contours
	 * @param growthY  Vertical growth factor that is applied every time a smearing
	 *                 iteration does not change the combined contour count
	 * @param growthX  Horizontal growth factor that is applied every time a
	 *                 smearing iteration does not change the combined contour count
	 * @return MatOfPoint contour that includes all contours
	 */
	public static MatOfPoint combine(Collection<MatOfPoint> contours, Mat source, double growthY, double growthX) {
		if (contours.size() < 1)
			throw new IllegalArgumentException("Can't combine 0 contours.");

		List<MatOfPoint> workingContours = new ArrayList<>(contours);

		Mat resultImage = new Mat(source.rows(), source.cols(), CvType.CV_8UC1, new Scalar(0));
		Imgproc.drawContours(resultImage, new ArrayList<>(workingContours), -1, new Scalar(255), -1);
		Mat workImage = resultImage.clone();

		double growingX = 1;
		double growingY = 1;
		int previousContourCount = contours.size();

		final int cols = resultImage.cols();
		final int rows = resultImage.rows();

		int top = rows;
		int bottom = 0;
		int left = cols;
		int right = 0;
		while (workingContours.size() > 1) {
			// Smear Contours to combine them
			// Calc center x and y via moments
			final List<Integer> heights = new ArrayList<>();
			final List<Integer> widths = new ArrayList<>();
			for (MatOfPoint contour : workingContours) {
				final Rect bounds = Imgproc.boundingRect(contour);

				top = bounds.y <= top ? bounds.y - 1 : top;
				bottom = bounds.br().y >= bottom ? (int) bounds.br().y + 1 : bottom;
				left = bounds.x <= left ? bounds.x - 1 : left;
				right = bounds.br().x >= right ? (int) bounds.br().x + 1 : right;

				heights.add(bounds.height);
				widths.add(bounds.width);
			}

			// Find median widths of contours
			widths.sort(COMP_DOUBLE);
			heights.sort(COMP_DOUBLE);

			final double medianDistanceX = widths.get(widths.size() / 2) * growingX;
			final double medianDistanceY = heights.get(heights.size() / 2) * growingY;

			// Smearing
			int[] currentGapsX = new int[rows];
			Arrays.fill(currentGapsX, ((int) medianDistanceX) + 1);
			for (int x = left; x <= right; x++) {
				int currentGapY = ((int) medianDistanceY) + 1;
				for (int y = top; y <= bottom; y++) {
					double value = workImage.get(y, x)[0];
					if (value > 0) {
						// Entered Contour
						final int currentGapX = currentGapsX[y];

						if (currentGapY < medianDistanceY) {
							// Draw over
							for (int i = 1; i <= currentGapY; i++)
								resultImage.put(y - i, x, new double[] { 255 });
						}

						if (currentGapX < medianDistanceX) {
							if (currentGapX > 0)
								// Draw over
								for (int i = 1; i <= currentGapX; i++)
									resultImage.put(y, x - i, new double[] { 255 });
						}

						currentGapY = 0;
						currentGapsX[y] = 0;
					} else {
						// Entered/Still in Gap
						currentGapY++;
						currentGapsX[y]++;
					}
				}
			}

			workingContours = new ArrayList<>(Contourextractor.fromSource(resultImage));
			int contourCount = workingContours.size();

			if (previousContourCount == contourCount) {
				growingX = growingX * growthX;
				growingY = growingY * growthY;
			} else {
				growingX = 1;
				growingY = 1;
			}

			// Copy current to temp
			for (int x = left; x <= right; x++)
				for (int y = top; y <= bottom; y++)
					if (resultImage.get(y, x)[0] > 0)
						workImage.put(y, x, new double[] { 255 });
					else
						workImage.put(y, x, new byte[] { 0 });

			previousContourCount = contourCount;
		}

		// Draw small border to account for shrinking
		Imgproc.drawContours(resultImage, new ArrayList<>(workingContours), -1, new Scalar(255), 2);
		workingContours = new ArrayList<>(Contourextractor.fromSource(resultImage));
		resultImage.release();
		workImage.release();
		System.gc();

		return workingContours.get(0);
	}
}
