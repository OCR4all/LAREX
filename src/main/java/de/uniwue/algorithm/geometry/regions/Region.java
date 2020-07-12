package de.uniwue.algorithm.geometry.regions;

import java.util.Arrays;
import java.util.Collection;

import de.uniwue.algorithm.geometry.positions.PriorityPosition;
import de.uniwue.algorithm.geometry.positions.RelativePosition;
import de.uniwue.algorithm.geometry.regions.type.PAGERegionType;
import de.uniwue.algorithm.geometry.regions.type.TypeConverter;

public class Region {
	private final PAGERegionType type;
	private int minSize;

	private Collection<RelativePosition> positions;

	private int maxOccurances;
	private final PriorityPosition priorityPosition;


	public Region(PAGERegionType type, int minSize, int maxOccurances, PriorityPosition priorityPosition,
			Collection<RelativePosition> positions) {
		this.type = type;

		this.minSize = minSize;
		this.maxOccurances = maxOccurances;
		this.priorityPosition = priorityPosition;

		if (positions == null) {
			throw new IllegalArgumentException("Positions can't be null.");
		} else {
			this.positions = positions;
		}
	}

	public Region(PAGERegionType type, int minSize, int maxOccurances, PriorityPosition priorityPosition,
			RelativePosition... positions) {
		this(type,minSize,maxOccurances,priorityPosition,Arrays.asList(positions));
	}

	public Region(String typeString, String subTypeString, int minSize, int maxOccurances, String priorityString,
			Collection<RelativePosition> positions) {
		this(TypeConverter.stringToPAGEType(typeString,subTypeString), minSize, maxOccurances,
				calcPriorityPosition(maxOccurances, priorityString), positions);
	}

	private static PriorityPosition calcPriorityPosition(int maxOccurances, String priorityString) {
		if (maxOccurances == 1) {
			switch (priorityString) {
				case "top":
					return PriorityPosition.top;
				case "bottom":
					return PriorityPosition.bottom;
				case "left":
					return PriorityPosition.left;
				case "right":
					return PriorityPosition.right;
			}
		}
		return null;
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

	public int getMaxOccurances() {
		return maxOccurances;
	}

	public void setMaxOccurances(int maxOccurances) {
		this.maxOccurances = maxOccurances;
	}

	public PriorityPosition getPriorityPosition() {
		return priorityPosition;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + "Region".hashCode();
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof Region) {
			Region otherRegion = (Region) obj;
			return type.equals(otherRegion.getType());
		}
		return false;
	}
}