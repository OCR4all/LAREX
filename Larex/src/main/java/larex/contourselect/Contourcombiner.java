package larex.contourselect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class Contourcombiner {

	// Comparator since Lambda not working on some servers
	private static Comparator<Integer> COMP_DOUBLE = new Comparator<Integer>() {
		@Override
		public int compare(Integer o1, Integer o2) {
			return Integer.compare(o1, o2);
		}
	};

	public static MatOfPoint combine(Collection<MatOfPoint> contours, Mat source) {
		return combine(contours, source, 2.5,1.5);
	}

	public static MatOfPoint combine(Collection<MatOfPoint> contours, Mat source, double growthY, double growthX) {
		if (contours.size() < 1)
			throw new IllegalArgumentException("Can't combine 0 contours.");
		
		final Mat workingImage = new Mat(source.rows(), source.cols(), CvType.CV_8UC1, new Scalar(0));
		Mat temp = workingImage.clone();
		List<MatOfPoint> workingContours = new ArrayList<>(contours);

		double growingX = 1;
		double growingY = 1;
		int previousContourCount = contours.size();
		
		final int cols = workingImage.cols();
		final int rows = workingImage.rows();
		
		int top = rows;
		int bottom = 0;
		int left = cols;
		int right = 0;
		while (workingContours.size() > 1) {
			//Calc center x and y via moments
			final List<Integer> heights = new ArrayList<>();
			final List<Integer> widths = new ArrayList<>();
			for (MatOfPoint contour : workingContours) {
				final Rect bounds = Imgproc.boundingRect(contour);
			
				top = bounds.y < top ? bounds.y : top; 
				bottom = bounds.br().y > bottom ? (int) bounds.br().y : bottom; 
				left = bounds.x < left ? bounds.x : left; 
				right = bounds.br().x > right ? (int) bounds.br().x : right; 

				heights.add(bounds.height);
				widths.add(bounds.width);
			}

			//Find median widths of contours
			widths.sort(COMP_DOUBLE);
			heights.sort(COMP_DOUBLE);

			final double medianDistanceX = widths.get(widths.size()/2)*growingX;
			final double medianDistanceY = heights.get(heights.size()/2)*growingY;
			
			//Smear Contours to combine them	
			Imgproc.drawContours(workingImage, new ArrayList<>(workingContours), -1, new Scalar(255), -1);


			// Smearing
			int[] currentGapsX = new int[rows];
			Arrays.fill(currentGapsX, Integer.MAX_VALUE);
			for (int x = left; x <= right; x++) {
				int currentGapY = Integer.MAX_VALUE;
				for (int y = top; y <= bottom; y++) {
					double value = temp.get(y, x)[0];
					if (value > 0) {
						// Entered Contour
						final int currentGapX = currentGapsX[y];
						
						if (currentGapY < medianDistanceY) {
							// Draw over
							for (int i = 1; i <= currentGapY; i++)
								workingImage.put(y - i,x, new byte[] { 1 });
						}

						if (currentGapX < medianDistanceX) {
							// Draw over
							for (int i = 1; i <= currentGapX; i++)
								workingImage.put(y,x - i, new byte[] { 1 });
						}
						
						currentGapY = 0;
						currentGapsX[x] = 0;
					} else {
						// Entered/Still in Gap
						currentGapY++;
						currentGapsX[x]++;
					}
				}
			}


			workingContours = new ArrayList<>(Contourextractor.extract(workingImage));
			int contourCount = workingContours.size();
			
			if(previousContourCount == contourCount) {
				growingX = growingX*growthX;
				growingY = growingY*growthY;
			}else {
				growingX = 1;
				growingY = 1;
			}
			
			//Copy current to temp
			for (int x = left; x <= right; x++) 
				for (int y = top; y <= bottom; y++){
					if (workingImage.get(y, x)[0] > 0) temp.put(y,x,1);
					else temp.put(y, x, 0);
				
			previousContourCount = contourCount;
		}

		//Draw small border to account for shrinking
		Imgproc.drawContours(workingImage, new ArrayList<>(workingContours), -1, new Scalar(255), 2);
		workingContours = new ArrayList<>(Contourextractor.extract(workingImage));
		workingImage.release();
		temp.release();
		System.gc();

		return workingContours.get(0);
	}
}
