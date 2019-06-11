package com.web.facade;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import com.web.controller.FileManager;
import com.web.model.Book;
import com.web.model.Point;
import com.web.model.Polygon;
import com.web.model.Rectangle;
import com.web.model.Region;
import com.web.model.database.FileDatabase;

import larex.geometry.regions.RegionSegment;
import larex.geometry.regions.type.RegionSubType;
import larex.operators.Contourextractor;
import larex.operators.Merger;

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
		System.gc();

		return new Region(mergedRegion);
	}

	public static Collection<List<Point>> extractContours(int pageNr, int bookID, FileManager fileManager, FileDatabase database) {
		Book book = database.getBook(bookID);
		larex.data.Page page = LarexFacade.getLarexPage(book.getPage(pageNr), fileManager);
		page.initPage();

		Collection<MatOfPoint> contours = Contourextractor.fromSource(page.getOriginal());
		page.clean();
		System.gc();

		Collection<List<Point>> contourSegments = new ArrayList<>();
		for (MatOfPoint contour : contours) {
			LinkedList<Point> points = new LinkedList<>();
			for (org.opencv.core.Point regionPoint : contour.toList()) {
				points.add(new Point(regionPoint.x, regionPoint.y));
			}
			contourSegments.add(points);
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
	public static Region combineContours(Collection<List<Point>> contours, int pageNr, int bookID, int accuracy,
			FileManager fileManager, FileDatabase database) {
		Book book = database.getBook(bookID);
		larex.data.Page page = LarexFacade.getLarexPage(book.getPage(pageNr), fileManager);
		page.initPage();

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
		
		MatOfPoint combined = Merger.smearMerge(matContours, page.getBinary(), growth, growth,10);
		page.clean();
		System.gc();

		return new Region(combined, UUID.randomUUID().toString(), RegionSubType.paragraph.toString());
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

		final RotatedRect rotated = Imgproc.minAreaRect(new MatOfPoint2f(origPoints));
	
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
