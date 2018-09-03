package larex.contourselect;

import java.util.Collection;
import java.util.LinkedList;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;

public class Contourcombiner {

	public static MatOfPoint combine(Collection<MatOfPoint> contours, Mat source) {
		LinkedList<Contour> contourList = new LinkedList<>();
		
		for(MatOfPoint contour : contours) {
			contourList.add(new Contour(contour));
		}
		
		Region region = new Region(contourList,source);
		
		return region.getRegionMOP();
	}
}
