package de.uniwue.web.io;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;


public class ImageLoader {

	/**
	 * Load an image in its original color space
	 * 
	 * @param filepath File path of a image
	 * @return Mat of the loaded image
	 */
	public static Mat readOriginal(File filepath) {
		Mat image = null;
		if(filepath.isFile()) {
			image = Imgcodecs.imread(filepath.getAbsolutePath());
		}
		if(image == null) {
			throw new IllegalArgumentException("No image exists at "+filepath.getAbsolutePath());
		} else {
			return image;
		}
	}

	/**
	 * Load an image as grayscale
	 * 
	 * @param filepath File path of a image
	 * @return Mat of the loaded image
	 */
	public static Mat readGray(File filepath) {
		Mat grayscale = null;
		if(filepath.isFile()) {
			grayscale = Imgcodecs.imread(filepath.getAbsolutePath(),Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);
		}
		if(grayscale == null) {
			throw new IllegalArgumentException("No image exists at "+filepath.getAbsolutePath());
		} else {
			return grayscale;
		}
	}

	/**
	 * Read the dimensions of a Image file without loading the whole image file.
	 * 
	 * @param filepath File path of a image
	 * @return Size with image dimensions
	 */
	public static Size readDimensions(File filepath) {
		String name = filepath.getName();
		if(filepath.isFile()) {
			String extension = name.substring(name.lastIndexOf(".") + 1);
			try(ImageInputStream in = ImageIO.createImageInputStream(filepath)){
				Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(extension);
				if(readers.hasNext()) {
					ImageReader reader = readers.next();
					reader.setInput(in);
					return new Size(reader.getWidth(0),reader.getHeight(0));
				}
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		throw new IllegalArgumentException("No image exists at "+filepath.getAbsolutePath());
	}
}
