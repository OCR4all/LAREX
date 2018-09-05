package larex.contourselect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class Contourcombiner {

	// Comparator since Lambda not working on some servers
	private static Comparator<Integer> COMP_INT = new Comparator<Integer>() {
		@Override public int compare(Integer o1, Integer o2) { return Integer.compare(o1, o2); }
	}; 
		
	public static MatOfPoint combine(Collection<MatOfPoint> contours, Mat source) {
		if(contours.size() < 1)
			throw new IllegalArgumentException("Can't combine 0 contours.");
			
		List<MatOfPoint> workingContours = new ArrayList<>(contours);
		
		while(workingContours.size() > 1) {
			final List<Integer> heights = new ArrayList<>();
			final List<Integer> widths = new ArrayList<>();
			
			for(MatOfPoint contour: contours) {
				heights.add(contour.height());
				widths.add(contour.width());
			}
			
			heights.sort(COMP_INT);
			widths.sort(COMP_INT);
			
			final int medianHeight = heights.get((heights.size()/2));
			final int medianWidth = widths.get((widths.size()/2));
			
			final int thickness = medianHeight < medianWidth ? medianWidth : medianHeight;
			
			Mat workingImage = new Mat(source.rows(), source.cols(), CvType.CV_8UC1, new Scalar(0));
			Imgproc.drawContours(workingImage, new ArrayList<>(contours), -1, new Scalar(255), thickness);

			workingContours = new ArrayList<>(Contourextractor.extract(workingImage));
			workingImage.release();
		}
		
		return workingContours.get(0);
	}
}
