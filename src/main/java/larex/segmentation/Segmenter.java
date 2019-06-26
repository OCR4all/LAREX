package larex.segmentation;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import larex.data.MemoryCleaner;
import larex.geometry.regions.Region;
import larex.geometry.regions.RegionSegment;
import larex.geometry.regions.type.PAGERegionType;
import larex.geometry.regions.type.RegionType;
import larex.imageProcessing.ImageProcessor;
import larex.segmentation.parameters.ImageSegType;
import larex.segmentation.parameters.Parameters;

public class Segmenter {

	public static SegmentationResult segment(final Mat original, Parameters parameters) {
		final double scaleFactor = parameters.getScaleFactor(original.height());
		final ArrayList<RegionSegment> fixedSegments = parameters.getExistingGeometry().getFixedRegionSegments();

		//// Downscale for faster calculation
		// Calculate downscaled binary
		final Mat resized = ImageProcessor.resize(original, parameters.getDesiredImageHeight());
		final Mat gray = ImageProcessor.calcGray(resized);
		MemoryCleaner.clean(resized);
		final Mat binary = ImageProcessor.calcInvertedBinary(gray);
		MemoryCleaner.clean(gray);
		
		// calculate downscaled regions
		final ArrayList<Region> regions = parameters.getRegionManager().getRegions();
		for (Region region : regions) {
			region.calcPositionRects(binary.size());
		}

		//// Preprocess
		// fill fixed segments in the image
		final List<MatOfPoint> contours = fixedSegments.stream().map(s -> s.getResizedPoints(scaleFactor))
											.collect(Collectors.toList());
		Imgproc.drawContours(binary, contours, -1, new Scalar(0), -1);
		MemoryCleaner.clean(contours);
		
		
		//// Detection
		ArrayList<RegionSegment> results = new ArrayList<RegionSegment>();
		// detect images 
		Region imageRegion = regions.stream().filter((r) -> r.getType().getType().equals(RegionType.ImageRegion)).findFirst().get();
		ArrayList<MatOfPoint> images = detectImages(binary, imageRegion, parameters.getImageSegType(), parameters, scaleFactor);
		for (final MatOfPoint image : images) {
			RegionSegment result = new RegionSegment(new PAGERegionType(RegionType.ImageRegion), image);
			results.add(result);
		}

		// detect and classify text regions
		ArrayList<MatOfPoint> texts = detectText(binary, regions, parameters, scaleFactor);
		MemoryCleaner.clean(binary);
		// classify
		RegionClassifier regionClassifier = new RegionClassifier(regions);
		results.addAll(regionClassifier.classifyRegions(texts));
		

		//// Create final result
		// Apply scale correction
		ArrayList<RegionSegment> scaled = new ArrayList<>();
		for (RegionSegment result : results) {
			scaled.add(result.getResized(1.0 / parameters.getScaleFactor(original.height())));
			MemoryCleaner.clean(result);
		}
		results = scaled;

		// Add fixed segments
		for (RegionSegment segment : fixedSegments) {
			results.add(new RegionSegment(segment.getType(), segment.getPoints(), segment.getId()));
		}

		// Set result
		SegmentationResult segResult = new SegmentationResult(results);
		segResult.removeImagesWithinText();

		return segResult;
	}

	private static ArrayList<MatOfPoint> detectText(final Mat binary, ArrayList<Region> regions, Parameters parameters, double scaleFactor) {
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

	private static ArrayList<MatOfPoint> detectImages(Mat binary, Region imageRegion, ImageSegType type, Parameters parameters, double scaleFactor) {
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
}
