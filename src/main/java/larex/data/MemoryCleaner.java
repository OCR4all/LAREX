package larex.data;

import java.util.Collection;

import org.opencv.core.Mat;

import larex.geometry.PointList;

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
			} else if(c instanceof Page) {
				clean((Page) c);
			} else if(c instanceof PointList) {
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
	 * Clean the memory of all pages provided to this function
	 * 
	 * @param pages Pages to clean
	 */
	public static void clean(Page... pages) {
		for(Page page: pages) {
			if(page != null) {
				page.clean();
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
}
