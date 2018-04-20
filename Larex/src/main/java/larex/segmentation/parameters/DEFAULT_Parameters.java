package larex.segmentation.parameters;

public class DEFAULT_Parameters {

	// DEFAULT Processing Parameters
	private static final int IMAGE_HEIGHT_DEFAULT = 1600;
	private static final int IMAGE_HEIGHT_MIN = 800;
	private static final int IMAGE_HEIGHT_MAX = 3000;
	private static final int IMAGE_HEIGHT_STEPSIZE = 100;

	private static final int BINARY_THRESH_DEFAULT = -1;
	private static final int BINARY_THRESH_MIN = -1;
	private static final int BINARY_THRESH_MAX = 255;
	private static final int BINARY_THRESH_STEPSIZE = 1;

	private static final int IMAGE_REMOVAL_DILATION_X_DEFAULT = 1;
	private static final int IMAGE_REMOVAL_DILATION_X_MIN = 0;
	private static final int IMAGE_REMOVAL_DILATION_X_MAX = 100;
	private static final int IMAGE_REMOVAL_DILATION_X_STEPSIZE = 1;

	private static final int IMAGE_REMOVAL_DILATION_Y_DEFAULT = 1;
	private static final int IMAGE_REMOVAL_DILATION_Y_MIN = 0;
	private static final int IMAGE_REMOVAL_DILATION_Y_MAX = 100;
	private static final int IMAGE_REMOVAL_DILATION_Y_STEPSIZE = 1;

	private static final int TEXT_REMOVAL_DILATION_X_DEFAULT = 20;
	private static final int TEXT_REMOVAL_DILATION_X_MIN = 1;
	private static final int TEXT_REMOVAL_DILATION_X_MAX = 100;
	private static final int TEXT_REMOVAL_DILATION_X_STEPSIZE = 1;

	private static final int TEXT_REMOVAL_DILATION_Y_DEFAULT = 15;
	private static final int TEXT_REMOVAL_DILATION_Y_MIN = 1;
	private static final int TEXT_REMOVAL_DILATION_Y_MAX = 100;
	private static final int TEXT_REMOVAL_DILATION_Y_STEPSIZE = 1;

	// DEFAULT Region Parameters
	private static final int IMAGE_MIN_SIZE_DEFAULT = 3000;
	private static final int IMAGE_MIN_SIZE_MIN = 1;
	private static final int IMAGE_MIN_SIZE_MAX = 999000;
	private static final int IMAGE_MIN_SIZE_STEPSIZE = 100;

	private static final int PARAGRAPH_MIN_SIZE_DEFAULT = 2000;
	private static final int PARAGRAPH_MIN_SIZE_MIN = 1;
	private static final int PARAGRAPH_MIN_SIZE_MAX = 999000;
	private static final int PARAGRAPH_MIN_SIZE_STEPSIZE = 100;

	private static final int MARGINALIA_MIN_SIZE_DEFAULT = 2000;
	private static final int MARGINALIA_MIN_SIZE_MIN = 1;
	private static final int MARGINALIA_MIN_SIZE_MAX = 999000;
	private static final int MARGINALIA_MIN_SIZE_STEPSIZE = 100;
	private static final int MARGINALIA_LEFT_RIGHT_PERCENTAGE = 25;

	private static final int PAGE_NUMBER_MIN_SIZE_DEFAULT = 1500;
	private static final int PAGE_NUMBER_MIN_SIZE_MIN = 1;
	private static final int PAGE_NUMBER_MIN_SIZE_MAX = 999000;
	private static final int PAGE_NUMBER_MIN_SIZE_STEPSIZE = 50;
	private static final int PAGE_NUMBER_TOP_BOTTOM_PERCENTAGE = 15;

	public static int getImageHeightDefault() {
		return IMAGE_HEIGHT_DEFAULT;
	}

	public static int getImageHeightMin() {
		return IMAGE_HEIGHT_MIN;
	}

	public static int getImageHeightMax() {
		return IMAGE_HEIGHT_MAX;
	}

	public static int getImageHeightStepsize() {
		return IMAGE_HEIGHT_STEPSIZE;
	}

	public static int getBinaryThreshDefault() {
		return BINARY_THRESH_DEFAULT;
	}

	public static int getBinaryThreshMin() {
		return BINARY_THRESH_MIN;
	}

	public static int getBinaryThreshMax() {
		return BINARY_THRESH_MAX;
	}

