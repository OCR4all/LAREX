package larex.geometry.regions;

import java.util.ArrayList;
import java.util.Collection;

import org.opencv.core.Rect;
import org.opencv.core.Size;

import larex.geometry.positions.PriorityPosition;
import larex.geometry.positions.RelativePosition;
import larex.geometry.regions.type.PAGERegionType;
import larex.geometry.regions.type.RegionSubType;
import larex.geometry.regions.type.RegionType;
import larex.geometry.regions.type.TypeConverter;

public class Region {
	private final PAGERegionType type;
	private int minSize;

	private Collection<RelativePosition> positions;

	private int maxOccurances;
	private final PriorityPosition priorityPosition;

	private static Size activeMat;

	public Region(PAGERegionType type, int minSize, int maxOccurances, PriorityPosition priorityPosition,
			ArrayList<RelativePosition> positions) {
		this.type = type;

		this.minSize = minSize;
		this.maxOccurances = maxOccurances;
		this.priorityPosition = priorityPosition;

		if (positions == null) {
			initRegions();
			calcPositionRects();
		} else {
			setPositions(positions);
		}
	}

	public Region(String typeString, String subTypeString, int minSize, int maxOccurances, String priorityString,
			ArrayList<RelativePosition> positions) {
		this(TypeConverter.stringToPAGEType(typeString,subTypeString), minSize, maxOccurances,
				calcPriorityPosition(maxOccurances, priorityString), positions);
	}

	private static PriorityPosition calcPriorityPosition(int maxOccurances, String priorityString) {
		if (maxOccurances == 1) {
			if (priorityString.equals("top")) {
				return PriorityPosition.top;
			} else if (priorityString.equals("bottom")) {
				return PriorityPosition.bottom;
			} else if (priorityString.equals("left")) {
				return PriorityPosition.left;
			} else if (priorityString.equals("right")) {
				return PriorityPosition.right;
			}
		}
		return null;
	}

	public void calcPositionRects(Size image) {
		setActiveMat(image);

		for (RelativePosition position : positions) {
			Rect rect = position.calcRect(image);
			position.updateRect(rect, activeMat);
		}
	}

	public void calcPositionRects() {
		if (activeMat != null) {
			calcPositionRects(activeMat);
		}
	}

	public void initRegions() {
		ArrayList<RelativePosition> positions = new ArrayList<RelativePosition>();

		if(type.getType().equals(RegionType.TextRegion)) {
			if (type.getSubtype().equals(RegionSubType.paragraph)) {
				RelativePosition position = new RelativePosition(0, 0, 1, 1);
				positions.add(position);
			} else if (type.getSubtype().equals(RegionSubType.marginalia)) {
				RelativePosition leftPosition = new RelativePosition(0, 0, 0.25, 1);
				RelativePosition rightPosition = new RelativePosition(0.75, 0, 1, 1);
				positions.add(leftPosition);
				positions.add(rightPosition);
			} else if (type.getSubtype().equals(RegionSubType.page_number)) {
				RelativePosition topPosition = new RelativePosition(0, 0, 1, 0.2);
				positions.add(topPosition);
			} else if (type.getSubtype().equals(RegionSubType.header) || type.getSubtype().equals(RegionSubType.heading)) {
				RelativePosition bottomPosition = new RelativePosition(0, 0, 1, 0.2);
				positions.add(bottomPosition);
			} else if (type.getSubtype().equals(RegionSubType.footer) || type.getSubtype().equals(RegionSubType.footnote)
					|| type.getSubtype().equals(RegionSubType.footnote_continued)) {
				RelativePosition bottomPosition = new RelativePosition(0, 0.8, 1, 1);
				positions.add(bottomPosition);
			} else if (!type.getSubtype().equals(RegionSubType.ignore)) {
				RelativePosition defaultPosition = new RelativePosition(0.2, 0.2, 0.8, 0.8);
				positions.add(defaultPosition);
			}
		} else if(!type.getType().equals(RegionType.ImageRegion)) {
			RelativePosition defaultPosition = new RelativePosition(0.2, 0.2, 0.8, 0.8);
			positions.add(defaultPosition);
		}

		setPositions(positions);
	}

	public void addPosition(RelativePosition position) {
		positions.add(position);
		position.calcPercentages(activeMat);
		calcPositionRects();
	}

	public PAGERegionType getType() {
		return type;
	}

	public String getPageXmlIdentifier() {
		return type.toString();
	}

	public int getMinSize() {
		return minSize;
	}

	public void setMinSize(int minSize) {
		this.minSize = minSize;
	}

	public Collection<RelativePosition> getPositions() {
		return positions;
	}

	public void setPositions(Collection<RelativePosition> positions) {
		this.positions = positions;
	}

	public int getMaxOccurances() {
		return maxOccurances;
	}

	public void setMaxOccurances(int maxOccurances) {
		this.maxOccurances = maxOccurances;
	}

	public PriorityPosition getPriorityPosition() {
		return priorityPosition;
	}

	public void setActiveMat(Size activeMat) {
		Region.activeMat = activeMat;
	}
}