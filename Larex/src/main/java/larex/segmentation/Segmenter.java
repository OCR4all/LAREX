package larex.segmentation;

import larex.geometry.PointList;
import larex.imageProcessing.ImageContainerOLD;
import larex.imageProcessing.Rectangle;

import java.util.ArrayList;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import larex.positions.Position;
import larex.regions.Region;
import larex.regions.type.RegionType;
import larex.segmentation.parameters.ImageSegType;
import larex.segmentation.parameters.Parameters;
import larex.segmentation.result.ResultRegion;
import larex.segmentation.result.SegmentationResult;

public class Segmenter {

	private Parameters parameters;
	private ArrayList<Region> regions;

	private Mat binary;

	public Segmenter(Parameters parameters) {
		setParameters(parameters);
	}

	public SegmentationResult segment(Mat original) {
		// initialize image and regions
		init(original, parameters);

		// handle fixed regions and detect images
		// ?????????????????????????????????
		Region imageRegion = getImageRegion();
		Region ignoreRegion = getIgnoreRegion();

		fillFixedPointsLists(original);

		ArrayList<MatOfPoint> fixed = processFixedRegions(imageRegion, ignoreRegion);
		ArrayList<MatOfPoint> images = detectImages(imageRegion, parameters.getImageSegType());
		images.addAll(fixed);

		// detect and classify text regionss
		ArrayList<MatOfPoint> texts = detectText();
		ArrayList<ResultRegion> results = classifyText(texts);

		for (MatOfPoint image : images) {
			ResultRegion result = new ResultRegion(RegionType.image, parameters.getDesiredImageHeight(), image);
			results.add(result);
		}

		addFixedPointsLists(results);
		double scaleFactor = (double) parameters.getDesiredImageHeight()/(double) original.height();
		rescaleDingens(results, scaleFactor);
		SegmentationResult segResult = new SegmentationResult(results, parameters.getDesiredImageHeight(), scaleFactor);
		segResult.removeImagesWithinText();

		return segResult;
	}

	private void rescaleDingens(ArrayList<ResultRegion> results, double scaleFactor) {
		for (ResultRegion result : results) {
			result.rescale(scaleFactor);
		}
	}

	private void addFixedPointsLists(ArrayList<ResultRegion> results) {
		ArrayList<PointList> pointsLists = parameters.getRegionManager().getPointListManager().getPointLists();

		for (PointList pointList : pointsLists) {
			if (pointList.isClosed()) {
				parameters.getRegionManager();
				ResultRegion result = new ResultRegion(pointList.getType(), parameters.getDesiredImageHeight(),
						pointList.getOcvPoints());
				results.add(result);
			}
		}
	}

	private void fillFixedPointsLists(Mat original) {
		ArrayList<PointList> pointsLists = parameters.getRegionManager().getPointListManager().getPointLists();
		int verticalRes = original.height();//parameters.getRegionManager().getPointListManager().getVerticalResolution();
		ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();

		for (PointList pointList : pointsLists) {
			if (pointList.isClosed()) {
				contours.add(pointList.calcMatOfPoint(verticalRes, parameters.getDesiredImageHeight()));
			}
		}

		Imgproc.drawContours(binary, contours, -1, new Scalar(0), -1);
	}

	public ArrayList<ResultRegion> classifyText(ArrayList<MatOfPoint> texts) {
		RegionClassifier regionClassifier = new RegionClassifier(binary, regions);
		ArrayList<ResultRegion> classifiedRegions = regionClassifier.classifyRegions(texts);

		return classifiedRegions;
	}

