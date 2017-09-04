package larex.regions;

import java.util.ArrayList;

import larex.geometry.PointListManager;
import larex.positions.Position;
import larex.positions.PositionManager;
import larex.positions.PriorityPosition;
import larex.regions.type.RegionType;
import larex.segmentation.parameters.DEFAULT_Parameters;

public class RegionManager {

	private static ArrayList<Region> regions;

	private PositionManager positionManager;
	private PointListManager pointListManager;

	public RegionManager() {
		initRegions();
		setPositionManager(new PositionManager(this));
		setPointListManager(new PointListManager());
	}

	public void initRegions() {

		ArrayList<Region> regions = new ArrayList<Region>();

		Region imageRegion = new Region(RegionType.image, DEFAULT_Parameters.getImageMinSizeDefault(), -1, null, null);
		Region paragraphRegion = new Region(RegionType.paragraph, DEFAULT_Parameters.getParagraphMinSizeDefault(), -1,
				null, null);
		Region marginaliaRegion = new Region(RegionType.marginalia, DEFAULT_Parameters.getMarginaliaMinSizeDefault(),
				-1, null, null);
		Region pageNumberRegion = new Region(RegionType.page_number, DEFAULT_Parameters.getPageNumberMinSizeDefault(),
				1, PriorityPosition.top, null);
		Region ignoreRegion = new Region(RegionType.ignore, 0, -1, null, null);

		regions.add(imageRegion);
		regions.add(paragraphRegion);
		regions.add(marginaliaRegion);
		regions.add(pageNumberRegion);
		regions.add(ignoreRegion);

		setRegions(regions);

		Region tempRegion = new Region(RegionType.other, 0, -1, null, null);
		tempRegion.setPositions(new ArrayList<Position>());
	}

	public Region getRegionByPosition(Position position) {
		for (Region region : regions) {
			if (region.getPositions().contains(position)) {
				return region;
			}
		}

		return null;
	}

	public Region getRegionByType(RegionType type) {
		for (Region region : regions) {
			if (region.getType().equals(type)) {
				return region;
			}
		}

		return null;
	}

	public void addRegion(Region region) {
		regions.add(region);
	}

	public Region getRegion(String type) {
		for (Region region : regions) {
			if (region.getPageXmlIdentifier().equals(type)) {
				return region;
			}
		}

		return null;
	}

	public ArrayList<Region> getRegions() {
		return regions;
	}

	public void setRegions(ArrayList<Region> regions) {
		RegionManager.regions = regions;
	}

	public PositionManager getPositionManager() {
		return positionManager;
	}

	public void setPositionManager(PositionManager positionManager) {
		this.positionManager = positionManager;
	}

	public PointListManager getPointListManager() {
		return pointListManager;
	}

	public void setPointListManager(PointListManager pointListManager) {
		this.pointListManager = pointListManager;
	}
}