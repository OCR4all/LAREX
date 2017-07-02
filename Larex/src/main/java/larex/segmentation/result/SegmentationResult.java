package larex.segmentation.result;

import larex.imageProcessing.Contour;

import java.awt.Color;
import java.util.ArrayList;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import larex.positions.Position;
import larex.regions.RegionManager;
import larex.regions.type.RegionType;
import larex.segmentation.ImageProcessor;
import larex.dataManagement.Page;

public class SegmentationResult {

	private String fileName;
	private int verticalResolution;
	private double scaleFactor;
	private ArrayList<ResultRegion> regions;
	private boolean scaleCorrection;
	private ArrayList<ResultRegion> readingOrder;

	public SegmentationResult(ArrayList<ResultRegion> regions, int verticalResolution, double scaleFactor) {
		setRegions(regions);
		setVerticalResolution(verticalResolution);
		setScaleFactor(scaleFactor);
		setReadingOrder(new ArrayList<ResultRegion>());
	}

	public void removeRegion(ResultRegion region) {
		for (ResultRegion roRegion : readingOrder) {
			if (roRegion.getPoints().equals(region.getPoints())) {
				readingOrder.remove(roRegion);
				break;
			}
		}

		regions.remove(region);
	}

	public void removeRegionByID(String id){
		for (ResultRegion roRegion : regions) {
			if (roRegion.getId().equals(id)) {
				removeRegion(roRegion);
				break;
			}
		}
	}
	
	public void calcROBinaries(Mat image) {
		for (ResultRegion result : regions) {
			result.calcROBinary(image);
		}
	}

	public ResultRegion identifyResult(Point point) {
		for (ResultRegion result : regions) {
			if (Imgproc.pointPolygonTest(new MatOfPoint2f(result.getScaledPoints().toArray()), point, false) >= 0) {
				return result;
			}
		}

		return null;
	}

	public ResultRegion identifyResultBinary(Point point) {

		for (ResultRegion result : regions) {
			if (result.getContainedPoints().contains(point)) {
				return result;
			}
		}

		return null;
	}

