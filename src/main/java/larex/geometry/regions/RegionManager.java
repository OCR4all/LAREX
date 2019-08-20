package larex.geometry.regions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import larex.geometry.positions.PriorityPosition;
import larex.geometry.regions.type.PAGERegionType;
import larex.geometry.regions.type.RegionSubType;
import larex.geometry.regions.type.RegionType;
import larex.segmentation.parameters.DEFAULT_Parameters;

public class RegionManager {

	private static Map<PAGERegionType,Region> regions;

	public RegionManager(Set<Region> regions) {
		for(Region region : regions) addArea(region);
	}
	
	public RegionManager() {
		regions = new HashMap<>();
		// Set default regions
		final PAGERegionType image = new PAGERegionType(RegionType.ImageRegion);
		regions.put(image,new Region(image, DEFAULT_Parameters.IMAGE_MIN_SIZE_DEFAULT, -1, null, null));

		final PAGERegionType paragraph = new PAGERegionType(RegionType.TextRegion, RegionSubType.paragraph);
		regions.put(paragraph, new Region(paragraph, DEFAULT_Parameters.PARAGRAPH_MIN_SIZE_DEFAULT, -1, null, null));
		
		final PAGERegionType marginalia = new PAGERegionType(RegionType.TextRegion, RegionSubType.marginalia);
		regions.put(marginalia, new Region(marginalia, DEFAULT_Parameters.MARGINALIA_MIN_SIZE_DEFAULT, -1, null, null));
		
		final PAGERegionType pagenumber = new PAGERegionType(RegionType.TextRegion, RegionSubType.page_number);
		regions.put(pagenumber, new Region(pagenumber, DEFAULT_Parameters.PAGE_NUMBER_MIN_SIZE_DEFAULT, 1, PriorityPosition.top, null));
		
		final PAGERegionType ignore = new PAGERegionType(RegionType.TextRegion, RegionSubType.ignore);
		regions.put(ignore, new Region(ignore, 0, -1, null, null));
	}

	/**
	 * Get a region by its type.
	 * 
	 * @param type
	 * @return
	 */
	public Region getRegionByType(PAGERegionType type) {
		return regions.getOrDefault(type, null);
	}

	/**
	 * Add a region to the collection of regions.
	 * (Will overwrite if a region of the same type already exists in the collection)
	 * 
	 * @param region
	 */
	public void addArea(Region region) {
		regions.put(region.getType(), region);
	}

	/**
	 * Retrieve a collection of all regions
	 * 
	 * @return
	 */
	public Set<Region> getRegions() {
		return new HashSet<>(regions.values());
	}
}