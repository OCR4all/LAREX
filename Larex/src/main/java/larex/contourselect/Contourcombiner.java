package larex.contourselect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

public class Contourcombiner {

	// Comparator since Lambda not working on some servers
	private static Comparator<Double> COMP_DOUBLE = new Comparator<Double>() {
		@Override
		public int compare(Double o1, Double o2) {
			return Double.compare(o1, o2);
		}
	};

	public static MatOfPoint combine(Collection<MatOfPoint> contours, Mat source) {
		return combine(contours, source, 3);
	}

	public static MatOfPoint combine(Collection<MatOfPoint> contours, Mat source, double grow) {
		if (contours.size() < 1)
			throw new IllegalArgumentException("Can't combine 0 contours.");
		
		final Mat workingImage = new Mat(source.rows(), source.cols(), CvType.CV_8UC1, new Scalar(0));
		List<MatOfPoint> workingContours = new ArrayList<>(contours);

		double growing = 1;
		int previousContourCount = contours.size();
		
		while (workingContours.size() > 1) {
			//Calc center x and y via moments
			final List<Double> centersY = new ArrayList<>();
			final List<Double> centersX = new ArrayList<>();
			for (MatOfPoint contour : workingContours) {
				final Moments moments= Imgproc.moments(contour);
				final Point centroid = new Point();
				centroid.x = moments.get_m10() / moments.get_m00();
				centroid.y = moments.get_m01() / moments.get_m00();	
				
				centersY.add(centroid.y);
				centersX.add(centroid.x);
			}

			//Find median distance between contour moments
			centersY.sort(COMP_DOUBLE);
			centersX.sort(COMP_DOUBLE);

			final List<Double> distancesX = new ArrayList<>();
			final List<Double> distancesY = new ArrayList<>();
			for(int i = 0; i < workingContours.size() - 1; i++) {
				distancesX.add(centersX.get(i+1) - centersX.get(i));
				distancesY.add(centersY.get(i+1) - centersY.get(i));
			}
			
			double minDistanceY = distancesX.get(distancesX.size()/2) * growing;
			double minDistanceX = distancesY.get(distancesY.size()/2) * growing;
			
			//Smear Contours to combine them	
			Imgproc.drawContours(workingImage, new ArrayList<>(workingContours), -1, new Scalar(255), -1);

			Mat temp = workingImage.clone();

			// Vertical Smearing
			for (int x = 0; x < workingImage.rows(); x++) {
				int currentGap = 0;
				for (int y = 0; y < workingImage.cols(); y++) {
					double value = temp.get(x, y)[0];
					if (value > 0) {
						// Entered Contour
						if (currentGap < minDistanceY) {
							// Draw over
							for (int i = 1; i <= currentGap; i++)
								workingImage.put(x, y - i, new byte[] { 1 });
						}
						currentGap = 0;
					} else {
						// Entered/Still in Gap
						currentGap++;
					}
				}
			}

			// Horizontal smearing
			for (int y = 0; y < workingImage.cols(); y++) {
				int currentGap = 0;
				for (int x = 0; x < workingImage.rows(); x++) {
					double value = temp.get(x, y)[0];
					if (value > 0) {
						// Entered Contour
						if (currentGap < minDistanceX) {
							// Draw over
							for (int i = 1; i <= currentGap; i++)
								workingImage.put(x - i, y, new byte[] { 1 });
						}
						currentGap = 0;
					} else {
						// Entered/Still in Gap
						currentGap++;
					}

				}
			}

			temp.release();

			workingContours = new ArrayList<>(Contourextractor.extract(workingImage));
			int contourCount = workingContours.size();
			
			if(previousContourCount == contourCount) {
				growing = growing*grow;
			}else {
				growing = 1;
			}
			
			previousContourCount = contourCount;
			
			if(workingContours.size() > 1) {
				//Draw small border to account for shrinking
				Imgproc.drawContours(workingImage, new ArrayList<>(workingContours), -1, new Scalar(255), 1);
				workingContours = new ArrayList<>(Contourextractor.extract(workingImage));
			}
		}
		workingImage.release();

		return workingContours.get(0);
	}
}
