package larex.helper;

import larex.regions.type.RegionType;

public class TypeConverter {
	public static RegionType stringToType(String typeString) {
		typeString = typeString.replace("-", "_");
		RegionType type = RegionType.valueOf(typeString);
		
		if(type == null) {
			System.out.println("Couldn't match RegionType!");
		}
		
		return type;
	}
}
