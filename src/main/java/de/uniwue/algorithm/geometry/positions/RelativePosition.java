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
		final int rectLeft = (int) (Math.floor(image.width * left));
		final int rectTop = (int) (Math.floor(image.height * top));
		final int rectRight = (int) (Math.ceil(image.width * right));
		final int rectBottom = (int) (Math.ceil(image.height * bottom));

		return new Rect(new Point(rectLeft, rectTop), new Point(rectRight, rectBottom));
	}
}