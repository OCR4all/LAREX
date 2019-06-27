package larex.segmentation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import larex.data.MemoryCleaner;
import larex.geometry.ExistingGeometry;
import larex.geometry.regions.Region;
import larex.geometry.regions.RegionSegment;
import larex.geometry.regions.type.PAGERegionType;
import larex.geometry.regions.type.RegionType;
import larex.imageProcessing.ImageProcessor;
import larex.segmentation.parameters.ImageSegType;
import larex.segmentation.parameters.Parameters;

public class Segmenter {

	/**
	 * Segment an image based on presented parameters
	 * 
	 * @param original Image to segment
	 * @param parameters Parameters to use for the segmentation
	 * @return Segmentation of the document image
	 */
	public static Collection<RegionSegment> segment(final Mat original, Parameters parameters) {
		final double scaleFactor = parameters.getScaleFactor(original.height());
		final Collection<RegionSegment> fixedSegments = parameters.getExistingGeometry().getFixedRegionSegments();
		final ExistingGeometry existingGeometry = parameters.getExistingGeometry();

		//// Downscale for faster calculation
		// Calculate downscaled binary
		final Mat resized = ImageProcessor.resize(original, parameters.getDesiredImageHeight());
		final Mat gray = ImageProcessor.calcGray(resized);
		MemoryCleaner.clean(resized);
		final Mat binary = ImageProcessor.calcInvertedBinary(gray);
		MemoryCleaner.clean(gray);
		
		// calculate downscaled regions
		final Set<Region> regions = parameters.getRegionManager().getRegions();
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
		Collection<RegionSegment> results = new ArrayList<RegionSegment>();
		// detect images 
		Region imageRegion = regions.stream().filter((r) -> r.getType().getType().equals(RegionType.ImageRegion)).findFirst().get();
		Collection<MatOfPoint> images = detectImages(binary, imageRegion, parameters.getImageSegType(), existingGeometry,
				parameters.getImageRemovalDilationX(), parameters.getImageRemovalDilationY(), scaleFactor, parameters.isCombineImages());
		for (final MatOfPoint image : images) {
			RegionSegment result = new RegionSegment(new PAGERegionType(RegionType.ImageRegion), image);
			results.add(result);
			// Fill image regions in to remove them from further detections
			Imgproc.fillConvexPoly(binary, image, new Scalar(0));
		}

		// detect and classify text regions
		Collection<MatOfPoint> texts = detectText(binary, regions, existingGeometry, parameters.getTextDilationX(),
				parameters.getTextDilationY(), scaleFactor);
		MemoryCleaner.clean(binary);
		// classify
		results.addAll(RegionClassifier.classifyRegions(regions, texts));
		//MemoryCleaner.clean(texts);
		

		//// Create final result
		// Apply scale correction
		ArrayList<RegionSegment> scaled = new ArrayList<>();
		for (RegionSegment result : results) {
			scaled.add(result.getResized(1.0 / parameters.getScaleFactor(original.height())));
			//MemoryCleaner.clean(result);
		}
		results = scaled;

		// Add fixed segments
		for (RegionSegment segment : fixedSegments) {
			results.add(new RegionSegment(segment.getType(), segment.getPoints(), segment.getId()));
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
													int textdilationX, int textdilationY, double scaleFactor) {
		Mat dilate = new Mat();

		if (textdilationX == 0 || textdilationY == 0) {
			dilate = binary.clone();
		} else {
			dilate = ImageProcessor.dilate(binary, new Size(textdilationX, textdilationY));
		}

		// draw user defined lines
		final Mat workImage = existingGeometry.drawIntoImage(dilate, scaleFactor);
		MemoryCleaner.clean(dilate);

		int minSize = Integer.MAX_VALUE;

		for (Region region : regions) {
			if (region.getMinSize() < minSize) {
				minSize = region.getMinSize();
			}
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
	private static Collection<MatOfPoint> detectImages(Mat binary, Region imageRegion, ImageSegType type, ExistingGeometry existingGeometry,
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

		Collection<MatOfPoint> images = ImageSegmentation.detectImageContours(workImage, imageRegion.getMinSize(), type,
				combineImages);

		MemoryCleaner.clean(workImage);

		return images;
	}
}
