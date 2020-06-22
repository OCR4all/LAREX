package de.uniwue.algorithm.operators;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import de.uniwue.algorithm.data.MemoryCleaner;
import de.uniwue.algorithm.geometry.regions.RegionSegment;
import de.uniwue.algorithm.geometry.regions.type.PAGERegionType;
import de.uniwue.algorithm.imageProcessing.ImageProcessor;

/**
 * Merger to combine multiple contours/segments to one
 */
public class Merger {

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
	 * @param source   Size of the source image that includes the contours
	 * @return MatOfPoint contour that includes all contours
	 */
	public static MatOfPoint smearMerge(Collection<MatOfPoint> contours, Size source) {
		return smearMerge(contours, source, 2.5, 1.5, 10);
	}

	/**
	 * Combine contours in a source image via smearing with x and y growth factors
	 * to potentially speed up on contours with a large distance in-between
	 * 
	 * @param contours Contours to combine
	 * @param source   Size of the source image that includes the contours
	 * @param growthY  Vertical growth factor that is applied every time a smearing
	 *                 iteration does not change the combined contour count
	 * @param growthX  Horizontal growth factor that is applied every time a
	 *                 smearing iteration does not change the combined contour count
	 * @return MatOfPoint contour that includes all contours
	 */
	public static MatOfPoint smearMerge(Collection<MatOfPoint> contours, Size source, double growthY, double growthX , int maxIterations) {
		if (contours.size() < 1)
			throw new IllegalArgumentException("Can't combine 0 contours.");

		List<MatOfPoint> workingContours = new ArrayList<>(contours);

		final Mat resultImage = new Mat(source, CvType.CV_8UC1, new Scalar(0));
		Imgproc.drawContours(resultImage, new ArrayList<>(workingContours), -1, new Scalar(255), -1);
		final Mat workImage = resultImage.clone();

		double growingX = 1;
		double growingY = 1;
		int previousContourCount = contours.size();

		final int cols = resultImage.cols();
		final int rows = resultImage.rows();

		int top = rows;
		int bottom = 0;
		int left = cols;
		int right = 0;
		int it = 0;
		while (workingContours.size() > 1 ) {
			// Smear Contours to combine them
			// Calc center x and y via moments
			final List<Integer> heights = new ArrayList<>();
			final List<Integer> widths = new ArrayList<>();
			for (final MatOfPoint contour : workingContours) {
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
								resultImage.put(y - i, x, 255);
						}

						if (currentGapX < medianDistanceX) {
							if (currentGapX > 0)
								// Draw over
								for (int i = 1; i <= currentGapX; i++)
									resultImage.put(y, x - i, 255);
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

			workingContours = new ArrayList<>(Contourextractor.fromInverted(resultImage));
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
						workImage.put(y, x, 255);
					else
						workImage.put(y, x, new byte[] { 0 });

			previousContourCount = contourCount;
			
			// Fallback convex hull
			if(it++ > maxIterations) {
				MatOfInt hull = new MatOfInt();
				final MatOfPoint contour_points = new MatOfPoint(workingContours.stream().flatMap(c -> c.toList().stream()).toArray(Point[]::new));
				Imgproc.convexHull(contour_points, hull);
				
				final MatOfPoint contour_hull = new MatOfPoint();
				
				contour_hull.create((int) hull.size().height,1,CvType.CV_32SC2);

				for(int i = 0; i < hull.size().height ; i++) {
				    int index = (int)hull.get(i, 0)[0];
				    double[] point = new double[] {
				        contour_points.get(index, 0)[0], contour_points.get(index, 0)[1]
				    };
				    contour_hull.put(i, 0, point);
				} 
				return contour_hull;
			}
		}

		// Draw small border to account for shrinking
		Imgproc.drawContours(resultImage, new ArrayList<>(workingContours), -1, new Scalar(255), 2);

		workingContours = new ArrayList<>(Contourextractor.fromInverted(resultImage));

		// Clean Memory
		MemoryCleaner.clean(resultImage,workImage);
		return workingContours.get(0);
	}

	/**
	 * Merge RegionSegments by combining overlapping and drawing lines between non
	 * overlapping segments
	 * 
	 * @param segments   Segments to merge
	 * @return
	 */
	public static RegionSegment lineMerge(ArrayList<RegionSegment> segments) {
		if (segments.size() < 2) {
			return null;
		}
		
		// Calculate binary image size via max segments x and y positions
		final Set<Point> points = segments.stream().flatMap(s -> s.getPoints().toList().stream())
												.collect(Collectors.toSet());
		final double maxX = points.stream().map(p -> p.x).max(Comparator.naturalOrder()).orElse(0.0);
		final double maxY = points.stream().map(p -> p.y).max(Comparator.naturalOrder()).orElse(0.0);
		
		
		// Create combined segments
		final Mat temp = new Mat(new Size(maxX,maxY), CvType.CV_8UC1, new Scalar(0));
		ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
		ArrayList<Point> cogs = new ArrayList<Point>();
		double biggestArea = Double.MIN_VALUE;
		PAGERegionType biggestRegionType = segments.get(0).getType();

		for (RegionSegment region : segments) {
			final MatOfPoint regionContour = region.getPoints();
			contours.add(regionContour);

			Point cog = ImageProcessor.calcCenterOfGravityOCV(region.getPoints(), true);
			cogs.add(cog);
			
			final double regionArea = Imgproc.contourArea(regionContour);
			if(biggestArea < regionArea) {
				biggestArea = regionArea;
				biggestRegionType = region.getType();
			}
		}

		Imgproc.drawContours(temp, contours, -1, new Scalar(255), -1);

		ArrayList<Point> remainingCogs = new ArrayList<Point>();
		ArrayList<Point> assignedCogs = new ArrayList<Point>();

		remainingCogs.addAll(cogs);
		assignedCogs.add(cogs.get(0));
		remainingCogs.remove(cogs.get(0));

		while (remainingCogs.size() > 0) {
			Point assignedCogTemp = null;
			Point remCogTemp = null;

			double minDist = Double.MAX_VALUE;

			for (Point assignedCog : assignedCogs) {
				for (Point remainingCog : remainingCogs) {
					double dist = Math.pow(remainingCog.x - assignedCog.x, 2)
							+ Math.pow(remainingCog.y - assignedCog.y, 2);

					if (dist < minDist) {
						assignedCogTemp = assignedCog;
						remCogTemp = remainingCog;
						minDist = dist;
					}
				}
			}

			assert assignedCogTemp != null;
			Imgproc.line(temp, assignedCogTemp, remCogTemp, new Scalar(255), 2);
			assignedCogs.add(remCogTemp);
			remainingCogs.remove(remCogTemp);

		}

		contours = new ArrayList<>(Contourextractor.fromInverted(temp));
		MemoryCleaner.clean(temp);

		return new RegionSegment(biggestRegionType, contours.get(0));
	}
}
