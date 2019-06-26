package larex.geometry.regions;

import java.util.ArrayList;

import larex.geometry.positions.PriorityPosition;
import larex.geometry.regions.type.PAGERegionType;
import larex.geometry.regions.type.RegionSubType;
import larex.geometry.regions.type.RegionType;
import larex.segmentation.parameters.DEFAULT_Parameters;

public class RegionManager {

	private static ArrayList<Region> regions;

	public RegionManager() {
		initRegions();
	}

	public void initRegions() {
		ArrayList<Region> regions = new ArrayList<Region>();

		Region imageRegion = new Region(new PAGERegionType(RegionType.ImageRegion), 
				DEFAULT_Parameters.IMAGE_MIN_SIZE_DEFAULT, -1, null, null);
		Region paragraphRegion = new Region(new PAGERegionType(RegionType.TextRegion,RegionSubType.paragraph), 
				DEFAULT_Parameters.PARAGRAPH_MIN_SIZE_DEFAULT, -1, null, null);
		Region marginaliaRegion = new Region(new PAGERegionType(RegionType.TextRegion,RegionSubType.marginalia), 
				DEFAULT_Parameters.MARGINALIA_MIN_SIZE_DEFAULT, -1, null, null);
		Region pageNumberRegion = new Region(new PAGERegionType(RegionType.TextRegion,RegionSubType.page_number), 
				DEFAULT_Parameters.PAGE_NUMBER_MIN_SIZE_DEFAULT, 1, PriorityPosition.top, null);
		Region ignoreRegion = new Region(new PAGERegionType(RegionType.TextRegion,RegionSubType.ignore), 0, -1, null, null);

		regions.add(imageRegion);
		regions.add(paragraphRegion);
		regions.add(marginaliaRegion);
		regions.add(pageNumberRegion);
		regions.add(ignoreRegion);

		setRegions(regions);
	}

	public Region getRegionByType(PAGERegionType type) {
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

	public ArrayList<Region> getRegions() {
		return regions;
	}

	public void setRegions(ArrayList<Region> regions) {
		RegionManager.regions = regions;
	}
}