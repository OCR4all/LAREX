package larex.contourselect;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * Represent and process the various regions.
 *
 */
public class Region {

	private List<Point> points; // Cornerpoints in order (without duplicates)
	private MatOfPoint mop; // Cornerpoints in order (without duplicates)

	public List<Point> removedPoints = new LinkedList<Point>(); // For debugging

	private List<Contour> initialContours = new LinkedList<Contour>();

	private LinkedList<Contour> sortedXLeft = new LinkedList<Contour>();
	private LinkedList<Contour> sortedXRight = new LinkedList<Contour>();
	private LinkedList<Contour> sortedYTop = new LinkedList<Contour>();
	private LinkedList<Contour> sortedYBot = new LinkedList<Contour>();

	public Region(LinkedList<Point> points) {
		this.points = points;
		mop.fromList(points);
	}

	public Region(MatOfPoint m) {
		mop = m;
		points = new LinkedList<Point>();
		points = m.toList();
	}

	/**
	 * creates a Region with points in the right Order which surrounds all
	 * Contours bounding boxes.
	 *
	 * @param selectedContours
	 * @param img
	 *            a binary Mat of the original image, only required for size and
	 *            type.
	 */
	public Region(List<Contour> selectedContours, Mat img) {
		System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);

		initialContours = new LinkedList<Contour>(selectedContours);

		final long timeStart = System.currentTimeMillis();
		sortMatOfPoint(selectedContours);
		final long timeEnd = System.currentTimeMillis();

		final long timeStart2 = System.currentTimeMillis();
		points = getSurroundPolyPoints(selectedContours);
		final long timeEnd2 = System.currentTimeMillis();
		// Poly fuellen und darauf findContours anwenden
		List<MatOfPoint> surroundPolyList = new LinkedList<MatOfPoint>();
		MatOfPoint mop = new MatOfPoint();
		mop.fromList(points);
		surroundPolyList.add(mop);

		Mat polyImg = new Mat(img.rows(), img.cols(), img.type());
		// Imgproc.fillPoly(polyImg, surroundPolyList, new Scalar(255,255,255));
		Imgproc.drawContours(polyImg, surroundPolyList, -1, new Scalar(255, 255, 255), -1);
		Mat workMat = polyImg.clone(); // working copy of the binary Matrix
		Mat hierarchy = new Mat();
		List<MatOfPoint> finalContourList = new LinkedList<MatOfPoint>();
		Imgproc.findContours(workMat, finalContourList, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
		if (finalContourList.size() < 1) {
			System.out.println("Error creating a Region.");
			return;
		}
		// Removing Indents
		final long timeStart3 = System.currentTimeMillis();
		List<Point> cleanedUpPoly = removeIndents(finalContourList.get(0).toList(), selectedContours);
		int cDel = finalContourList.get(0).toList().size() - cleanedUpPoly.size();
		System.out.println(cDel + " Elements Removed in first Iteration");
		boolean removedElements = true;
		while (removedElements) {
			removedElements = false;
			int oldSize = cleanedUpPoly.size();
			cleanedUpPoly = removeIndents(cleanedUpPoly, selectedContours);
			int cRemoved = oldSize - cleanedUpPoly.size();
			if (cRemoved > 0) {
				removedElements = true;
				System.out.println("Removed " + cRemoved + " Elements!");
			}
		}
		final long timeEnd3 = System.currentTimeMillis();
		points.clear();
		points.addAll(cleanedUpPoly);
		// for screenshots (without removeIndents
//		points.clear();
//		points.addAll(finalContourList.get(0).toList());
		// points.addAll(finalContourList.get(0).toList()); // without CleanUp
		mop.fromList(points);
	}

