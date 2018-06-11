package larex.dataManagement;

import java.io.File;

import org.opencv.core.Mat;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import larex.segmentation.result.SegmentationResult;

/**
 * Maintains the required information for a page.
 */
public class Page {

	private String imagePath;
	private String fileName;

	private Mat original;
	private Mat binary;

	private SegmentationResult segmentationResult;

	private boolean isAccepted;

	/**
	 * Constructor for a Page element.
	 * 
	 * @param imagePath
	 *            The path to the image.
	 * @param identifier
	 *            The identifier of the page which is shown in Gui.
	 */
	public Page(String imagePath) {
		setImagePath(imagePath);

		String fileName = imagePath.substring(imagePath.lastIndexOf(File.separator) + 1, imagePath.lastIndexOf("."));
		setFileName(fileName);
	}

	/**
	 * Initializes a Page element.
	 * 
	 * @param verticalResolution
	 *            The desired vertical resolution of the image.
	 */
	public void initPage() {
		Mat original = Highgui.imread(imagePath);

		Mat binary = new Mat();
		Mat gray = new Mat();
		
		Imgproc.cvtColor(original, gray, Imgproc.COLOR_BGR2GRAY);
		Imgproc.threshold(gray, binary, -1, 255, Imgproc.THRESH_BINARY);

		setOriginal(original);
		setBinary(binary);
	}

	/**
	 * Cleans up a Page element to release memory when no longer needed.
	 */
	public void clean() {
		if (original != null) {
			original.release();
			setOriginal(null);
		}
		if (binary != null) {
			binary.release();
			setBinary(null);
		}
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getImagePath() {
		return imagePath;
	}

	public void setImagePath(String imagePath) {
		this.imagePath = imagePath;
	}

	public Mat getOriginal() {
		return original;
	}

	public void setOriginal(Mat original) {
		this.original = original;
	}

	public Mat getBinary() {
		return binary;
	}

	public void setBinary(Mat binary) {
		this.binary = binary;
	}

	public SegmentationResult getSegmentationResult() {
		return segmentationResult;
	}

	public void setSegmentationResult(SegmentationResult segmentationResult) {
		this.segmentationResult = segmentationResult;
	}

	public boolean isAccepted() {
		return isAccepted;
	}

	public void setAccepted(boolean isAccepted) {
		this.isAccepted = isAccepted;
	}

	/**
	 * Creates a copy of the Page, with a shallow copy of SegmentationResult
	 */
	public Page clone(){
		Page copy = new Page(imagePath);
		copy.setAccepted(isAccepted);
		if(binary != null)
			copy.setBinary(binary.clone());
		copy.setFileName(fileName);
		copy.setImagePath(imagePath);
		if(original != null)
			copy.setOriginal(original.clone());
		if(segmentationResult != null){
			copy.setSegmentationResult(segmentationResult.clone());
		}
		
		return copy;
	}
}