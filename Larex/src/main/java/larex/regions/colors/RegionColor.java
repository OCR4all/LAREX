package larex.regions.colors;

import java.awt.Color;

import org.opencv.core.Scalar;

import larex.regions.type.RegionType;

public class RegionColor {

	private String name;
	private Color color;
	private Scalar openCVColor;

	private RegionType type;
	
	public RegionColor(String name, Color color) {
		setName(name);
		setColor(color);
		setOpenCVColor(new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
	}

	public Color getColor() {
		return color;
	}

	public void setColor(Color color) {
		this.color = color;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Scalar getOpenCVColor() {
		return openCVColor;
	}

	public void setOpenCVColor(Scalar openCVColor) {
		this.openCVColor = openCVColor;
	}

	public RegionType getType() {
		return type;
	}

	public void setType(RegionType type) {
		this.type = type;
	}	
}