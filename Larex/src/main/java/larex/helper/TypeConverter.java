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
	
	
	public static RegionType stringToTypeOld(String typeString) {
//		typeString = typeString.toLowerCase();
		typeString = typeString.replace("_", "-");
		RegionType type = RegionType.image;
		
		if (typeString.equals("paragraph")) {
			type= RegionType.paragraph;
		} else if (typeString.equals("image")) {
			type= RegionType.image;
		} else if (typeString.equals("heading")) {
			type= RegionType.heading;
		} else if (typeString.equals("caption")) {
			type= RegionType.caption;
		} else if (typeString.equals("marginalia")) {
			type= RegionType.marginalia;
		} else if (typeString.equals("page-number") || typeString.equals("pagenumber")) {
			type= RegionType.page_number;
		} else if (typeString.equals("ignore")) {
			type= RegionType.ignore;
		}
		
		return type;
	}
}
