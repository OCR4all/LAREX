package larex.segmentation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.Box.Filler;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import larex.data.MemoryCleaner;
import larex.geometry.positions.RelativePosition;
import larex.geometry.regions.Region;
import larex.geometry.regions.RegionSegment;
import larex.geometry.regions.type.PAGERegionType;
import larex.geometry.regions.type.RegionSubType;
import larex.geometry.regions.type.RegionType;
import larex.imageProcessing.ImageProcessor;
import larex.segmentation.parameters.ImageSegType;
import larex.segmentation.parameters.Parameters;

public class Segmenter2 {

	public static SegmentationResult segment(final Mat original, Parameters parameters) {
		final double scaleFactor = parameters.getScaleFactor(original.height());

		//// Downscale for faster calculation
		// Calculate downscaled binary
		final Mat resized = ImageProcessor.resize(original, parameters.getDesiredImageHeight());
		final Mat gray = ImageProcessor.calcGray(resized);
		MemoryCleaner.clean(resized);
		final Mat binary = ImageProcessor.calcInvertedBinary(gray);
		MemoryCleaner.clean(gray);
		
		// calculate downscaled regions
		Collection<Region> regions = calcTrueRegionSize(resized.size(),parameters.getRegionManager().getRegions());
		/*for (Region region : regions) {
			region.calcPositionRects(binary.size());
		}*/

		// detect images and set fixed regions
		fillFixedSegments(original,binary,parameters,scaleFactor);
		/*Collection<RegionSegment> fixedSegments = parameters.getExistingGeometry().getFixedRegionSegments();
		// fill fixed segments in the image
		List<MatOfPoint> contours = fixedSegments.stream().map(s -> s.getResizedPoints(scaleFactor))
											.collect(Collectors.toList());
		Imgproc.drawContours(binary, contours, -1, new Scalar(0), -1);*/
		
		Collection<MatOfPoint> fixed = processFixedImageRegions(binary, regions);
		

		Region imageRegion = getImageRegion(regions);	
		//Region imageRegion = regions.stream().filter((r) -> r.getType().getType().equals(RegionType.ImageRegion)).findFirst().get();

		//Collection<MatOfPoint> images = detectImages(binary, imageRegion, parameters.getImageSegType(), parameters, scaleFactor);
		Collection<MatOfPoint> images = detectImages(binary,imageRegion, parameters.getImageSegType(),parameters,scaleFactor);
		images.addAll(fixed);
		


		// detect and classify text regions
		Collection<MatOfPoint> texts = detectText(binary, regions, parameters, scaleFactor);
		Collection<RegionSegment> results = classifyText(texts,regions);
		/*MemoryCleaner.clean(binary);
		// classify
		RegionClassifier regionClassifier = new RegionClassifier(regions);
		Collection<RegionSegment> results = regionClassifier.classifyRegions(texts);
		*/
		for (final MatOfPoint image : images) {
			RegionSegment result = new RegionSegment(new PAGERegionType(RegionType.ImageRegion), image);
			results.add(result);
		}
		

		//// Upscale for final result
		// Apply scale correction
		Collection<RegionSegment> scaled = new ArrayList<>();
		for (RegionSegment result : results) {
			scaled.add(result.getResized(1.0 / parameters.getScaleFactor(original.height())));
			MemoryCleaner.clean(result);
		}
		results = scaled;

		// Add fixed segments
		for (RegionSegment segment : parameters.getExistingGeometry().getFixedRegionSegments()) {
			results.add(new RegionSegment(segment.getType(), segment.getPoints(), segment.getId()));
		}

		// Set result
		SegmentationResult segResult = new SegmentationResult(results);
		segResult.removeImagesWithinText();

		return segResult;
	}
	private static Collection<RegionSegment> classifyText(Collection<MatOfPoint> texts, Collection<Region> regions) {
		RegionClassifier regionClassifier = new RegionClassifier(regions);
		Collection<RegionSegment> classifiedRegions = regionClassifier.classifyRegions(texts);

		return classifiedRegions;
}

