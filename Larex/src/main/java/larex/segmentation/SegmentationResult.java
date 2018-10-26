package larex.segmentation;

import java.util.ArrayList;

import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import larex.geometry.regions.RegionSegment;
import larex.geometry.regions.type.RegionType;

public class SegmentationResult {

	private ArrayList<RegionSegment> regions;
	private ArrayList<RegionSegment> readingOrder;

	public SegmentationResult(ArrayList<RegionSegment> regions) {
		this.regions = regions;
		setReadingOrder(new ArrayList<RegionSegment>());
	}

	public RegionSegment getRegionByID(String id){
		for (RegionSegment roRegion : regions) {
			if (roRegion.getId().equals(id)) {
				return roRegion;
			}
		}
		return null;
	}
	
	private ArrayList<RegionSegment> identifyImageList() {
		ArrayList<RegionSegment> images = new ArrayList<RegionSegment>();

		for (RegionSegment region : regions) {
			if (region.getType().equals(RegionType.image)) {
				images.add(region);
			}
		}

		return images;
	}

	private boolean rectIsWithinText(Rect rect) {
		for (RegionSegment region : regions) {
			MatOfPoint2f contour2f = new MatOfPoint2f(region.getPoints().toArray());

			if (Imgproc.pointPolygonTest(contour2f, rect.tl(), false) > 0
					&& Imgproc.pointPolygonTest(contour2f, rect.br(), false) > 0) {
				return true;
			}
		}

		return false;
	}

	public void removeImagesWithinText() {
		ArrayList<RegionSegment> imageList = identifyImageList();

		if (imageList.size() == 0) {
			return;
		}

		ArrayList<RegionSegment> keep = new ArrayList<RegionSegment>();

		for (RegionSegment image : imageList) {
			Rect imageRect = Imgproc.boundingRect(image.getPoints());

			if (!rectIsWithinText(imageRect)) {
				keep.add(image);
			}
		}

		regions.removeAll(imageList);
		regions.addAll(keep);
	}

	public ArrayList<RegionSegment> getRegions() {
		return regions;
	}

	public ArrayList<RegionSegment> getReadingOrder() {
		return readingOrder;
	}

	public void setReadingOrder(ArrayList<RegionSegment> readingOrder) {
		this.readingOrder = readingOrder;
	}
}