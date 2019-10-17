package de.uniwue.algorithm.geometry.positions;

import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;

public class RelativePosition {

	private final double left;
	private final double top;
	private final double right;
	private final double bottom;

	private boolean isFixed;

	public RelativePosition(double left, double top, double right, double bottom) {
		this.left = left;
		this.top = top;
		this.right = right;
		this.bottom = bottom;
	}

	public double left() {
		return left;
	}

	public double top() {
		return top;
	}

	public double right() {
		return right;
	}

	public double bottom() {
		return bottom;
	}

	public boolean isFixed() {
		return isFixed;
	}

	public void setFixed(boolean isFixed) {
		this.isFixed = isFixed;
	}

	public Rect getRect(Size image) {
		double tempTopLeftX = image.width * left;
		double tempTopLeftY = image.height * top;
		double tempBottomRightX = image.width * right;
		double tempBottomRightY = image.height * bottom;

		int topLeftX = (int) (image.width * left);
		int topLeftY = (int) (image.height * top);
		int bottomRightX = (int) (image.width * right);
		int bottomRightY = (int) (image.height * bottom);

		topLeftX = (int) (tempTopLeftX + 1);

		if (topLeftX > image.width - 1) {
			topLeftX = (int) image.width - 1;
		}


		topLeftY = (int) (tempTopLeftY + 1);

		if (topLeftY > image.height - 1) {
			topLeftY = (int) image.height - 1;
		}

		bottomRightX = (int) (tempBottomRightX + 1);

		if (bottomRightX > (int) image.width - 1) {
			bottomRightX = (int) image.width - 1;
		}

		bottomRightY = (int) (tempBottomRightY + 1);

		if (bottomRightY > (int) image.height - 1) {
			bottomRightY = (int) image.height - 1;
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

		Rect rect = new Rect(new Point(topLeftX, topLeftY), new Point(bottomRightX, bottomRightY));

		return rect;
	}
}