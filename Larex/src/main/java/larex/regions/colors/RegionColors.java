package larex.regions.colors;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;

import larex.regions.Region;
import larex.regions.type.RegionType;

public class RegionColors {

	private static ArrayList<RegionColor> regionColors;
	private static String[] regionColorsNames;
		
	public static void init() {
		regionColors = new ArrayList<RegionColor>();
		
//		RegionColor white = new RegionColor("white", Color.WHITE);
//		regionColors.add(white);
		
		RegionColor black = new RegionColor("black", Color.BLACK);
		regionColors.add(black);
		
		RegionColor red = new RegionColor("red", Color.RED);
		regionColors.add(red);
		
		RegionColor green = new RegionColor("green", Color.GREEN);
		regionColors.add(green);
		
		RegionColor blue = new RegionColor("blue", Color.BLUE);
		regionColors.add(blue);
		
		RegionColor yellow = new RegionColor("yellow", Color.YELLOW);
		regionColors.add(yellow);
		
		RegionColor magenta = new RegionColor("magenta", Color.MAGENTA);
		regionColors.add(magenta);
		
		RegionColor purple = new RegionColor("purple", new Color(128, 0, 128));
		regionColors.add(purple);
		
		RegionColor cyan = new RegionColor("cyan", Color.CYAN);
		regionColors.add(cyan);
		
		RegionColor maroon = new RegionColor("maroon", new Color(128, 0, 0));
		regionColors.add(maroon);
		
		RegionColor olive = new RegionColor("olive", new Color(128, 128, 0));
		regionColors.add(olive);
		
		
		regionColorsNames = new String[regionColors.size()];
		
		for(int i = 0; i < regionColors.size(); i++) {
			regionColorsNames[i] = regionColors.get(i).getName();
		}
		
		setRegionColorsNames(regionColorsNames);
	}

	public static Color getColorByType(RegionType type) {
		if(regionColors == null) {
			init();
		}
		
		for(RegionColor color : regionColors) {
			if(color.getType() != null) {
				if(color.getType().equals(type)) {
					return color.getColor();
				}
			}
		}
		
		return null;
	}
	
	public static RegionColor getColorByName(String name) {
		if(regionColors == null) {
			init();
		}
		
		for(RegionColor color : regionColors) {
			if(color.getName().equals(name)) {
				return color;
			}
		}
		
		return null;
	}
	
	public static String[] getUnusedColorNames(ArrayList<Region> regions) {
		if(regionColorsNames == null) {
			init();
		}
		
		ArrayList<String> unusedNames = new ArrayList<String>(Arrays.asList(regionColorsNames));
		
		for(Region region : regions) {
			unusedNames.remove(region.getColor().getName());
		}
		
		String[] unused = new String[unusedNames.size()];
		return unusedNames.toArray(unused);
	}
	
	
	public static ArrayList<RegionColor> getRegionColors() {
		if(regionColors == null) {
			init();
		}
		
		return regionColors;
	}

	public static void setRegionColors(ArrayList<RegionColor> regionColors) {
		RegionColors.regionColors = regionColors;
	}

	public static String[] getRegionColorsNames() {
		if(regionColorsNames == null) {
			init();
		}
		
		return regionColorsNames;
	}

	public static void setRegionColorsNames(String[] regionColorsNames) {
		RegionColors.regionColorsNames = regionColorsNames;
	}
}