package larex.imageProcessing;
//TODO Delete
import org.opencv.core.Mat;

@Deprecated
public class ImageContainerOLD {
	private Mat original;
	
	private Mat resized;
	private Mat binary;
	private Mat gray;
	private Mat edges;
	private Mat inverted;
	
	private Mat pictureRemoved;
		
	public ImageContainerOLD(Mat original) {
		setOriginal(original);
	}

	public Mat getOriginal() {
		return original;
	}

	public void setOriginal(Mat original) {
		this.original = original;
	}

	@Deprecated
	public Mat getResized() {
		return resized;
	}

	@Deprecated
	public void setResized(Mat resized) {
		this.resized = resized;
	}

	public Mat getBinary() {
		return binary;
	}

	public void setBinary(Mat binary) {
		this.binary = binary;
	}

	public Mat getGray() {
		return gray;
	}

	public void setGray(Mat gray) {
		this.gray = gray;
	}

	public Mat getEdges() {
		return edges;
	}

	public void setEdges(Mat edges) {
		this.edges = edges;
	}

	public Mat getInverted() {
		return inverted;
	}

	public void setInverted(Mat inverted) {
		this.inverted = inverted;
	}

	public Mat getPictureRemoved() {
		return pictureRemoved;
	}

	public void setPictureRemoved(Mat pictureRemoved) {
		this.pictureRemoved = pictureRemoved;
	}	
}
