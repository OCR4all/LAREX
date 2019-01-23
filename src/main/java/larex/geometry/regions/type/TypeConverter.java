package larex.geometry.regions.type;

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
