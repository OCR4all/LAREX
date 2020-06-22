package de.uniwue.algorithm.geometry.regions.type;

public class TypeConverter {

	/**
	 * Convert a string representation of a main type (e.g. TextRegion) and a sub
	 * type (e.g. paragraph) into a PAGERegionType.
	 * 
	 * Will raise an IllegalArgumentException if the types are unknown or if an
	 * incompatible sub type is given for the main type.
	 * 
	 * @param typeString    Main type as String. e.g. "TextRegion"
	 * @param subtypeString Sub type as String. e.g. "paragraph"
	 * @return PAGERegionType with both main and sub type incorporated
	 */
	public static PAGERegionType stringToPAGEType(String typeString, String subtypeString) {
		return new PAGERegionType(stringToMainType(typeString), stringToSubType(subtypeString));
	}

	/**
	 * Convert a string representation of a combined type into a PAGERegionType. A
	 * combined type is either a Region (e.g. "GraphicRegion") or the sub type of a
	 * TextRegion (e.g. "paragraph") since only TextRegions have sub types.
	 * 
	 * Will raise an IllegalArgumentException if the types are unknown
	 * 
	 * @param typeString Combined types as String. e.g. "paragraph" or
	 *                   "GraphicRegion"
	 * @return PAGERegionType with both main and sub type incorporated
	 */
	public static PAGERegionType stringToPAGEType(String typeString) {
		RegionType maintype = TypeConverter.stringToMainType(typeString);
		if (maintype == null) {
			return new PAGERegionType(RegionType.TextRegion, TypeConverter.stringToSubType(typeString));
		} else {
			return new PAGERegionType(stringToMainType(typeString));
		}
	}

	/**
	 * Convert a string representation of a main type (e.g. "TextRegion",
	 * "GraphicRegion") into a RegionType
	 * 
	 * @param typeString String representation of a main type / RegionType. e.g.
	 *                   "TextRegion", "GraphicRegion"
	 * @return RegionType representing the type string
	 */
	public static RegionType stringToMainType(String typeString) {
		try {
			return RegionType.valueOf(typeString);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * Convert the internal RegionType enum into the RegionType the PRimA format
	 * uses.
	 * 
	 * @param regionType RegionType to convert
	 * @return PRimA format RegionType classes
	 */
	public static org.primaresearch.dla.page.layout.physical.shared.RegionType enumRegionTypeToPrima(
			RegionType regionType) {
		switch (regionType) {
		case AdvertRegion:
			return org.primaresearch.dla.page.layout.physical.shared.RegionType.AdvertRegion;
		case ChartRegion:
			return org.primaresearch.dla.page.layout.physical.shared.RegionType.ChartRegion;
		case ChemRegion:
			return org.primaresearch.dla.page.layout.physical.shared.RegionType.ChemRegion;
		case CustomRegion:
			return org.primaresearch.dla.page.layout.physical.shared.RegionType.CustomRegion;
		case GraphicRegion:
			return org.primaresearch.dla.page.layout.physical.shared.RegionType.GraphicRegion;
		case ImageRegion:
			return org.primaresearch.dla.page.layout.physical.shared.RegionType.ImageRegion;
		case LineDrawingRegion:
			return org.primaresearch.dla.page.layout.physical.shared.RegionType.LineDrawingRegion;
		case MapRegion:
			return org.primaresearch.dla.page.layout.physical.shared.RegionType.MapRegion;
		case MathsRegion:
			return org.primaresearch.dla.page.layout.physical.shared.RegionType.MathsRegion;
		case MusicRegion:
			return org.primaresearch.dla.page.layout.physical.shared.RegionType.MusicRegion;
		case NoiseRegion:
			return org.primaresearch.dla.page.layout.physical.shared.RegionType.NoiseRegion;
		case SeparatorRegion:
			return org.primaresearch.dla.page.layout.physical.shared.RegionType.SeparatorRegion;
		case TableRegion:
			return org.primaresearch.dla.page.layout.physical.shared.RegionType.TableRegion;
		case TextRegion:
			return org.primaresearch.dla.page.layout.physical.shared.RegionType.TextRegion;
		case UnknownRegion:
			return org.primaresearch.dla.page.layout.physical.shared.RegionType.UnknownRegion;
		default:
			return null;
		}
	}

	/**
	 * Convert a string representation of a sub type (e.g. "paragraph",
	 * "marginalia") into a RegionSubType
	 * 
	 * @param typeString String representation of a sub type / RegionSubType. e.g.
	 *                   "paragraph", "marginalia"
	 * @return RegionSubType representing the type string
	 */
	public static RegionSubType stringToSubType(String typeString) {
		typeString = typeString.replace("-", "_");
		try {
			RegionSubType type = RegionSubType.valueOf(typeString);

			return type;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * Convert a sub type into a string representation (e.g. "paragraph",
	 * "marginalia") 
	 * 
	 * @param subType RegionSubType
	 * @return String representing the subtype
	 */
	public static String subTypeToString(RegionSubType subType) {
		return subType.toString().replace("_", "-");
	}
}
