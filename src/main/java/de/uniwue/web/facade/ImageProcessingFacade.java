package de.uniwue.web.facade;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import de.uniwue.algorithm.data.MemoryCleaner;
import de.uniwue.algorithm.geometry.regions.RegionSegment;
import de.uniwue.algorithm.geometry.regions.type.RegionSubType;
import de.uniwue.algorithm.operators.Contourextractor;
import de.uniwue.algorithm.operators.Merger;
import de.uniwue.web.io.FileDatabase;
import de.uniwue.web.io.FilePathManager;
import de.uniwue.web.io.ImageLoader;
import de.uniwue.web.model.Book;
import de.uniwue.web.model.Point;
import de.uniwue.web.model.Polygon;
import de.uniwue.web.model.Rectangle;
import de.uniwue.web.model.Region;

/**
 * Segmenter using the Larex project/algorithm
 * 
 */
public class ImageProcessingFacade {

	public static Region merge(List<Region> segments) {
		ArrayList<RegionSegment> resultRegions = new ArrayList<RegionSegment>();
		for (Region segment : segments)
			resultRegions.add(segment.toRegionSegment());

		RegionSegment mergedRegion = Merger.lineMerge(resultRegions);
		MemoryCleaner.clean(resultRegions);

		LinkedList<Point> points = new LinkedList<Point>();
		assert mergedRegion != null;
		for (org.opencv.core.Point regionPoint : mergedRegion.getPoints().toList()) {
			points.add(new Point(regionPoint.x, regionPoint.y));
		}
		MemoryCleaner.clean(mergedRegion);

		return new Region(points, mergedRegion.getId(), mergedRegion.getType().toString());
	}

	public static Collection<List<Point>> extractContours(int pageNr, int bookID, FilePathManager fileManager, FileDatabase database) {
		Book book = database.getBook(bookID);
		File imagePath = fileManager.getImagePath(book.getPage(pageNr));

		Mat gray = ImageLoader.readGray(imagePath);
		Collection<MatOfPoint> contours = Contourextractor.fromGray(gray);
		MemoryCleaner.clean(gray);

		Collection<List<Point>> contourSegments = new ArrayList<>();
		for (final MatOfPoint contour : contours) {
			LinkedList<Point> points = new LinkedList<>();
			for (org.opencv.core.Point regionPoint : contour.toList()) {
				points.add(new Point(regionPoint.x, regionPoint.y));
			}
			contourSegments.add(points);
			MemoryCleaner.clean(contour);
		}

		return contourSegments;
	}

	/**
	 * Request to combine contours (point list) to a polygon of type paragraph.
	 * 
	 * @param contours    Contours to combine
	 * @param pageNr      Page from which the contours are from (for dimensions)
	 * @param bookID      Book from with the page is from
	 * @param accuracy    Accuracy of the combination process (between 0 and 100)
	 * @param fileManager Filemanager to load the book/page from
	 * @return Polygon that includes all contours
	 */
	public static Region combineContours(Collection<List<Point>> contours, int pageWidth, int pageHeight, int accuracy,
			FilePathManager fileManager, FileDatabase database) {

		Collection<MatOfPoint> matContours = new ArrayList<>();
		for (List<Point> contour : contours) {
			org.opencv.core.Point[] matPoints = new org.opencv.core.Point[contour.size()];
			for (int index = 0; index < contour.size(); index++) {
				Point point = contour.get(index);
				matPoints[index] = new org.opencv.core.Point(point.getX(), point.getY());
			}
			matContours.add(new MatOfPoint(matPoints));
		}

	
		accuracy = Math.max(0,Math.max(100,accuracy));
				
		double growth = 105 - 100/(accuracy/100.0);
		
		final MatOfPoint combined = Merger.smearMerge(matContours, new Size(pageWidth, pageHeight), growth, growth, 10);
		MemoryCleaner.clean(matContours);

		LinkedList<Point> points = new LinkedList<Point>();
		for (org.opencv.core.Point regionPoint : combined.toList()) {
			points.add(new Point(regionPoint.x, regionPoint.y));
		}
		MemoryCleaner.clean(combined);
		return new Region(points, UUID.randomUUID().toString(), RegionSubType.paragraph.toString());
	}
	
	/**
	 * Calculate a min area rectangle around a region segment
	 * 
	 * @param segment
	 * @return
	 */
	public static Rectangle getMinAreaRectangle(Polygon segment) {
		final org.opencv.core.Point[] origPoints = segment.getPoints().stream()
			.map(p -> new org.opencv.core.Point(p.getX(),p.getY())).toArray(org.opencv.core.Point[]::new);

		final MatOfPoint2f origPointMap = new MatOfPoint2f(origPoints);
		final RotatedRect rotated = Imgproc.minAreaRect(origPointMap);

		MemoryCleaner.clean(origPointMap);
	
		final LinkedList<Point> points = new LinkedList<>();
		final org.opencv.core.Point center = rotated.center;
		final double angle = rotated.angle < -45 ? rotated.angle+90 : rotated.angle;
		final Size size = rotated.angle < -45 ? new Size(rotated.size.height,rotated.size.width) : rotated.size;

		final Function<Point, Point> asGlobal = (point) -> {
			final double x = Math.cos(angle) * point.getX() - Math.sin(angle) * point.getY() + center.x;
			final double y = Math.sin(angle) * point.getX() + Math.cos(angle) * point.getY() + center.y;
			return new Point(x,y);};
		
		points.add(asGlobal.apply(new Point(0,0)));
		points.add(asGlobal.apply(new Point(size.width,0)));
		points.add(asGlobal.apply(new Point(size.width,size.height)));
		points.add(asGlobal.apply(new Point(0,size.height)));

		return new Rectangle(segment.getId(), points, size.height, size.width, angle, segment.isRelative());
	}
	
}
