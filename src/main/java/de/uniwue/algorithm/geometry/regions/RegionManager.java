package de.uniwue.algorithm.geometry.regions;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.uniwue.algorithm.geometry.positions.PriorityPosition;
import de.uniwue.algorithm.geometry.positions.RelativePosition;
import de.uniwue.algorithm.geometry.regions.type.PAGERegionType;
import de.uniwue.algorithm.geometry.regions.type.RegionSubType;
import de.uniwue.algorithm.geometry.regions.type.RegionType;
import de.uniwue.algorithm.segmentation.parameters.DEFAULT_Parameters;

public class RegionManager {

	private static Map<PAGERegionType,Region> regions;

	public RegionManager(Set<Region> regions) {
		for(Region region : regions) addArea(region);
	}
	
	public RegionManager() {
		regions = new HashMap<>();
		// Set default regions
		final PAGERegionType image = new PAGERegionType(RegionType.ImageRegion);
		regions.put(image,new Region(image, DEFAULT_Parameters.IMAGE_MIN_SIZE_DEFAULT, -1, null,
				all(new RelativePosition(0, 0, 1, 1))));

		final PAGERegionType paragraph = new PAGERegionType(RegionType.TextRegion, RegionSubType.paragraph);
		regions.put(paragraph, new Region(paragraph, DEFAULT_Parameters.PARAGRAPH_MIN_SIZE_DEFAULT, -1, null, 
				all(new RelativePosition(0, 0, 1, 1))));
		
		final PAGERegionType marginalia = new PAGERegionType(RegionType.TextRegion, RegionSubType.marginalia);
		regions.put(marginalia, new Region(marginalia, DEFAULT_Parameters.MARGINALIA_MIN_SIZE_DEFAULT, -1, null, 
				all(new RelativePosition(0, 0, 0.25, 1), new RelativePosition(0.75, 0, 1, 1))));
		
		final PAGERegionType pagenumber = new PAGERegionType(RegionType.TextRegion, RegionSubType.page_number);
		regions.put(pagenumber, new Region(pagenumber, DEFAULT_Parameters.PAGE_NUMBER_MIN_SIZE_DEFAULT, 1, PriorityPosition.top, 
				all(new RelativePosition(0, 0, 1, 0.2))));
	}

	private Collection<RelativePosition> all(RelativePosition... positions){
		return Arrays.asList(positions);
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