	public void changeRegionType(MatOfPoint segment, RegionType type, RegionManager regionManager) {
		for (ResultRegion region : regions) {
			if (region.getPoints().equals(segment)) {
				Color color = RegionManager.getColorByRegionType(type);
				region.setColor(new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
				region.setType(type);
			}
		}
	}

	public void applyScaleCorrection(Mat result, Mat resized) {
		for (ResultRegion region : regions) {
			region.applyScaleCorrection(result, resized);
		}
	}

	//TODO Delete
	@Deprecated
	public Mat drawResult(Mat result, Mat resized) {
		if (!scaleCorrectionIsDone()) {

			applyScaleCorrection(result, resized);
			setScaleCorrectionDone(true);
		}

		for (ResultRegion region : regions) {
			ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
			contours.add(region.getScaledPoints());
			resized = Contour.drawContours(resized, contours, region.getColor(), 2);
		}

		// TODO
		// result = Contour.drawContours(result,
		// regionList.getRegions(), regionList.getColor(), 15);

		return resized;
	}

	private ArrayList<ResultRegion> identifyImageList() {
		ArrayList<ResultRegion> images = new ArrayList<ResultRegion>();

		for (ResultRegion region : regions) {
			if (region.getType().equals(RegionType.image)) {
				images.add(region);
			}
		}

		return images;
	}

	private boolean rectIsWithinText(Rect rect) {
		for (ResultRegion region : regions) {
			MatOfPoint2f contour2f = new MatOfPoint2f(region.getPoints().toArray());

			if (Imgproc.pointPolygonTest(contour2f, rect.tl(), false) > 0
					&& Imgproc.pointPolygonTest(contour2f, rect.br(), false) > 0) {
				return true;
			}
		}

		return false;
	}

	public void removeImagesWithinText() {
		ArrayList<ResultRegion> imageList = identifyImageList();

		if (imageList.size() == 0) {
			return;
		}

		ArrayList<ResultRegion> keep = new ArrayList<ResultRegion>();

		for (ResultRegion image : imageList) {
			Rect imageRect = Imgproc.boundingRect(image.getPoints());

			if (!rectIsWithinText(imageRect)) {
				keep.add(image);
			}
		}

		regions.removeAll(imageList);
		regions.addAll(keep);
	}

	private ArrayList<ResultRegion> detectSegmentsWithinRoI(Rect rect) {
		ArrayList<ResultRegion> targetRegions = new ArrayList<ResultRegion>();

		for (ResultRegion region : regions) {
			Rect toCheck = Imgproc.boundingRect(region.getPoints());
			if (rect.contains(toCheck.tl()) && rect.contains(toCheck.br())) {
				targetRegions.add(region);
			}
		}

		return targetRegions;
	}

	public void mergeRegions(Position roi/*, Gui gui*/) {
		Page page = null;//TODO gui.getGuiManager().getCurrentPage();

		Mat resized = new Mat(
				new Size(page.getOriginal().width() / page.getScaleFactor(), page.getOriginal().height() / scaleFactor),
				CvType.CV_8U, new Scalar(0));

		Rect rect = new Rect(
				new Point(resized.width() * roi.getTopLeftXPercentage(),
						resized.height() * roi.getTopLeftYPercentage()),
				new Point(resized.width() * roi.getBottomRightXPercentage(),
						resized.height() * roi.getBottomRightYPercentage()));

		ArrayList<ResultRegion> targetRegions = detectSegmentsWithinRoI(rect);
		ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
		Point lastPoint = null;

		if (targetRegions.size() > 0) {
			ResultRegion leftRegion = targetRegions.get(0);

			int leftX = Integer.MAX_VALUE;

			for (ResultRegion region : targetRegions) {
				contours.add(region.getPoints());
				java.awt.Point tempCog = ImageProcessor.calcCenterOfGravity(region.getPoints());
				Point cog = new Point(tempCog.getX(), tempCog.getY());

				if (lastPoint != null) {
					Core.line(resized, lastPoint, cog, new Scalar(255), 2);
				}

				lastPoint = cog;

				Point tl = Imgproc.boundingRect(region.getPoints()).tl();

				if (tl.x < leftX) {
					leftX = (int) tl.x;
					leftRegion = region;
				}
			}

			Imgproc.drawContours(resized, contours, -1, new Scalar(255), -1);
			contours = Contour.findContours(resized);

			if (contours.size() > 0) {
				ResultRegion newResult = new ResultRegion(leftRegion.getType(), leftRegion.getImageHeight(),
						contours.get(0));
				//TODO newResult.applyScaleCorrection(resized, gui.getPnlBackground().getMatImage());
				regions.removeAll(targetRegions);
				regions.add(newResult);
			}
		}
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public int getVerticalResolution() {
		return verticalResolution;
	}

	public void setVerticalResolution(int verticalResolution) {
		this.verticalResolution = verticalResolution;
	}

	public double getScaleFactor() {
		return scaleFactor;
	}

	public void setScaleFactor(double scaleFactor) {
		this.scaleFactor = scaleFactor;
	}

	public ArrayList<ResultRegion> getRegions() {
		return regions;
	}

	public void setRegions(ArrayList<ResultRegion> regions) {
		this.regions = regions;
	}

	public boolean scaleCorrectionIsDone() {
		return scaleCorrection;
	}

	public void setScaleCorrectionDone(boolean scaleCorrection) {
		this.scaleCorrection = scaleCorrection;
	}

	public ArrayList<ResultRegion> getReadingOrder() {
		return readingOrder;
	}

	public void setReadingOrder(ArrayList<ResultRegion> readingOrder) {
		for (int i = 0; i < readingOrder.size(); i++) {
			readingOrder.get(i).setReadingOrderIndex(i);
		}

		this.readingOrder = readingOrder;
	}
}