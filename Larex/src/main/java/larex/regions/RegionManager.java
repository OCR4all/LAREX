package larex.regions;


import java.awt.Color;
import java.util.ArrayList;

import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import larex.geometry.PointListManager;
import larex.positions.Position;
import larex.positions.PositionManager;
import larex.positions.PriorityPosition;
import larex.regions.colors.RegionColor;
import larex.regions.type.RegionType;
import larex.segmentation.parameters.DEFAULT_Parameters;

public class RegionManager {

	//private Gui gui;
	private static ArrayList<Region> regions;

	private Region tempRegion;

	private PositionManager positionManager;
	private PointListManager pointListManager;

	public RegionManager(/*Gui gui*/) {
		//setGui(gui);
		initRegions();
		setPositionManager(new PositionManager(this));
		setPointListManager(new PointListManager());
	}

	/*public void setRoI(Position roi, Painter painter, boolean isPermanent) {
		Region ignoreRegion = getRegion("ignore");

		if (roi.getTopLeftYPercentage() > 0) {
			Position topPos = new Position(0, 0, 1, roi.getTopLeftYPercentage());
			topPos.setFixed(true);
			topPos.setPermanent(isPermanent);
			topPos.updateRect(topPos.calcRect(painter.getPanel().getMatImage()), painter.getPanel().getMatImage());
			ignoreRegion.addPosition(topPos);
		}

		if (roi.getBottomRightYPercentage() < 1) {
			Position bottomPos = new Position(0, roi.getBottomRightYPercentage(), 1, 1);
			bottomPos.setFixed(true);
			bottomPos.setPermanent(isPermanent);
			ignoreRegion.addPosition(bottomPos);
		}

		if (roi.getTopLeftXPercentage() > 0) {
			Position leftPos = new Position(0, 0, roi.getTopLeftXPercentage(), 1);
			leftPos.setFixed(true);
			leftPos.setPermanent(isPermanent);
			ignoreRegion.addPosition(leftPos);
		}

		if (roi.getBottomRightXPercentage() < 1) {
			Position rightPos = new Position(roi.getBottomRightXPercentage(), 0, 1, 1);
			rightPos.setFixed(true);
			rightPos.setPermanent(isPermanent);
			ignoreRegion.addPosition(rightPos);
		}

		//painter.setActivePosition(null);
		//painter.getPanel().repaint();
		ignoreRegion.setVisible(true);
	}*/

	public void initRegions() {

		ArrayList<Region> regions = new ArrayList<Region>();

		Region imageRegion = new Region(RegionType.image, DEFAULT_Parameters.getImageMinSizeDefault(),
				new RegionColor("green", Color.GREEN), -1, null, null);
		Region paragraphRegion = new Region(RegionType.paragraph, DEFAULT_Parameters.getParagraphMinSizeDefault(),
				new RegionColor("red", Color.RED), -1, null, null);
		Region marginaliaRegion = new Region(RegionType.marginalia, DEFAULT_Parameters.getMarginaliaMinSizeDefault(),
				new RegionColor("yellow", Color.YELLOW), -1, null, null);
		Region pageNumberRegion = new Region(RegionType.page_number, DEFAULT_Parameters.getPageNumberMinSizeDefault(),
				new RegionColor("magenta", new Color(255, 0, 255)), 1, PriorityPosition.top, null);
		Region ignoreRegion = new Region(RegionType.ignore, 0, new RegionColor("black", Color.BLACK), -1, null, null);

		regions.add(imageRegion);
		regions.add(paragraphRegion);
		regions.add(marginaliaRegion);
		regions.add(pageNumberRegion);
		regions.add(ignoreRegion);

		setRegions(regions);

		Region tempRegion = new Region(RegionType.other, 0, new RegionColor("purple", new Color(128, 0, 255, 255)), -1,
				null, null);
		tempRegion.setPositions(new ArrayList<Position>());
		setTempRegion(tempRegion);
	}

	public void update(Mat image, boolean removeFixed) {
		for (Region region : regions) {
			if (removeFixed) {
				region.removeNonPermanentFixedPositions();
			}
			region.calcPositionRects(image);
		}
	}

	public Region getRegionByPosition(Position position) {
		for (Region region : regions) {
			if (region.getPositions().contains(position)) {
				return region;
			}
		}

		if (tempRegion.getPositions().contains(position)) {
			return tempRegion;
		}

		return null;
	}

	public static Color getColorByRegionType(RegionType type) {
		for (Region region : regions) {
			if (region.getType().equals(type)) {
				return region.getRegionColor().getColor();
			}
		}

		return null;
	}
	
	public static Scalar getScalarByRegionType(RegionType type) {
		Color color = getColorByRegionType(type);
		
		if(color == null) {
			return null;
		}
		
		Scalar scalar = new Scalar(color.getBlue(), color.getGreen(), color.getRed());
		
		return scalar;
	}

	public Region getRegionByType(RegionType type) {
		for (Region region : regions) {
			if (region.getType().equals(type)) {
				return region;
			}
		}

		if (tempRegion.getType().equals(type)) {
			return tempRegion;
		}

		return null;
	}

	public void addRegion(Region region) {
		regions.add(region);
		//gui.getPnlRegionParameters().refreshAddedRegion(region);
		//gui.getPnlPostProcessing().refreshAddedRegion(region);
	}

	public Region getRegion(String type) {
		for (Region region : regions) {
			if (region.getPageXmlIdentifier().equals(type)) {
				return region;
			}
		}

		return null;
	}

	public void changeActiveRegion(String type) {
		for (Region region : regions) {
			if (region.getPageXmlIdentifier().equals(type)) {
				region.setVisible(true);
			} else {
				region.setVisible(false);
			}
		}
	}

	public void changeActiveRegion(Region region) {
		for (Region tempRegion : regions) {
			if (tempRegion.equals(region)) {
				region.setVisible(true);
			} else {
				region.setVisible(false);
			}
		}
	}

	public static String[] getRegionNames() {
		String[] regionNames = new String[regions.size()];

		for (int i = 0; i < regions.size(); i++) {
			String regionName = regions.get(i).getPageXmlIdentifier();
			regionName = regionName.replace("_", "-");
			regionNames[i] = regionName;
		}

		return regionNames;
	}

	public void addTempPosition(Position position) {
		ArrayList<Position> tempPositions = this.tempRegion.getPositions();
		tempPositions.add(position);
		tempRegion.setPositions(tempPositions);
	}

	/*public Gui getGui() {
		return gui;
	}

	public void setGui(Gui gui) {
		this.gui = gui;
	}*/

	public ArrayList<Region> getRegions() {
		return regions;
	}

	public void setRegions(ArrayList<Region> regions) {
		RegionManager.regions = regions;
	}

	public Region getTempRegion() {
		return tempRegion;
	}

	public void setTempRegion(Region tempRegion) {
		this.tempRegion = tempRegion;
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