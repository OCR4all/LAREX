package de.uniwue.algorithm.segmentation.parameters;

import de.uniwue.algorithm.geometry.ExistingGeometry;
import de.uniwue.algorithm.geometry.regions.RegionManager;

public class Parameters {

	// Processing Parameters
	private int desiredImageHeight;

	private int imageRemovalDilationX;
	private int imageRemovalDilationY;

	private int textDilationX;
	private int textDilationY;

	private RegionManager regionManager;
	private ExistingGeometry existingGeometry;

	private ImageSegType imageSegType;
	private boolean combineImages;

	public Parameters() {
		this(new RegionManager(), 0);
	}
	
	public Parameters(RegionManager regionManager,int originalHeight) {
		this.regionManager = regionManager;

		this.desiredImageHeight = DEFAULT_Parameters.IMAGE_HEIGHT_DEFAULT;

		this.imageRemovalDilationX = DEFAULT_Parameters.IMAGE_REMOVAL_DILATION_X_DEFAULT;
		this.imageRemovalDilationY = DEFAULT_Parameters.IMAGE_REMOVAL_DILATION_Y_DEFAULT;

		this.textDilationX = DEFAULT_Parameters.TEXT_REMOVAL_DILATION_X_DEFAULT;
		this.textDilationY = DEFAULT_Parameters.TEXT_REMOVAL_DILATION_Y_DEFAULT;

		setImageSegType(ImageSegType.STRAIGHT_RECT);
		setCombineImages(true);
	}

	public RegionManager getRegionManager() {
		return regionManager;
	}
	
	public ExistingGeometry getExistingGeometry() {
		return existingGeometry;
	}

	public void setExistingGeometry(ExistingGeometry existingGeometry) {
		this.existingGeometry = existingGeometry;
	}
	
	public int getDesiredImageHeight() {
		return desiredImageHeight;
	}

	public int getImageRemovalDilationX() {
		return imageRemovalDilationX;
	}

	public void setImageRemovalDilationX(int imageRemovalDilationX) {
		this.imageRemovalDilationX = imageRemovalDilationX;
	}

	public int getImageRemovalDilationY() {
		return imageRemovalDilationY;
	}

	public void setImageRemovalDilationY(int imageRemovalDilationY) {
		this.imageRemovalDilationY = imageRemovalDilationY;
	}

	public int getTextDilationX() {
		return textDilationX;
	}

	public void setTextDilationX(int textDilationX) {
		this.textDilationX = textDilationX;
	}

	public int getTextDilationY() {
		return textDilationY;
	}

	public void setTextDilationY(int textDilationY) {
		this.textDilationY = textDilationY;
	}

	public double getScaleFactor(int imageHeight) {
		return (double) desiredImageHeight / (double) imageHeight;
	}

	public ImageSegType getImageSegType() {
		return imageSegType;
	}

	public void setImageSegType(ImageSegType imageSegType) {
		this.imageSegType = imageSegType;
	}

	public boolean isCombineImages() {
		return combineImages;
	}

	public void setCombineImages(boolean combineImages) {
		this.combineImages = combineImages;
	}
}