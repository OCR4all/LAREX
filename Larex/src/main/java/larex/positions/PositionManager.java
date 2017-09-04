package larex.positions;

import larex.regions.Region;
import larex.regions.RegionManager;
import larex.regions.type.RegionType;

public class PositionManager {

	private RegionManager regionManager;
	private Position activePosition;

	public PositionManager(RegionManager regionManager) {
		setRegionManager(regionManager);
	}

	public void deletePosition(Position position) {
		for (Region region : regionManager.getRegions()) {
			if (region.getPositions().contains(position)) {
				region.getPositions().remove(position);
			}
		}
	}

	public void manageActivePositions(Position newActivePosition) {
		if (activePosition != null) {
			activePosition.setActive(false);
		}

		if (newActivePosition != null) {
			newActivePosition.setActive(true);
		}

		setActivePosition(newActivePosition);
	}

	public void processFixed(Position position, RegionType type) {
		Region targetRegion = regionManager.getRegionByType(type);
		Region containingRegion = regionManager.getRegionByPosition(position);
		
		if(targetRegion != null) {
			if(containingRegion != null) {
				containingRegion.getPositions().remove(position);
			}
			targetRegion.addPosition(position);
		}
	}
	
	public RegionManager getRegionManager() {
		return regionManager;
	}

	public void setRegionManager(RegionManager regionManager) {
		this.regionManager = regionManager;
	}

	public Position getActivePosition() {
		return activePosition;
	}

	public void setActivePosition(Position activePosition) {
		this.activePosition = activePosition;
	}
}