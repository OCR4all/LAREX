package larex.segmentation;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;

public class Candidate {

	private MatOfPoint contour;
	private Rect boundingRect;
	
	public Candidate(MatOfPoint contour, Rect boundingRect) {
		setContour(contour);
		setBoundingRect(boundingRect);
	}

	public MatOfPoint getContour() {
		return contour;
	}

	public void setContour(MatOfPoint contour) {
		this.contour = contour;
	}

	public Rect getBoundingRect() {
		return boundingRect;
	}

	public void setBoundingRect(Rect boundingRect) {
		this.boundingRect = boundingRect;
	}	
}