	/**
	 * Removes Indents of the poly that are smaller then a threshold (currently double the average Height/Width
	 * of all selected Contours)
	 * @param srcList originally List of Points of the poly, where indents should be removed
	 * @param selectedContours List of the Contours within the region, needed for the threshold calculation
	 * @return List of Cornerpoints of the new poly, which still contains all selected Contours
	 */
	private List<Point> removeIndents(List<Point> srcList, List<Contour> selectedContours) {
		// Get average Width and Height of the selected Contours to check for
		// indents
		// TODO Possible Improvement: Filtering out speckles...
		double avgW = 0.0;
		double avgH = 0.0;
		for (Contour c : selectedContours) {
			Rect r = c.getBounds();
			avgW += r.width;
			avgH += r.height;
		}
		avgW = avgW / selectedContours.size();
		avgH = avgH / selectedContours.size();
		double threshH = avgH * 2;
		double threshW = avgW * 2;

		LinkedList<Point> workList = new LinkedList<Point>(srcList);

		int i = 0;
		while (i < workList.size() && workList.size() >= 3) {

			MatOfPoint baseAreaMOP = new MatOfPoint();
			baseAreaMOP.fromList(workList);
			double baseArea = Imgproc.contourArea(baseAreaMOP);
			// Poly without Point i
			LinkedList<Point> tmpList = new LinkedList<Point>(workList); // (LinkedList<Point>)
																			// workList.clone();
			tmpList.remove(i);
			MatOfPoint newAreaMOP = new MatOfPoint();
			newAreaMOP.fromList(tmpList);
			double newArea = Imgproc.contourArea(newAreaMOP);
			// Checking for indents if the new Area is bigger then the baseArea
			if (baseArea < newArea) {
				Point p2 = workList.get(i); // Point to be removed maybe
				Point p1, p3;
				if (i > 0)
					p1 = workList.get(i - 1);
				else
					p1 = workList.getLast(); // Case i == 0
				if (i < workList.size() - 1)
					p3 = workList.get(i + 1);
				else
					p3 = workList.getFirst(); // Case i is last Element in
												// workList
				// Check height or width of the Indent
				if (Math.abs(p1.x - p3.x) < threshW) {
					workList.remove(p2);
					removedPoints.add(p2);
				} else if (Math.abs(p1.y - p3.y) < threshH) {
					workList.remove(p2);
					removedPoints.add(p2);
				} else
					i++;
			} else
				i++;
		}
		return workList;
	}

