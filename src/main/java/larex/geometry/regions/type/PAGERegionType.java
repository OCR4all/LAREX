package larex.geometry.regions.type;

import java.util.ArrayList;
import java.util.List;


public class PAGERegionType {
	private final RegionType type;
	private final RegionSubType subtype;
	private static List<PAGERegionType> regionTypes;
	
	public PAGERegionType(RegionType type, RegionSubType subtype) {
		this.type = type;
		this.subtype = subtype;
	}

	public PAGERegionType(RegionType type) {
		this(type,null);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof PAGERegionType) {
			PAGERegionType objTemp = (PAGERegionType) obj;

			return objTemp.type.equals(type) && 
					((objTemp.subtype == null && subtype == null) || 
							(objTemp.subtype != null && objTemp.subtype.equals(subtype)));
		}
		return false;
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
} 