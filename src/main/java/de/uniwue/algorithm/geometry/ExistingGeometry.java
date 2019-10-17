package de.uniwue.algorithm.geometry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import de.uniwue.algorithm.data.MemoryCleaner;
import de.uniwue.algorithm.geometry.regions.RegionSegment;

public class ExistingGeometry {

	private Collection<RegionSegment> fixedRegionSegments;
	private Collection<PointList> cuts;

	public ExistingGeometry(Collection<RegionSegment> fixedRegionSegments, Collection<PointList> cuts) {
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
	public Mat drawIntoImage(final Mat image, double scaleFactor) {
		final Mat result = image.clone();

		// Resize
		final List<MatOfPoint> contours = fixedRegionSegments.stream().map(s -> s.getResizedPoints(scaleFactor))
											.collect(Collectors.toList());
		Imgproc.drawContours(result, contours, -1, new Scalar(0), -1);
		MemoryCleaner.clean(contours);

		for (PointList cut : cuts) {
			ArrayList<Point> ocvPoints = new ArrayList<>(cut.getResizedPoints(scaleFactor).toList());
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

	public Collection<RegionSegment> getFixedRegionSegments() {
		return fixedRegionSegments;
	}

	public Collection<PointList> getCuts() {
		return cuts;
	}
}