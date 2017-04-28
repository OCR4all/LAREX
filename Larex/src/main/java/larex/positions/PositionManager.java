package larex.positions;

import java.awt.event.MouseEvent;
import java.util.ArrayList;

import org.opencv.core.Point;

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
		
		if (regionManager.getTempRegion().getPositions().contains(position)) {
			regionManager.getTempRegion().getPositions().remove(position);
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

	private Position identifySmallestPosition(ArrayList<Position> candidates) {
		if (candidates.size() == 1) {
			return candidates.get(0);
		} else {
			Position minAreaPosition = null;
			double minArea = Double.MAX_VALUE;

			for (Position position : candidates) {
				double area = position.getOpenCVRect().area();

				if (area < minArea) {
					minArea = area;
					minAreaPosition = position;
				}
			}

			return minAreaPosition;
		}
	}

	public Position identifyActiveRect(MouseEvent e) {
		Point point = new Point(e.getX(), e.getY());

		ArrayList<Position> candidates = new ArrayList<Position>();

		for (Region region : regionManager.getRegions()) {
			if (region.isVisible()) {
				for (Position position : region.getPositions()) {
					if (position.getOpenCVRect().contains(point)) {
						candidates.add(position);
					}
				}
			}
		}
		
		for(Position position : regionManager.getTempRegion().getPositions()) {
			if (position.getOpenCVRect().contains(point)) {
				candidates.add(position);
			}
		}

		if (candidates.size() > 0) {
			return identifySmallestPosition(candidates);
		} else {
			return null;
		}
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