	public ArrayList<MatOfPoint> detectText() {
//		binary = ImageProcessor.dilate(binary, new Size(parameters.getTextDilationX(), parameters.getTextDilationY()));
//
//		// draw user defined lines
//		binary = parameters.getRegionManager().getPointListManager().drawPointListIntoImage(binary,
//				parameters.getScaleFactor());
		
		Mat dilate = new Mat();

		if (parameters.getTextDilationX() == 0 || parameters.getTextDilationY() == 0) {
			dilate = binary.clone();
		} else {
			dilate = ImageProcessor.dilate(binary,
					new Size(parameters.getTextDilationX(), parameters.getTextDilationY()));
		}

		// draw user defined lines
		dilate = parameters.getRegionManager().getPointListManager().drawPointListIntoImage(dilate,
				parameters.getScaleFactor());

		int minSize = Integer.MAX_VALUE;

		for (Region region : regions) {
			if (region.getMinSize() < minSize) {
				minSize = region.getMinSize();
			}
		}

		ArrayList<MatOfPoint> texts = ImageSegmentation.detectTextContours(dilate, minSize);

		return texts;
	}

	public ArrayList<MatOfPoint> detectImages(Region imageRegion, ImageSegType type) {
		if (type.equals(ImageSegType.NONE)) {
			return new ArrayList<MatOfPoint>();
		}

		Mat dilate = new Mat();

		if (parameters.getImageRemovalDilationX() == 0 || parameters.getImageRemovalDilationY() == 0) {
			dilate = binary.clone();
		} else {
			dilate = ImageProcessor.dilate(binary,
					new Size(parameters.getImageRemovalDilationX(), parameters.getImageRemovalDilationY()));
		}

		// draw user defined lines
		dilate = parameters.getRegionManager().getPointListManager().drawPointListIntoImage(dilate,
				parameters.getScaleFactor());

		ArrayList<MatOfPoint> images = ImageSegmentation.detectImageContours(dilate, imageRegion.getMinSize(), type,
				parameters.isCombineImages());

		for (MatOfPoint tempContour : images) {
			Core.fillConvexPoly(binary, tempContour, new Scalar(0));
		}

		return images;
	}

	// TODO: remove redundancy
	public ArrayList<MatOfPoint> processFixedRegions(Region imageRegion, Region ignoreRegion) {
		ArrayList<MatOfPoint> fixed = new ArrayList<MatOfPoint>();

		for (Region region : regions) {
			if ((region.getType().equals(RegionType.image) || region.getType().equals(RegionType.ignore))) {
				for (Position position : region.getPositions()) {
					if (position.isFixed()) {
						Rect rect = position.getOpenCVRect();
						Mat removed = Rectangle.drawStraightRect(binary, rect, new Scalar(0), -1);
						setBinary(removed);
						// ImageProcessor.showResult(removed, removed.size());

						if (region.getType().equals(RegionType.image)) {
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

	public Region getIgnoreRegion() {
		for (Region region : regions) {
			if ((region.getType().equals(RegionType.ignore))) {
				return region;
			}
		}

		return null;
	}

	public Region getImageRegion() {
		for (Region region : regions) {
			if ((region.getType().equals(RegionType.image))) {
				return region;
			}
		}

		return null;
	}

	public void calcTrueRegionSize(Mat image, ArrayList<Region> regions) {
		for (Region region : regions) {
			region.calcPositionRects(image);
		}

		setRegions(new ArrayList<Region>(regions));
	}

	public void init(Mat original, Parameters parameters) {
		// resize
		//TODO ImageContainerOLD
		ImageContainerOLD image = new ImageContainerOLD(original);
		ImageProcessor.initImage(image, parameters.getDesiredImageHeight());

		// calculate region size
		calcTrueRegionSize(image.getResized(), parameters.getRegionManager().getRegions());

		// binarize
		int binaryThresh = parameters.getBinaryThresh();
		Mat binary = new Mat();

		if (binaryThresh == -1) {
			binary = ImageProcessor.calcBinary(image.getGray());
		} else {
			binary = ImageProcessor.calcBinaryFromThresh(image.getGray(), binaryThresh);
		}

		binary = ImageProcessor.invertImage(binary);
		setBinary(binary);
	}

	public Parameters getParameters() {
		return parameters;
	}

	public void setParameters(Parameters parameters) {
		this.parameters = parameters;
	}

	public ArrayList<Region> getRegions() {
		return regions;
	}

	public void setRegions(ArrayList<Region> regions) {
		this.regions = regions;
	}

	public Mat getBinary() {
		return binary;
	}

	public void setBinary(Mat binary) {
		this.binary = binary;
	}
}