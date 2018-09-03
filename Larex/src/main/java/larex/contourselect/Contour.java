package larex.contourselect;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

/**
 * Class representing a Contour.
 */

public class Contour {

	private Rect bounds;
	private MatOfPoint mop;

	/**
	 * Create a contour from matrix of points.
	 *
	 * @param mop
	 *            matrix of points for contour.
	 */
	public Contour(MatOfPoint mop) {
		this.mop = mop;
		bounds = Imgproc.boundingRect(mop);
	}

	// Getter / Setter --------------------------------------------------------

	/*
	 * returns the bounding rect of the contour
	 */
	public Rect getBounds() {
		return bounds;
	}

	/*
	 * sets the bounding rect of the contour
	 */
	public void setBounds(Rect bounds) {
		this.bounds = bounds;
	}

	/*
	 * returns the MatOfPoint of the contour
	 */
	public MatOfPoint getMop() {
		return mop;
	}

	/*
	 * sets the MatOfPoint of the contour
	 */
	public void setMop(MatOfPoint mop) {
		this.mop = mop;
	}

	/*
	 * returns a deep copy of the Contour
	 */
	public Contour deepCopy() {
		MatOfPoint newMOP = new MatOfPoint(mop);
		return new Contour(newMOP);
	}

}
