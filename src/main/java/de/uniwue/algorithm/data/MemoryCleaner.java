package de.uniwue.algorithm.data;

import java.util.Collection;

import org.opencv.core.Mat;

import de.uniwue.algorithm.geometry.PointList;
import de.uniwue.algorithm.segmentation.Candidate;

/**
 * Release the memory of provided objects
 */
public class MemoryCleaner {
	
	/**
	 * Clean the memory of all elements supported elements inside the collection
	 * 
	 * @param elements Collection of elements to clean
	 */
	public static void clean(Collection<?> elements) {
		elements.forEach(c -> {
			if(c instanceof Mat) {
				clean((Mat) c);
			} else if(c instanceof PointList) {
				clean((PointList) c);
			} else if(c instanceof Candidate) {
				clean((PointList) c);
			} else {
				throw new IllegalArgumentException("Can not clean objects of type " + c.getClass().getSimpleName());
			}
		});
	}

	/**
	 * Clean the memory of all mats provided to this function
	 * 
	 * @param mats Mats to clean
	 */
	public static void clean(Mat... mats) {
		for(final Mat mat: mats) {
			if(mat != null) {
				mat.release();
			}
		}
	}
	
	/**
	 * Clean the memory of all pointlists provided to this function
	 * 
	 * @param points PointLists to clean
	 */
	public static void clean(PointList... points) {
		for(PointList point: points) {
			if(point != null) {
				point.clean();
			}
		}
	}

	/**
	 * Clean the memory of all candidates provided to this function
	 * 
	 * @param candidate Candidate to clean
	 */
	public static void clean(Candidate... candidate) {
		for(Candidate point: candidate) {
			if(point != null) {
				point.clean();
			}
		}
	}
}
