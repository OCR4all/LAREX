package larex.segmentation.parameters;

import larex.regions.RegionManager;

public class Parameters {

	// Processing Parameters
	private int desiredImageHeight;
	private int binaryThresh;

	private int imageRemovalDilationX;
	private int imageRemovalDilationY;

	private int textDilationX;
	private int textDilationY;

	// Region Parameters
	private int minImageSize;
	private int minTextSize;
	private int minMarginaliaSize;
	private int minPageNumberSize;

	private boolean marginaliaPresent;
	private boolean pageNumberTop;
	private boolean pageNumberBottom;
	private boolean captionBelow;

	private RegionManager regionManager;

	private double scaleFactor;

	private ImageSegType imageSegType;
	private boolean combineImages;

	public Parameters(RegionManager regionManager, int originalHeight) {
		setRegionManager(regionManager);

		setDesiredImageHeight(DEFAULT_Parameters.getImageHeightDefault());
		setBinaryThresh(DEFAULT_Parameters.getBinaryThreshDefault());

		setImageRemovalDilationX(DEFAULT_Parameters.getImageRemovalDilationXDefault());
		setImageRemovalDilationY(DEFAULT_Parameters.getImageRemovalDilationYDefault());

		setTextDilationX(DEFAULT_Parameters.getTextRemovalDilationXDefault());
		setTextDilationY(DEFAULT_Parameters.getTextRemovalDilationYDefault());

		// TODO Real Image scale
		setScaleFactor((double) desiredImageHeight / originalHeight);

		setImageSegType(ImageSegType.ROTATED_RECT);
		setCombineImages(true);
	}

	public RegionManager getRegionManager() {
		return regionManager;
	}

	public void setRegionManager(RegionManager regionManager) {
		this.regionManager = regionManager;
	}

	public int getDesiredImageHeight() {
		return desiredImageHeight;
	}

	public void setDesiredImageHeight(int desiredImageHeight) {
		this.desiredImageHeight = desiredImageHeight;
	}

	public int getBinaryThresh() {
		return binaryThresh;
	}

	public void setBinaryThresh(int binaryThresh) {
		this.binaryThresh = binaryThresh;
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

	public int getMinImageSize() {
		return minImageSize;
	}

	public void setMinImageSize(int minImageSize) {
		this.minImageSize = minImageSize;
	}

	public int getMinTextSize() {
		return minTextSize;
	}

	public void setMinTextSize(int minTextSize) {
		this.minTextSize = minTextSize;
	}

	public int getMinMarginaliaSize() {
		return minMarginaliaSize;
	}

	public void setMinMarginaliaSize(int minMarginaliaSize) {
		this.minMarginaliaSize = minMarginaliaSize;
	}

	public int getMinPageNumberSize() {
		return minPageNumberSize;
	}

	public void setMinPageNumberSize(int minPageNumberSize) {
		this.minPageNumberSize = minPageNumberSize;
	}

	public boolean isMarginaliaPresent() {
		return marginaliaPresent;
	}

	public void setMarginaliaPresent(boolean marginaliaPresent) {
		this.marginaliaPresent = marginaliaPresent;
	}

	public boolean isPageNumberTop() {
		return pageNumberTop;
	}

	public void setPageNumberTop(boolean pageNumberTop) {
		this.pageNumberTop = pageNumberTop;
	}

	public boolean isPageNumberBottom() {
		return pageNumberBottom;
	}

	public void setPageNumberBottom(boolean pageNumberBottom) {
		this.pageNumberBottom = pageNumberBottom;
	}

	public boolean isCaptionBelow() {
		return captionBelow;
	}

	public void setCaptionBelow(boolean captionBelow) {
		this.captionBelow = captionBelow;
	}

	public double getScaleFactor() {
		return scaleFactor;
	}

	public void setScaleFactor(double scaleFactor) {
		this.scaleFactor = scaleFactor;
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