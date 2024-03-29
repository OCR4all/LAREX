package de.uniwue.algorithm.segmentation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import de.uniwue.algorithm.data.MemoryCleaner;
import de.uniwue.algorithm.geometry.ExistingGeometry;
import de.uniwue.algorithm.geometry.regions.Region;
import de.uniwue.algorithm.geometry.regions.RegionSegment;
import de.uniwue.algorithm.geometry.regions.type.PAGERegionType;
import de.uniwue.algorithm.geometry.regions.type.RegionSubType;
import de.uniwue.algorithm.geometry.regions.type.RegionType;
import de.uniwue.algorithm.imageProcessing.ImageProcessor;
import de.uniwue.algorithm.segmentation.parameters.ImageSegType;
import de.uniwue.algorithm.segmentation.parameters.Parameters;

public class Segmenter {

	/**
	 * Segment an image based on presented parameters
	 *
	 * @param original Image to segment
	 * @param parameters Parameters to use for the segmentation
	 * @return Segmentation of the document image
	 */
	public static Collection<RegionSegment> segment(final Mat original, Parameters parameters, double orientation) {
		Mat rotatedMat = original.clone();
		ExistingGeometry rotatedGeometry = parameters.getExistingGeometry();
		if(orientation != 0.0) {
			rotatedMat = rotateMat(original, orientation * -1.0);
			rotateExistingGeometry(rotatedGeometry, original.size(), rotatedMat.size(), orientation * 1.0);
		}
		final double scaleFactor = parameters.getScaleFactor(rotatedMat.height());

		//// Downscale for faster calculation
		// Calculate downscaled binary
		final Mat resized = ImageProcessor.resize(rotatedMat, parameters.getDesiredImageHeight());
		final Mat gray = ImageProcessor.calcGray(resized);
		MemoryCleaner.clean(resized);
		final Mat binary = ImageProcessor.calcInvertedBinary(gray);
		MemoryCleaner.clean(gray);

		//// Preprocess
		// fill fixed segments in the image
		Size matSize = rotatedMat.size();
		final List<MatOfPoint> contours = rotatedGeometry.getFixedRegionSegments().stream().map(s -> s.getResizedPoints(scaleFactor, matSize))
				.collect(Collectors.toList());
		Imgproc.drawContours(binary, contours, -1, new Scalar(0), -1);
		MemoryCleaner.clean(contours);

		// regions
		final Set<Region> regions = parameters.getRegionManager().getRegions();
		Region ignoreRegion = null;
		for (Region region : regions) {
			if(RegionSubType.ignore == region.getType().getSubtype()) {
				ignoreRegion = region;
			}
		}
		// fill ignore region in the image
		if(ignoreRegion != null) {
			ignoreRegion.getPositions().stream().map(p -> p.getRect(binary.size()))
					.forEach(r -> Imgproc.rectangle(binary, r.tl(), r.br(), new Scalar(0), -1));
		}

		//// Detection
		Collection<RegionSegment> results = new ArrayList<RegionSegment>();
		// detect images
		Region imageRegion = regions.stream().filter((r) -> r.getType().getType().equals(RegionType.ImageRegion)).findFirst().get();
		Collection<MatOfPoint> images = detectImages(binary, imageRegion, ignoreRegion, parameters.getImageSegType(), rotatedGeometry,
				parameters.getImageRemovalDilationX(), parameters.getImageRemovalDilationY(), scaleFactor, parameters.isCombineImages());
		for (final MatOfPoint image : images) {
			RegionSegment result = new RegionSegment(new PAGERegionType(RegionType.ImageRegion), image);
			results.add(result);
			// Fill image regions in to remove them from further detections
			Imgproc.fillConvexPoly(binary, image, new Scalar(0));
		}

		// detect and classify text regions
		Collection<MatOfPoint> texts = detectText(binary, regions, rotatedGeometry, parameters.getTextDilationX(),
				parameters.getTextDilationY(), scaleFactor, images);

		// classify
		results.addAll(RegionClassifier.classifyRegions(regions, texts, binary.size()));
		MemoryCleaner.clean(binary);


		//// Create final result
		// Apply scale correction
		ArrayList<RegionSegment> scaled = new ArrayList<>();
		for (RegionSegment result : results) {
			scaled.add(result.getResized(1.0 / parameters.getScaleFactor(rotatedMat.height()), rotatedMat.size()));
			MemoryCleaner.clean(result);
		}
		results = scaled;

		// Add fixed segments
		for (RegionSegment segment : rotatedGeometry.getFixedRegionSegments()) {
			results.add(new RegionSegment(segment.getType(), segment.getPoints()));
		}

		// Filter images that are inside text
		List<RegionSegment> imageList = results.stream().filter(
				r -> r.getType().getType().equals(RegionType.ImageRegion)).collect(Collectors.toList());

		for (RegionSegment image : imageList) {
			Rect imageRect = Imgproc.boundingRect(image.getPoints());

			for (RegionSegment region : results) {
				final MatOfPoint2f contour2f = new MatOfPoint2f(region.getPoints().toArray());

				if (Imgproc.pointPolygonTest(contour2f, imageRect.tl(), false) > 0
						&& Imgproc.pointPolygonTest(contour2f, imageRect.br(), false) > 0) {
					results.remove(image);
					break;
				}
			}
		}
		if(orientation != 0.0) { results = rotateRegions(original.size(), rotatedMat.size(), results, orientation * -1.0, false); }

		return results;
	}

