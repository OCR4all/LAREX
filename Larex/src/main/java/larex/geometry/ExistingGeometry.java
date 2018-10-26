package larex.geometry;

import java.util.ArrayList;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import larex.segmentation.result.PointList;
import larex.segmentation.result.RegionSegment;

public class ExistingGeometry {

	private ArrayList<RegionSegment> fixedRegionSegments;
	private ArrayList<PointList> cuts;

	public ExistingGeometry(ArrayList<RegionSegment> fixedRegionSegments, ArrayList<PointList> cuts) {
		this.fixedRegionSegments = fixedRegionSegments;
		this.cuts = cuts;
	}

	/**
	 * Draws all existing geometries into the image.
	 * 
	 * @param image       Image to draw into
	 * @param scaleFactor Scale factor of the image
	 * @return Clone of the image with drawn in geometry
	 */
	public Mat drawIntoImage(Mat image, double scaleFactor) {
		Mat result = image.clone();

		// Resize
		ArrayList<ArrayList<Point>> ocvPointLists = new ArrayList<ArrayList<Point>>();
		for (RegionSegment pointList : fixedRegionSegments)
			ocvPointLists.add(new ArrayList<>(pointList.getResizedPoints(scaleFactor).toList()));
		for (PointList cut : cuts)
			ocvPointLists.add(new ArrayList<>(cut.getResizedPoints(scaleFactor).toList()));

		// Convert and draw
		for (ArrayList<Point> ocvPoints : ocvPointLists) {
			if (ocvPoints != null && ocvPoints.size() > 1) {
				Point lastPoint = ocvPoints.get(0);

				for (int i = 1; i < ocvPoints.size(); i++) {
					Point currentPoint = ocvPoints.get(i);
					Imgproc.line(result, lastPoint, currentPoint, new Scalar(0), 2);
					lastPoint = currentPoint;
				}
			}
		}

		return result;
	}

	public ArrayList<RegionSegment> getFixedRegionSegments() {
		return fixedRegionSegments;
	}

	public ArrayList<PointList> getCuts() {
		return cuts;
	}
}