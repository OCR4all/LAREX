package larex.segmentation.result;

import java.util.ArrayList;

import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import larex.regions.RegionManager;
import larex.regions.type.RegionType;

public class SegmentationResult {

	private ArrayList<ResultRegion> regions;
	private ArrayList<ResultRegion> readingOrder;

	public SegmentationResult(ArrayList<ResultRegion> regions) {
		setRegions(regions);
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
	
	public ResultRegion removeRegionByID(String id){
		ResultRegion roRegion = getRegionByID(id);
		if(roRegion != null){
			removeRegion(roRegion);
			return roRegion;
		}
		return null;
	}
	
	public ResultRegion getRegionByID(String id){
		for (ResultRegion roRegion : regions) {
			if (roRegion.getId().equals(id)) {
				return roRegion;
			}
		}
		return null;
	}
	
	public void changeRegionType(MatOfPoint segment, RegionType type, RegionManager regionManager) {
		for (ResultRegion region : regions) {
			if (region.getPoints().equals(segment)) {
				region.setType(type);
			}
		}
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

	public ArrayList<ResultRegion> getRegions() {
		return regions;
	}
	public void addRegion(ResultRegion region){
		this.regions.add(region);
	}

	public void setRegions(ArrayList<ResultRegion> regions) {
		this.regions = regions;
	}

	public ArrayList<ResultRegion> getReadingOrder() {
		return readingOrder;
	}

	public void setReadingOrder(ArrayList<ResultRegion> readingOrder) {
		this.readingOrder = readingOrder;
	}
	
	/**
	 * Creates a shallow copy of the SegmentationResult (ResultRegions will not be cloned)
	 */
	public SegmentationResult clone(){
		SegmentationResult copy = new SegmentationResult(new ArrayList<ResultRegion>(regions));
		copy.setReadingOrder(new ArrayList<ResultRegion>(readingOrder));
		return copy;
	}
}