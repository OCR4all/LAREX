package larex.segmentation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import larex.geometry.regions.RegionSegment;
import larex.geometry.regions.type.RegionType;

public class SegmentationResult {

	private Collection<RegionSegment> regions;
	private List<RegionSegment> readingOrder;

	public SegmentationResult(Collection<RegionSegment> regions) {
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
	
	private Collection<RegionSegment> identifyImageList() {
		ArrayList<RegionSegment> images = new ArrayList<RegionSegment>();

		for (RegionSegment region : regions) {
			if (region.getType().getType().equals(RegionType.ImageRegion)) {
				images.add(region);
			}
		}

		return images;
	}

	private boolean rectIsWithinText(Rect rect) {
		for (RegionSegment region : regions) {
			final MatOfPoint2f contour2f = new MatOfPoint2f(region.getPoints().toArray());

			if (Imgproc.pointPolygonTest(contour2f, rect.tl(), false) > 0
					&& Imgproc.pointPolygonTest(contour2f, rect.br(), false) > 0) {
				return true;
			}
		}

		return false;
	}

	public void removeImagesWithinText() {
		Collection<RegionSegment> imageList = identifyImageList();

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

	public Collection<RegionSegment> getRegions() {
		return regions;
	}

	public Collection<RegionSegment> getReadingOrder() {
		return readingOrder;
	}

	public void setReadingOrder(List<RegionSegment> readingOrder) {
		this.readingOrder = readingOrder;
	}
}