	private static Collection<Region> calcTrueRegionSize(Size image, ArrayList<Region> regions) {
		for (Region region : regions) {
			region.calcPositionRects(image);
		}

		return new ArrayList<Region>(regions);
}
	private static Collection<MatOfPoint> detectText(final Mat binary, Collection<Region> regions, Parameters parameters, double scaleFactor) {
		Mat dilate = new Mat();

		if (parameters.getTextDilationX() == 0 || parameters.getTextDilationY() == 0) {
			dilate = binary.clone();
		} else {
			dilate = ImageProcessor.dilate(binary,
					new Size(parameters.getTextDilationX(), parameters.getTextDilationY()));
		}

		// draw user defined lines
		dilate = parameters.getExistingGeometry().drawIntoImage(dilate, scaleFactor);

		int minSize = Integer.MAX_VALUE;

		for (Region region : regions) {
			if (region.getMinSize() < minSize) {
				minSize = region.getMinSize();
			}
		}

		ArrayList<MatOfPoint> texts = ImageSegmentation.detectTextContours(dilate, minSize);
		MemoryCleaner.clean(dilate);

		return texts;
	}

	private static Collection<MatOfPoint> detectImages(Mat binary, Region imageRegion, ImageSegType type, Parameters parameters, double scaleFactor) {
		if (type.equals(ImageSegType.NONE)) {
			return new ArrayList<MatOfPoint>();
		}

		Mat dilate = null;

		if (parameters.getImageRemovalDilationX() == 0 || parameters.getImageRemovalDilationY() == 0) {
			dilate = binary.clone();
		} else {
			dilate = ImageProcessor.dilate(binary,
					new Size(parameters.getImageRemovalDilationX(), parameters.getImageRemovalDilationY()));
		}

		// draw user defined lines
		dilate = parameters.getExistingGeometry().drawIntoImage(dilate, scaleFactor);

		ArrayList<MatOfPoint> images = ImageSegmentation.detectImageContours(dilate, imageRegion.getMinSize(), type,
				parameters.isCombineImages());

		for (final MatOfPoint tempContour : images) {
			Imgproc.fillConvexPoly(binary, tempContour, new Scalar(0));
		}

		MemoryCleaner.clean(dilate);

		return images;
	}

		private static Region getImageRegion(Collection<Region> regions) {
			for (Region region : regions) {
				if ((region.getType().getType().equals(RegionType.ImageRegion))) {
					return region;
				}
			}

			return null;
		}

	// TODO: remove redundancy
	private static Collection<MatOfPoint> processFixedImageRegions(Mat binary, Collection<Region> regions) {
		ArrayList<MatOfPoint> fixed = new ArrayList<MatOfPoint>();

		for (Region region : regions) {
			if ((region.getType().getType().equals(RegionType.ImageRegion) ||
					RegionSubType.ignore.equals(region.getType().getSubtype()))) {
				for (RelativePosition position : region.getPositions()) {
					if (position.isFixed()) {
						Rect rect = position.getOpenCVRect();
						Imgproc.rectangle(binary, rect.tl(), rect.br(), new Scalar(0), -1);

						if (region.getType().getType().equals(RegionType.ImageRegion)) {
							Point[] points = { rect.tl(), new Point(rect.br().x, rect.tl().y), rect.br(),
									new Point(rect.tl().x, rect.br().y) };
							fixed.add(new MatOfPoint(points));
						}
					}
				}
			}
		}

		return fixed;
	}
	private static void fillFixedSegments(Mat original, Mat binary, Parameters parameters,double scaleFactor) {
		ArrayList<RegionSegment> fixedSegments = parameters.getExistingGeometry().getFixedRegionSegments();
		ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();

		// Add fixed segments
		for (RegionSegment segment : fixedSegments) {
			contours.add(segment.getResizedPoints(scaleFactor));
		}

		Imgproc.drawContours(binary, contours, -1, new Scalar(0), -1);
}
}