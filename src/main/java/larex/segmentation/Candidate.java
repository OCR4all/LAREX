package larex.segmentation;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;

/**
 * Candidate contour for the classification phase.
 * Contains the contour as well as a bounding rectangle around the contour
 */
public class Candidate {

	private final MatOfPoint contour;
	private final Rect boundingRect;
	
	public Candidate(final MatOfPoint contour, Rect boundingRect) {
		this.contour = contour;
		this.boundingRect = boundingRect;
	}

	/**
	 * Retrieve the contour of the candidate
	 * 
	 * @return
	 */
	public MatOfPoint getContour() {
		return contour;
	}

	/**
	 * Retrieve the bounding rectangle around the contour of the candidate
	 * 
	 * @return
	 */
	public Rect getBoundingRect() {
		return boundingRect;
	}
	
	/**
	 * Release the memory held by this candidates contour
	 */
	public void clean() {
		this.contour.release();
	}
}