package larex.dataManagement;

import java.io.File;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;

import larex.segmentation.ImageProcessor;
import larex.segmentation.result.SegmentationResult;

/**
 * Maintains the required information for a page.
 */
public class Page {

	private String imagePath;
	private String fileName;
	private String identifier;

	private Mat original;
	@Deprecated
	private Mat resized;
	private Mat binary;
	private Mat result;

	private double scaleFactor;
	private Size originalSize;

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
	public Page(String imagePath, String identifier) {
		setImagePath(imagePath);

		String fileName = imagePath.substring(imagePath.lastIndexOf(File.separator) + 1, imagePath.lastIndexOf("."));
		setFileName(fileName);

		setIdentifier(identifier);
	}

	/**
	 * Visualizes the segmentation result.
	 */
	@Deprecated
	public void visualizeSegmentation() {
		Mat result = original.clone();
		result = segmentationResult.drawResult(result, resized);
		setResult(result);
	}

	/**
	 * Initializes a Page element.
	 * 
	 * @param verticalResolution
	 *            The desired vertical resolution of the image.
	 */
	public void initPage() {
		Mat original = Highgui.imread(imagePath);
//		Mat resized = ImageProcessor.resize(original, verticalResolution);
		// TODO besser
//		setScaleFactor((double) resized.height() / (double) original.height());

		setOriginal(original);
		setResized(resized);
		setOriginalSize(original.size());

		if (segmentationResult != null) {
			visualizeSegmentation();
		}
	}

	/**
	 * Cleans up a Page element to release memory when no longer needed.
	 */
	public void clean() {
		if (original != null) {
			original.release();
			setOriginal(null);
		}
		if (resized != null) {
			resized.release();
			setResized(null);
		}
		if (binary != null) {
			binary.release();
			setBinary(null);
		}
		if (result != null) {
			result.release();
			setResult(null);
		}
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getIdentifier() {
		return identifier;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
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

	public double getScaleFactor() {
		return scaleFactor;
	}

	public void setScaleFactor(double scaleFactor) {
		this.scaleFactor = scaleFactor;
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

	public Mat getResult() {
		return result;
	}

	public void setResult(Mat result) {
		this.result = result;
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

	public Size getOriginalSize() {
		return originalSize;
	}

	public void setOriginalSize(Size originalSize) {
		this.originalSize = originalSize;
	}
}