	/**
	 * Detect text regions in the image binary
	 *
	 * @param binary
	 * @param regions
	 * @param existingGeometry
	 * @param textdilationX
	 * @param textdilationY
	 * @param scaleFactor
	 * @return
	 */
	private static Collection<MatOfPoint> detectText(final Mat binary, Collection<Region> regions, ExistingGeometry existingGeometry,
													 int textdilationX, int textdilationY, double scaleFactor, Collection<MatOfPoint> images) {
		Mat dilate = new Mat();

		if (textdilationX == 0 || textdilationY == 0) {
			dilate = binary.clone();
		} else {
			dilate = ImageProcessor.dilate(binary, new Size(textdilationX, textdilationY));
		}

		for (final MatOfPoint image : images) {
			Imgproc.fillConvexPoly(dilate, image, new Scalar(0));
		}

		// draw user defined lines
		final Mat workImage = existingGeometry.drawIntoImage(dilate, scaleFactor);
		MemoryCleaner.clean(dilate);

		int minSize = Integer.MAX_VALUE;

		Region ignoreRegion = null;
		for (Region region : regions) {
			if (region.getMinSize() < minSize) {
				minSize = region.getMinSize();
			}
			if(RegionSubType.ignore == region.getType().getSubtype()) {
				ignoreRegion = region;
			}
		}
		// fill ignore in the image
		if(ignoreRegion != null) {
			ignoreRegion.getPositions().stream().map(p -> p.getRect(workImage.size()))
					.forEach(r -> Imgproc.rectangle(workImage, r.tl(), r.br(), new Scalar(0), -1));
		}

		Collection<MatOfPoint> texts = ImageSegmentation.detectTextContours(workImage, minSize);
		MemoryCleaner.clean(workImage);

		return texts;
	}

	/**
	 * Detect image regions in the image binary
	 *
	 * @param binary
	 * @param imageRegion
	 * @param type
	 * @param existingGeometry
	 * @param imageRemovalDilationX
	 * @param imageRemovalDilationY
	 * @param scaleFactor
	 * @param combineImages
	 * @return
	 */
	private static Collection<MatOfPoint> detectImages(Mat binary, Region imageRegion, Region ignoreRegion,ImageSegType type, ExistingGeometry existingGeometry,
													   int imageRemovalDilationX, int imageRemovalDilationY, double scaleFactor, boolean combineImages) {
		if (type.equals(ImageSegType.NONE)) {
			return new ArrayList<MatOfPoint>();
		}

		Mat dilate = null;

		if (imageRemovalDilationX == 0 || imageRemovalDilationY == 0) {
			dilate = binary.clone();
		} else {
			dilate = ImageProcessor.dilate(binary, new Size(imageRemovalDilationX, imageRemovalDilationY));
		}

		// draw user defined lines
		final Mat workImage = existingGeometry.drawIntoImage(dilate, scaleFactor);
		MemoryCleaner.clean(dilate);
		// fill ignore in the image
		if(ignoreRegion != null) {
			ignoreRegion.getPositions().stream().map(p -> p.getRect(workImage.size()))
					.forEach(r -> Imgproc.rectangle(workImage, r.tl(), r.br(), new Scalar(0), -1));
		}

		Collection<MatOfPoint> images = ImageSegmentation.detectImageContours(workImage, imageRegion.getMinSize(), type,
				combineImages);

		MemoryCleaner.clean(workImage);

		return images;
	}

