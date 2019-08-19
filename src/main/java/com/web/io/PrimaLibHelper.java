package com.web.io;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.primaresearch.dla.page.layout.physical.Region;

/**
 * Helper Class for functionality in the PrimA PageXML lib to simplify some
 * calls
 */
public class PrimaLibHelper {

	/**
	 * Set the orientation of a region. This method helps with functions like
	 * "Page.getOrderedRegions" and "Page.getRegions" that return a list of
	 * "Region". Since setOrientation is defined in every SubRegion but
	 * "CustomRegion", "NoiseRegion", "RegionImpl" and "Region" itself.
	 * 
	 * @param region
	 * @param orientation
	 */
	public static void setOrientation(Region region, double orientation)
			throws UnsupportedOperationException, RuntimeException {
		/*
		 * Normally not recommended, but another option would be a 12 cases if-else
		 * thats not robust against changes (e.g. Additions of Regions) and more error
		 * prone
		 * 
		 * !But attention! This method will break if the "setOrientation" name changes
		 * (which isn't too likely, since it is a core function of the library)
		 */
		try {
			Method getOrientation = region.getClass().getMethod("setOrientation", new Class[] {double.class});
			getOrientation.invoke(region, new Object[] {orientation});
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
			throw new UnsupportedOperationException(
					"The region of type '" + region.getClass().getName() + "' does not have an orientation to set.");
		} catch (SecurityException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
			throw new RuntimeException(
					"Unable to access the getOrientation Method. PrimA Library must have been changed.");
		}
	}

	/**
	 * Get the orientation of a region. This method helps with functions like
	 * "Page.getOrderedRegions" and "Page.getRegions" that return a list of
	 * "Region". Since getOrientation is defined in every SubRegion but
	 * "CustomRegion", "NoiseRegion", "RegionImpl" and "Region" itself.
	 * 
	 * @param region
	 * @param orientation
	 */
	public static Double getOrientation(Region region) throws UnsupportedOperationException, RuntimeException {
		/*
		 * Normally not recommended, but another option would be a 12 cases if-else
		 * thats not robust against changes (e.g. Additions of Regions) and more error
		 * prone
		 * 
		 * !But attention! This method will break if the "getOrientation" name changes
		 * (which isn't too likely, since it is a core function of the library)
		 */
		try {
			Method getOrientation = region.getClass().getMethod("getOrientation");
			return (double) getOrientation.invoke(region, new Object[] {});
		} catch (NoSuchMethodException e) {
			throw new UnsupportedOperationException(
					"The region of type '" + region.getClass().getName() + "' does not have an orientation to get.");
		} catch (InvocationTargetException e) {
			if(e.getCause() instanceof NullPointerException) {
				// Catch NullPointer in prima-core-libs. "No orientation defined in the pagexml"
				return null;
			} else {
				throw new RuntimeException(
						"Unable to access the getOrientation Method. PrimA Library must have been changed.");
			}
		} catch (SecurityException | IllegalArgumentException | IllegalAccessException  e) {
			throw new RuntimeException(
					"Unable to access the getOrientation Method. PrimA Library must have been changed.");
		}
	}
}