	public static int getBinaryThreshStepsize() {
		return BINARY_THRESH_STEPSIZE;
	}

	public static int getImageRemovalDilationXDefault() {
		return IMAGE_REMOVAL_DILATION_X_DEFAULT;
	}

	public static int getImageRemovalDilationXMin() {
		return IMAGE_REMOVAL_DILATION_X_MIN;
	}

	public static int getImageRemovalDilationXMax() {
		return IMAGE_REMOVAL_DILATION_X_MAX;
	}

	public static int getImageRemovalDilationXStepsize() {
		return IMAGE_REMOVAL_DILATION_X_STEPSIZE;
	}

	public static int getImageRemovalDilationYDefault() {
		return IMAGE_REMOVAL_DILATION_Y_DEFAULT;
	}

	public static int getImageRemovalDilationYMin() {
		return IMAGE_REMOVAL_DILATION_Y_MIN;
	}

	public static int getImageRemovalDilationYMax() {
		return IMAGE_REMOVAL_DILATION_Y_MAX;
	}

	public static int getImageRemovalDilationYStepsize() {
		return IMAGE_REMOVAL_DILATION_Y_STEPSIZE;
	}

	public static int getTextRemovalDilationXDefault() {
		return TEXT_REMOVAL_DILATION_X_DEFAULT;
	}

	public static int getTextRemovalDilationXMin() {
		return TEXT_REMOVAL_DILATION_X_MIN;
	}

	public static int getTextRemovalDilationXMax() {
		return TEXT_REMOVAL_DILATION_X_MAX;
	}

	public static int getTextRemovalDilationXStepsize() {
		return TEXT_REMOVAL_DILATION_X_STEPSIZE;
	}

	public static int getTextRemovalDilationYDefault() {
		return TEXT_REMOVAL_DILATION_Y_DEFAULT;
	}

	public static int getTextRemovalDilationYMin() {
		return TEXT_REMOVAL_DILATION_Y_MIN;
	}

	public static int getTextRemovalDilationYMax() {
		return TEXT_REMOVAL_DILATION_Y_MAX;
	}

	public static int getTextRemovalDilationYStepsize() {
		return TEXT_REMOVAL_DILATION_Y_STEPSIZE;
	}

	public static int getImageMinSizeDefault() {
		return IMAGE_MIN_SIZE_DEFAULT;
	}

	public static int getImageMinSizeMin() {
		return IMAGE_MIN_SIZE_MIN;
	}

	public static int getImageMinSizeMax() {
		return IMAGE_MIN_SIZE_MAX;
	}

	public static int getImageMinSizeStepsize() {
		return IMAGE_MIN_SIZE_STEPSIZE;
	}

	public static int getParagraphMinSizeDefault() {
		return PARAGRAPH_MIN_SIZE_DEFAULT;
	}

	public static int getParagraphMinSizeMin() {
		return PARAGRAPH_MIN_SIZE_MIN;
	}

	public static int getParagraphMinSizeMax() {
		return PARAGRAPH_MIN_SIZE_MAX;
	}

	public static int getParagraphMinSizeStepsize() {
		return PARAGRAPH_MIN_SIZE_STEPSIZE;
	}

	public static int getMarginaliaMinSizeDefault() {
		return MARGINALIA_MIN_SIZE_DEFAULT;
	}

	public static int getMarginaliaMinSizeMin() {
		return MARGINALIA_MIN_SIZE_MIN;
	}

	public static int getMarginaliaMinSizeMax() {
		return MARGINALIA_MIN_SIZE_MAX;
	}

	public static int getMarginaliaMinSizeStepsize() {
		return MARGINALIA_MIN_SIZE_STEPSIZE;
	}

	public static int getMarginaliaLeftRightPercentage() {
		return MARGINALIA_LEFT_RIGHT_PERCENTAGE;
	}

	public static int getPageNumberMinSizeDefault() {
		return PAGE_NUMBER_MIN_SIZE_DEFAULT;
	}

	public static int getPageNumberMinSizeMin() {
		return PAGE_NUMBER_MIN_SIZE_MIN;
	}

	public static int getPageNumberMinSizeMax() {
		return PAGE_NUMBER_MIN_SIZE_MAX;
	}

	public static int getPageNumberMinSizeStepsize() {
		return PAGE_NUMBER_MIN_SIZE_STEPSIZE;
	}

	public static int getPageNumberTopBottomPercentage() {
		return PAGE_NUMBER_TOP_BOTTOM_PERCENTAGE;
	}

}