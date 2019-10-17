package de.uniwue.algorithm.segmentation;

import java.util.ArrayList;
import java.util.Collection;

import org.opencv.core.Rect;
import org.opencv.core.Size;

import de.uniwue.algorithm.geometry.positions.PriorityPosition;
import de.uniwue.algorithm.geometry.positions.RelativePosition;
import de.uniwue.algorithm.geometry.regions.Region;

public class MaxOccOneFinder {

	public static boolean isWithinRegion(Rect toCheck, Region region, Size imageSize) {
		for (RelativePosition position : region.getPositions()) {
			Rect rect = position.getRect(imageSize);
			if (rect.contains(toCheck.tl()) && rect.contains(toCheck.br())) {
				return true;
			}
		}

		return false;
	}
	
	public static Collection<Candidate> checkPositions(Collection<Candidate> candidates, Region region, Size imageSize) {
		ArrayList<Candidate> withinPosition = new ArrayList<Candidate>();
	
		for(Candidate candidate : candidates) {
			if(isWithinRegion(candidate.getBoundingRect(), region, imageSize)) {
				withinPosition.add(candidate);
			}
		}
		
		return withinPosition;
	}
	
	public static Candidate findMaxOccOne(Collection<Candidate> candidates, Region region, Size imageSize) {
		//TODO Remove because rarely used
		candidates = checkPositions(candidates, region, imageSize);
		
		int minSize = region.getMinSize();
		PriorityPosition priorityPosition = region.getPriorityPosition();
		
		if (priorityPosition.equals(PriorityPosition.top)) {
			return calcTopRect(candidates, minSize);
		} else if (priorityPosition.equals(PriorityPosition.bottom)) {
			return calcBottomRect(candidates, minSize);
		} else if (priorityPosition.equals(PriorityPosition.left)) {
			return calcLeftRect(candidates, minSize);
		} else if (priorityPosition.equals(PriorityPosition.right)) {
			return calcRightRect(candidates, minSize);
		}

		return calcTopRect(candidates, minSize);
	}

	public static Candidate calcTopRect(Collection<Candidate> candidates, int minSize) {
		Candidate topCandidate = null;
		int bottomPixelY = Integer.MAX_VALUE;

		for (Candidate candidate : candidates) {
			Rect rect = candidate.getBoundingRect();

			if (rect.area() > minSize && rect.br().y < bottomPixelY) {
				bottomPixelY = (int) rect.br().y;
				topCandidate = candidate;
			}
		}

		return topCandidate;
	}

	public static Candidate calcBottomRect(Collection<Candidate> candidates, int minSize) {
		Candidate bottomCandidate = null;
		int bottomPixelY = -1;

		for (Candidate candidate : candidates) {
			Rect rect = candidate.getBoundingRect();

			if (rect.area() > minSize && rect.tl().y > bottomPixelY) {
				bottomPixelY = (int) rect.tl().y;
				bottomCandidate = candidate;
			}
		}

		return bottomCandidate;
	}

	public static Candidate calcLeftRect(Collection<Candidate> candidates, int minSize) {
		Candidate leftCandidate = null;
		int leftPixelX = Integer.MAX_VALUE;

		for (Candidate candidate : candidates) {
			Rect rect = candidate.getBoundingRect();

			if (rect.area() > minSize && rect.br().x < leftPixelX) {
				leftPixelX = (int) rect.br().x;
				leftCandidate = candidate;
			}
		}

		return leftCandidate;
	}

	public static Candidate calcRightRect(Collection<Candidate> candidates, int minSize) {
		Candidate rightCandidate = null;
		int rightPixelX = -1;

		for (Candidate candidate : candidates) {
			Rect rect = candidate.getBoundingRect();

			if (rect.area() > minSize && rect.tl().x > rightPixelX) {
				rightPixelX = (int) rect.tl().x;
				rightCandidate = candidate;
			}
		}

		return rightCandidate;
	}
}