	/**
	 * Looking extreme points of contours, to create new lines that run close to
	 * the contours. Shrinks the regions closely to the contours. needs the
	 * sorted Lists to be initiated already
	 *
	 * @param selectedContours
	 */
	private LinkedList<Point> getSurroundPolyPoints(List<Contour> selectedContours) {

		// Sorting if not done already (not nice)
		if (sortedXLeft.size() != initialContours.size())
			sortMatOfPoint(selectedContours);

		double minX = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;

		for (Contour c : selectedContours) {
			Rect rTemp = c.getBounds();
			minX = Math.min(minX, rTemp.tl().x);
			minY = Math.min(minY, rTemp.tl().y);
			maxX = Math.max(maxX, rTemp.br().x);
			maxY = Math.max(maxY, rTemp.br().y);
		}

		// 3rd Algo (toCheck List)
		LinkedList<Point> res = new LinkedList<Point>();
		LinkedList<Contour> toCheck = new LinkedList<Contour>();

		// Beginn Top side
		// ================================================================

		// First element separate (so we can work with res.getLast from now on
		double lineMinYFirst = Double.POSITIVE_INFINITY;
		for (Contour c : sortedXLeft) {
			Rect tmpBB = c.getBounds();
			if (tmpBB.tl().x > minX)
				break;
			if (tmpBB.tl().x <= minX && tmpBB.br().x >= minX) {
				lineMinYFirst = Math.min(lineMinYFirst, tmpBB.tl().y);
			}
		}
		res.add(new Point(minX, lineMinYFirst));
		for (double i = minX; i < maxX; i++) {
			// remove obscolent Elements from toCheck List
			// So elements that end left to the current X value
			List<Contour> toRemove = new LinkedList<Contour>();
			for (Contour c : toCheck) {
				if (c.getBounds().br().x < i)
					toRemove.add(c);
			}
			toCheck.removeAll(toRemove);
			// Add new relevant Elemtens to toCheck List
			// So elements that start left to the current X value
			boolean chk = true;
			while (chk && !sortedXLeft.isEmpty()) {
				Rect mop = sortedXLeft.getFirst().getBounds();
				if (mop.tl().x <= i) {
					if (mop.br().x >= i)
						toCheck.add(sortedXLeft.removeFirst());
					else
						sortedXLeft.removeFirst(); // ending left from i
				} else {
					chk = false;
				}
			}
			double lineMinY = Double.POSITIVE_INFINITY;
			// toCheck now only contains Elements that start left to i and end
			// right side
			for (Contour tmpCon : toCheck) {
				lineMinY = Math.min(lineMinY, tmpCon.getBounds().tl().y);
			}
			if (Double.isFinite(lineMinY) && res.getLast().y != lineMinY) {
				res.add(new Point(i, res.getLast().y));

				res.add(new Point(i, lineMinY));

			}
		}

		// Beginn Right side
		// =============================================================
		toCheck.clear();
		Point lpt = res.getLast(); // Last point Top side. Startpoint for right
									// side
		for (double i = lpt.y; i < maxY; i++) {
			// remove obscolent Elements from toCheck List
			// So elements that end below the current Y value
			List<Contour> toRemove = new LinkedList<Contour>();
			for (Contour c : toCheck) {
				if (c.getBounds().br().y < i)
					toRemove.add(c);
			}
			toCheck.removeAll(toRemove);
			// Add new relevant Elemtens to toCheck List
			// So elements that start above the current Y value
			boolean chk = true;
			while (chk && !sortedYTop.isEmpty()) {
				Rect mop = sortedYTop.getFirst().getBounds();
				if (mop.tl().y <= i) {
					if (mop.br().y >= i)
						toCheck.add(sortedYTop.removeFirst());
					else
						sortedYTop.removeFirst(); // ending above i
				} else {
					chk = false;
				}
			}
			double lineMaxX = Double.NEGATIVE_INFINITY;
			// toCheck now only contains Elements that start left to i and end
			// right side
			for (Contour tmpCon : toCheck) {
				lineMaxX = Math.max(lineMaxX, tmpCon.getBounds().br().x);
			}
			if (Double.isFinite(lineMaxX) && res.getLast().x != lineMaxX) {
				res.add(new Point(res.getLast().x, i));

				res.add(new Point(lineMaxX, i));

			}
		}

		// Beginn Bottom side
		// =====================================================================
		toCheck.clear();
		Point lpl = res.getLast(); // Last point Left side. Startpoint for
									// bottom side
		for (double i = lpl.x; i > minX; i--) {
			// remove obscolent Elements from toCheck List
			// So elements that start right to the current X value
			List<Contour> toRemove = new LinkedList<Contour>();
			for (Contour c : toCheck) {
				if (c.getBounds().tl().x > i)
					toRemove.add(c);
			}
			toCheck.removeAll(toRemove);
			// Add new relevant Elemtens to toCheck List
			// So elements that end right to the current X value
			boolean chk = true;
			while (chk && !sortedXRight.isEmpty()) {
				Rect mop = sortedXRight.getFirst().getBounds();
				if (mop.br().x >= i) {
					if (mop.tl().x <= i)
						toCheck.add(sortedXRight.removeFirst());
					else
						sortedXRight.removeFirst(); // starting right from i
				} else {
					chk = false;
				}
			}
			double lineMaxY = Double.NEGATIVE_INFINITY;
			// toCheck now only contains Elements that start left to i and end
			// right side
			for (Contour tmpCon : toCheck) {
				lineMaxY = Math.max(lineMaxY, tmpCon.getBounds().br().y);
			}
			if (Double.isFinite(lineMaxY) && res.getLast().y != lineMaxY) {
				res.add(new Point(i, res.getLast().y));

				res.add(new Point(i, lineMaxY));

			}
		}

		// Beginn Left side
		// ===================================================================
		toCheck.clear();
		Point lpb = res.getLast(); // Last point Bottom side. Startpoint for
									// left side
		for (double i = lpb.y; i > minY; i--) {
			// remove obscolent Elements from toCheck List
			// So elements that start below the current Y value
			List<Contour> toRemove = new LinkedList<Contour>();
			for (Contour c : toCheck) {
				if (c.getBounds().tl().y > i)
					toRemove.add(c);
			}
			toCheck.removeAll(toRemove);
			// Add new relevant Elemtens to toCheck List
			// So elements that end below the current Y value
			boolean chk = true;
			while (chk && !sortedYBot.isEmpty()) {
				Rect mop = sortedYBot.getFirst().getBounds();
				if (mop.br().y >= i) {
					if (mop.tl().y <= i)
						toCheck.add(sortedYBot.removeFirst());
					else
						sortedYBot.removeFirst();
				} else {
					chk = false;
				}
			}
			double lineMinX = Double.POSITIVE_INFINITY;

			for (Contour tmpCon : toCheck) {
				lineMinX = Math.min(lineMinX, tmpCon.getBounds().tl().x);
			}
			if (Double.isFinite(lineMinX) && res.getLast().x != lineMinX) {
				res.add(new Point(res.getLast().x, i));

				res.add(new Point(lineMinX, i));

			}
		}
		return res;
	}