	/**
	 * rotates OpenCV.Mat without cropping
	 *
	 * @param mat openCV imageMat to rotate
	 * @param angle angle in degrees
	 * @return rotate openCV Mat
	 */
	public static Mat rotateMat(Mat mat, double angle) {
		int width = mat.width();
		int height = mat.height();
		Point mat_center = new Point(width/2.0,height/2.0);

		// returns an affine transformation matrix(2X3)
		Mat rotationMat = Imgproc.getRotationMatrix2D(mat_center, angle, 1.0);

		// sin and cos are part of the rotationMatrix
		double sin = rotationMat.get(0,1)[0];
		double cos = rotationMat.get(0,0)[0];

		// calculate bounds of rotated mat
		double bound_w = (height * Math.abs(sin) + width * Math.abs(cos));
		double bound_h = (height * Math.abs(cos) + width * Math.abs(sin));

		// calculate and set offset center
		double newCenter_x = rotationMat.get(0,2)[0] + ((bound_w / 2.0) - mat_center.x);
		double newCenter_y = rotationMat.get(1,2)[0] + ((bound_h / 2.0) - mat_center.y);
		rotationMat.put(0,2, newCenter_x);
		rotationMat.put(1,2, newCenter_y);

		Size rm_size = new Size(bound_w,bound_h);
		Mat rotatedMat = new Mat();
		// INTER_LINEAR, BORDER_CONSTANT and Scalar are used for painting background
		// It is possible that this results in false image detection on very dark pages
		// TODO: Maybe determine mean(or median?) imageMat color and use that as background?
		Imgproc.warpAffine(mat, rotatedMat, rotationMat, rm_size, Imgproc.INTER_LINEAR, org.opencv.core.Core.BORDER_CONSTANT, new Scalar(255, 255, 255));

		return rotatedMat;
	}

	/**
	 * Rotates each RegionSegment in given Collection
	 *
	 * @param originalSize	size of original Mat
	 * @param rotatedSize	size of rotated Mat
	 * @param regions 		RegionSegments
	 * @param angle 		negative orientation
	 * @param addOffset true: add offset if true, substract if false
	 * @return
	 */
	private static Collection<RegionSegment> rotateRegions (Size originalSize, Size rotatedSize, Collection<RegionSegment> regions, double angle, boolean addOffset) {
		double width = rotatedSize.width;
		double height = rotatedSize.height;

		Point mat_center = new Point(width/2.0,height/2.0);

		double radians = Math.toRadians(angle);
		double sin = Math.sin(radians);
		double cos = Math.cos(radians);

		//calculating offset
		double offset_x = ((width - originalSize.width) / 2.0);
		double offset_y = ((height - originalSize.height) / 2.0);

		if(addOffset) {
			mat_center.x = originalSize.width / 2.0;
			mat_center.y = originalSize.height / 2.0;
			offset_x = rotatedSize.width / 2.0 - mat_center.x;
			offset_y = rotatedSize.height / 2.0 - mat_center.y;
		}

		Collection<RegionSegment> rotatedSegments = new ArrayList<RegionSegment>();
		for(RegionSegment regionSegment : regions) {
			RegionSegment segment = regionSegment;

			MatOfPoint points = regionSegment.getPoints();
			MatOfPoint rotatedPoints = new MatOfPoint();

			List<Point> rotatedPointsList = new ArrayList<>();
			for(Point point : points.toList()) {
				double rotatedPoint_x = ((point.x - mat_center.x) * cos) - ((point.y - mat_center.y) * sin) + mat_center.x;
				double rotatePoint_y = ((point.x - mat_center.x) * sin) + ((point.y - mat_center.y) * cos) + mat_center.y;
				if(addOffset) {
					rotatedPointsList.add(new Point(rotatedPoint_x + offset_x, rotatePoint_y + offset_y));
				} else {
					rotatedPointsList.add(new Point(rotatedPoint_x - offset_x, rotatePoint_y - offset_y));
				}
			}
			rotatedPoints.fromList(rotatedPointsList);
			regionSegment.setPoints(rotatedPoints);
			rotatedSegments.add(regionSegment);
		}
		return rotatedSegments;
	}

	public static void rotateExistingGeometry(ExistingGeometry existingGeometry, Size originalSize, Size targetSize, double orientation) {
		rotateRegions(originalSize, targetSize, existingGeometry.getFixedRegionSegments(), orientation * 1.0, true);
	}
}
