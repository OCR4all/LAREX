package larex.geometry.positions;

import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;

public class RelativePosition {

	private double topLeftXPercentage;
	private double topLeftYPercentage;
	private double bottomRightXPercentage;
	private double bottomRightYPercentage;

	private boolean isFixed;

	private boolean roundedUpTopLeftX;
	private boolean roundedUpTopLeftY;
	private boolean roundedUpBottomRightX;
	private boolean roundedUpBottomRightY;

	private Rect openCVRect;

	public RelativePosition(double topLeftXPercentage, double topLeftYPercentage, double bottomRightXPercentage,
			double bottomRightYPercentage) {
		setTopLeftXPercentage(topLeftXPercentage);
		setTopLeftYPercentage(topLeftYPercentage);
		setBottomRightXPercentage(bottomRightXPercentage);
		setBottomRightYPercentage(bottomRightYPercentage);
	}

	public RelativePosition(Rect rect, Size image) {
		updateRect(rect, image);
	}

	public void updateRect(Rect rect, Size image) {
		this.openCVRect = rect;
		calcPercentages(image);
	}

	public void calcPercentages(Size image) {
		if (openCVRect != null) {
			double topLeftX = openCVRect.x;
			double topLeftY = openCVRect.y;
			double bottomRightX = openCVRect.br().x;
			double bottomRightY = openCVRect.br().y;

			double tempTopLeftXPerc = topLeftX / image.width;
			double tempTopLeftYPerc = topLeftY / image.height;
			double tempBottomRightXPerc = bottomRightX / image.width;
			double tempBottomRightYPerc = bottomRightY / image.height;

			if (Math.abs(tempTopLeftXPerc - topLeftXPercentage) > 0.01) {
				setTopLeftXPercentage(tempTopLeftXPerc);
			}
			if (Math.abs(tempTopLeftYPerc - topLeftYPercentage) > 0.01) {
				setTopLeftYPercentage(tempTopLeftYPerc);
			}
			if (Math.abs(tempBottomRightXPerc - bottomRightXPercentage) > 0.01) {
				setBottomRightXPercentage(tempBottomRightXPerc);
			}
			if (Math.abs(tempBottomRightYPerc - bottomRightYPercentage) > 0.01) {
				setBottomRightYPercentage(tempBottomRightYPerc);
			}
		}
	}

	public Rect calcRect(Size image) {
		double tempTopLeftX = image.width * topLeftXPercentage;
		double tempTopLeftY = image.height * topLeftYPercentage;
		double tempBottomRightX = image.width * bottomRightXPercentage;
		double tempBottomRightY = image.height * bottomRightYPercentage;

		int topLeftX = (int) (image.width * topLeftXPercentage);
		int topLeftY = (int) (image.height * topLeftYPercentage);
		int bottomRightX = (int) (image.width * bottomRightXPercentage);
		int bottomRightY = (int) (image.height * bottomRightYPercentage);

		if (roundedUpTopLeftX) {
			roundedUpTopLeftX = false;
		} else {
			topLeftX = (int) (tempTopLeftX + 1);

			if (topLeftX > image.width - 1) {
				topLeftX = (int) image.width - 1;
			}

			roundedUpTopLeftX = true;
		}

		if (roundedUpTopLeftY) {
			roundedUpTopLeftY = false;
		} else {
			topLeftY = (int) (tempTopLeftY + 1);

			if (topLeftY > image.height - 1) {
				topLeftY = (int) image.height - 1;
			}

			roundedUpTopLeftY = true;
		}

		if (roundedUpBottomRightX) {
			roundedUpBottomRightX = false;
		} else {
			bottomRightX = (int) (tempBottomRightX + 1);

			if (bottomRightX > (int) image.width - 1) {
				bottomRightX = (int) image.width - 1;
			}

			roundedUpBottomRightX = true;
		}

		if (roundedUpBottomRightY) {
			roundedUpBottomRightY = false;
		} else {
			bottomRightY = (int) (tempBottomRightY + 1);

			if (bottomRightY > (int) image.height - 1) {
				bottomRightY = (int) image.height - 1;
			}

			roundedUpBottomRightY = true;
		}

		// even out rounding errors at the outer border of the image
		if (topLeftX == 1) {
			topLeftX = 0;
		}

		if (topLeftY == 1) {
			topLeftY = 0;
		}

		if (bottomRightX == image.width - 2) {
			bottomRightX = (int) image.width - 1;
		}

		if (bottomRightY == image.height - 2) {
			bottomRightY = (int) image.height - 1;
		}

		// argh
		if (topLeftXPercentage < 0.05) {
			topLeftXPercentage = 0;
		}
		
		if (topLeftYPercentage < 0.05) {
			topLeftYPercentage = 0;
		}
		
		if (bottomRightXPercentage > 0.95) {
			bottomRightXPercentage = 1;
		}
		
		if (bottomRightYPercentage > 0.95) {
			bottomRightYPercentage = 1;
		}
		
		Rect rect = new Rect(new Point(topLeftX, topLeftY), new Point(bottomRightX, bottomRightY));

		return rect;
	}

	public double getTopLeftXPercentage() {
		return topLeftXPercentage;
	}

	public void setTopLeftXPercentage(double topLeftXPercentage) {
		this.topLeftXPercentage = topLeftXPercentage;
	}

	public double getTopLeftYPercentage() {
		return topLeftYPercentage;
	}

	public void setTopLeftYPercentage(double topLeftYPercentage) {
		this.topLeftYPercentage = topLeftYPercentage;
	}

	public double getBottomRightXPercentage() {
		return bottomRightXPercentage;
	}

	public void setBottomRightXPercentage(double bottomRightXPercentage) {
		this.bottomRightXPercentage = bottomRightXPercentage;
	}

	public double getBottomRightYPercentage() {
		return bottomRightYPercentage;
	}

	public void setBottomRightYPercentage(double bottomRightYPercentage) {
		this.bottomRightYPercentage = bottomRightYPercentage;
	}

	public boolean isFixed() {
		return isFixed;
	}

	public void setFixed(boolean isFixed) {
		this.isFixed = isFixed;
	}

	public Rect getOpenCVRect() {
		return openCVRect;
	}
}