package larex.segmentation.result;

import java.util.ArrayList;

import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import larex.regions.type.RegionType;

public class SegmentationResult {

	private ArrayList<ResultRegion> regions;
	private ArrayList<ResultRegion> readingOrder;

	public SegmentationResult(ArrayList<ResultRegion> regions) {
		this.regions = regions;
		setReadingOrder(new ArrayList<ResultRegion>());
	}

	public ResultRegion getRegionByID(String id){
		for (ResultRegion roRegion : regions) {
			if (roRegion.getId().equals(id)) {
				return roRegion;
			}
		}
		return null;
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

	public ArrayList<ResultRegion> getReadingOrder() {
		return readingOrder;
	}

	public void setReadingOrder(ArrayList<ResultRegion> readingOrder) {
		this.readingOrder = readingOrder;
	}
}