	/**
	 * Sorted four lists sortedXLeft,sortedXRight,sortedYTop,sortedYBot,
	 * according to x,y coordinates.
	 *
	 * @param selectedContours
	 *            contains amount of Image contours.
	 */
	public void sortMatOfPoint(List<Contour> selectedContours) {
		sortedXLeft.clear();
		sortedXRight.clear();
		sortedYTop.clear();
		sortedYBot.clear();

		for (Contour c : selectedContours) {
			sortedXLeft.add(c);
			sortedXRight.add(c);
			sortedYTop.add(c);
			sortedYBot.add(c);
		}

		sortedXLeft.sort(new Comparator<Contour>() {
			@Override
			public int compare(Contour c1, Contour c2) {
				return Double.compare(c1.getBounds().tl().x, c2.getBounds().tl().x);
			}
		});

		sortedYTop.sort(new Comparator<Contour>() {
			@Override
			public int compare(Contour c1, Contour c2) {
				return Double.compare(c1.getBounds().tl().y, c2.getBounds().tl().y);
			}
		});

		sortedXRight.sort(new Comparator<Contour>() {
			@Override
			public int compare(Contour c1, Contour c2) {
				return -1 * Double.compare(c1.getBounds().br().x, c2.getBounds().br().x);
			}
		});

		sortedYBot.sort(new Comparator<Contour>() {
			@Override
			public int compare(Contour c1, Contour c2) {
				return -1 * Double.compare(c1.getBounds().br().y, c2.getBounds().br().y);
			}
		});
	}

	// Getter / Setter --------------------------------------------------------

	/**
	 * 
	 * @return a deep copy of the region
	 */
	public Region deepCopy() {
		MatOfPoint newMOP = new MatOfPoint();
		newMOP.fromList(points);
		Region r = new Region(newMOP);

		List<Contour> copyIniCon = new LinkedList<Contour>();
		for (Contour c : initialContours) {
			copyIniCon.add(c.deepCopy());
		}

		r.setInitialContours(copyIniCon);

		return r;
	}

	/**
	 * 
	 * @return MatOfPoint of the Region
	 */
	public MatOfPoint getRegionMOP() {
		MatOfPoint res = new MatOfPoint();
		res.fromList(points);
		return res;
	}

	/**
	 * 
	 * @param p point to be removed of the region poly
	 */
	public boolean deletePoint(Point p) {
		if (p == null)
			return false;
		return points.remove(p);
	}

	/**
	 * 
	 * @return List of Points of the regions poly
	 */
	public List<Point> getPoints() {
		return points;
	}

	/**
	 * sets the points of the region
	 */
	public void setPoints(LinkedList<Point> points) {
		this.points = points;
	}

	/**
	 * 
	 * @return List of the initial contours which created the region
	 */
	public List<Contour> getInitialContours() {
		return initialContours;
	}

	/**
	 * sets the initial Contours
	 * @param initialContours
	 */
	public void setInitialContours(List<Contour> initialContours) {
		this.initialContours = initialContours;
	}

}
