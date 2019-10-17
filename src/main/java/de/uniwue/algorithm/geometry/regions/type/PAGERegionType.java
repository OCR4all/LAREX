package de.uniwue.algorithm.geometry.regions.type;

import java.util.ArrayList;
import java.util.List;


public class PAGERegionType {
	private final RegionType type;
	private final RegionSubType subtype;
	private static List<PAGERegionType> regionTypes;
	
	public PAGERegionType(RegionType type, RegionSubType subtype) {
		if(type == null && subtype == null)
			throw new IllegalArgumentException("A PAGERegion must have a type.");
		this.type = type;
		this.subtype = subtype;
	}

	public PAGERegionType(RegionType type) {
		this(type,null);
	}
	
	public RegionType getType() {
		return type;
	}
	
	public RegionSubType getSubtype() {
		return subtype;
	}
	
	@Override
	public String toString() {
		if(subtype == null){
			return type.toString();
		} else {
			return subtype.toString();
		}
	}
	
	public static List<PAGERegionType> values(){
		if(regionTypes == null) {
			regionTypes = new ArrayList<PAGERegionType>();
			for(RegionType type: RegionType.values()) {
				if(type.equals(RegionType.TextRegion)) {
					for(RegionSubType subtype: RegionSubType.values()) {
						regionTypes.add(new PAGERegionType(type,subtype));
					}
				} else {
					regionTypes.add(new PAGERegionType(type,null));
				}
			}
		}
		return regionTypes;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((subtype == null) ? 0 : subtype.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof PAGERegionType) {
			PAGERegionType other = (PAGERegionType) obj;
			if(subtype != null) {
				return subtype.equals(other.getSubtype());
			} else {
				return type.equals(other.getType());
			}
		}
		return false;
	}
} 