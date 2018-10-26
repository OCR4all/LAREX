package larex.dataManagement;

import java.io.File;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
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

	/**
	 * Constructor for a Page element.
	 * 
	 * @param imagePath
	 *            The path to the image.
	 * @param identifier
	 *            The identifier of the page which is shown in Gui.
	 */
	public Page(String imagePath) {
		this.imagePath = imagePath;

		String fileName = imagePath.substring(imagePath.lastIndexOf(File.separator) + 1, imagePath.lastIndexOf("."));
		this.fileName = fileName;
	}

	/**
	 * Initializes a Page element.
	 */
	public void initPage() {
		this.original = Imgcodecs.imread(imagePath);

		this.binary = new Mat();
		Mat gray = new Mat();
		
		Imgproc.cvtColor(original, gray, Imgproc.COLOR_BGR2GRAY);
		Imgproc.threshold(gray, binary, -1, 255, Imgproc.THRESH_BINARY);
	}

	/**
	 * Cleans up a Page element to release memory when no longer needed.
	 */
	public void clean() {
		if (this.original != null) {
			this.original.release();
			this.original = null;
		}
		if (this.binary != null) {
			this.binary.release();
			this.binary = null;
		}
	}

	public String getFileName() {
		return fileName;
	}

	public String getImagePath() {
		return imagePath;
	}

	public Mat getOriginal() {
		return original;
	}

	public Mat getBinary() {
		return binary;
	}

	public SegmentationResult getSegmentationResult() {
		return segmentationResult;
	}

	public void setSegmentationResult(SegmentationResult segmentationResult) {
		this.segmentationResult = segmentationResult;
	}
}