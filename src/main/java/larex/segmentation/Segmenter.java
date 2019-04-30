package larex.segmentation;

import java.util.ArrayList;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import larex.geometry.positions.RelativePosition;
import larex.geometry.regions.Region;
import larex.geometry.regions.RegionSegment;
import larex.geometry.regions.type.PAGERegionType;
import larex.geometry.regions.type.RegionSubType;
import larex.geometry.regions.type.RegionType;
import larex.imageProcessing.ImageProcessor;
import larex.segmentation.parameters.ImageSegType;
import larex.segmentation.parameters.Parameters;

public class Segmenter {

	private Parameters parameters;
	private ArrayList<Region> regions;

	private Mat binary;
	private double scaleFactor;

	public Segmenter(Parameters parameters) {
		setParameters(parameters);
	}

	public SegmentationResult segment(Mat original) {
		// initialize image and regions
		init(original, parameters);

		// handle fixed regions and detect images
		Region imageRegion = getImageRegion();
		Region ignoreRegion = getIgnoreRegion();

		fillFixedSegments(original);

		ArrayList<MatOfPoint> fixed = processFixedRegions(imageRegion, ignoreRegion);
		ArrayList<MatOfPoint> images = detectImages(imageRegion, parameters.getImageSegType());
		images.addAll(fixed);

		// detect and classify text regionss
		ArrayList<MatOfPoint> texts = detectText();
		ArrayList<RegionSegment> results = classifyText(texts);

		for (MatOfPoint image : images) {
			RegionSegment result = new RegionSegment(new PAGERegionType(RegionType.ImageRegion), image);
			results.add(result);
		}

		// Apply scale correction
		ArrayList<RegionSegment> scaled = new ArrayList<>();
		for (RegionSegment result : results) {
			scaled.add(result.getResized(1.0 / parameters.getScaleFactor(original.height())));
		}
		results = scaled;

		// Add fixed segments
		ArrayList<RegionSegment> fixedSegments = parameters.getExistingGeometry().getFixedRegionSegments();
		for (RegionSegment segment : fixedSegments) {
			results.add(new RegionSegment(segment.getType(), segment.getPoints(), segment.getId()));
		}

		// Set result
		SegmentationResult segResult = new SegmentationResult(results);
		segResult.removeImagesWithinText();

		return segResult;
	}

	private void fillFixedSegments(Mat original) {
		ArrayList<RegionSegment> fixedSegments = parameters.getExistingGeometry().getFixedRegionSegments();
		ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();

		// Add fixed segments
		for (RegionSegment segment : fixedSegments) {
			contours.add(segment.getResizedPoints(this.scaleFactor));
		}

		Imgproc.drawContours(binary, contours, -1, new Scalar(0), -1);
	}

	private ArrayList<RegionSegment> classifyText(ArrayList<MatOfPoint> texts) {
		RegionClassifier regionClassifier = new RegionClassifier(regions);
		ArrayList<RegionSegment> classifiedRegions = regionClassifier.classifyRegions(texts);

		return classifiedRegions;
	}

	private ArrayList<MatOfPoint> detectText() {
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

		return texts;
	}

	private ArrayList<MatOfPoint> detectImages(Region imageRegion, ImageSegType type) {
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

		for (MatOfPoint tempContour : images) {
			Imgproc.fillConvexPoly(binary, tempContour, new Scalar(0));
		}

		return images;
	}

	// TODO: remove redundancy
	private ArrayList<MatOfPoint> processFixedRegions(Region imageRegion, Region ignoreRegion) {
		ArrayList<MatOfPoint> fixed = new ArrayList<MatOfPoint>();

		for (Region region : regions) {
			if ((region.getType().getType().equals(RegionType.ImageRegion) ||
					RegionSubType.ignore.equals(region.getType().getSubtype()))) {
				for (RelativePosition position : region.getPositions()) {
					if (position.isFixed()) {
						Rect rect = position.getOpenCVRect();
						Mat removed = binary.clone();
						Imgproc.rectangle(removed, rect.tl(), rect.br(), new Scalar(0), -1);
						this.binary = removed;

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

	private Region getIgnoreRegion() {
		for (Region region : regions) {
			if (RegionSubType.ignore.equals(region.getType().getSubtype())) {
				return region;
			}
		}

		return null;
	}

	private Region getImageRegion() {
		for (Region region : regions) {
			if ((region.getType().getType().equals(RegionType.ImageRegion))) {
				return region;
			}
		}

		return null;
	}

	private void calcTrueRegionSize(Mat image, ArrayList<Region> regions) {
		for (Region region : regions) {
			region.calcPositionRects(image);
		}

		this.regions = new ArrayList<Region>(regions);
	}

	private void init(Mat original, Parameters parameters) {
		this.scaleFactor = parameters.getScaleFactor(original.height());
		Mat resized = ImageProcessor.resize(original, parameters.getDesiredImageHeight());
		Mat gray = ImageProcessor.calcGray(resized);
		// calculate region size
		calcTrueRegionSize(resized, parameters.getRegionManager().getRegions());

		// binarize
		int binaryThresh = parameters.getBinaryThresh();
		Mat binary = new Mat();

		if (binaryThresh == -1) {
			binary = ImageProcessor.calcBinary(gray);
		} else {
			binary = ImageProcessor.calcBinaryFromThresh(gray, binaryThresh);
		}

		binary = ImageProcessor.invertImage(binary);
		this.binary = binary;
	}

	public void setParameters(Parameters parameters) {
		this.parameters = parameters;
	}

}