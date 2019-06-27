package larex.segmentation;

import java.util.ArrayList;
import java.util.Collection;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import larex.geometry.positions.RelativePosition;
import larex.geometry.regions.Region;
import larex.geometry.regions.RegionSegment;
import larex.geometry.regions.type.PAGERegionType;
import larex.geometry.regions.type.RegionSubType;
import larex.geometry.regions.type.RegionType;

public class RegionClassifier {

	private Collection<Region> regions;
	private Collection<RegionSegment> results;
	private Collection<Candidate> candidates;

	public RegionClassifier(Collection<Region> regions) {
		preprocessRegions(regions);
		setResults(new ArrayList<RegionSegment>());
	}

	public boolean isWithinRegion(Rect toCheck, Region region) {
		for (RelativePosition position : region.getPositions()) {
			if (position.getOpenCVRect().contains(toCheck.tl()) && position.getOpenCVRect().contains(toCheck.br())) {
				return true;
			}
		}

		return false;
	}

	public Collection<Candidate> checkMaxOccUnbounded(Collection<Candidate> candidates, Region region) {
		ArrayList<Candidate> remainingCandidates = new ArrayList<Candidate>();
		remainingCandidates.addAll(candidates);

		for (Candidate candidate : candidates) {
			Rect rect = candidate.getBoundingRect();

			if (rect.area() > region.getMinSize() && isWithinRegion(rect, region)) {
				RegionSegment newResult = new RegionSegment(region.getType(), candidate.getContour());
				results.add(newResult);
				remainingCandidates.remove(candidate);
			}
		}

		return remainingCandidates;
	}

	public Collection<Candidate> checkMaxOccOne(Collection<Candidate> candidates, Region region) {
		Candidate candidate = MaxOccOneFinder.findMaxOccOne(candidates, region);

		if (candidate != null) {
			RegionSegment newResult = new RegionSegment(region.getType(), candidate.getContour());
			results.add(newResult);

			candidates.remove(candidate);
		}

		return candidates;
	}

	public Collection<RegionSegment> classifyRegions(Collection<MatOfPoint> contours) {
		setCandidates(calcCandidates(contours));

		for(Region region : regions) {
			if (region.getMaxOccurances() == -1) {
				Collection<Candidate> remainingCandidates = checkMaxOccUnbounded(candidates, region);
				setCandidates(remainingCandidates);
			} else {
				setCandidates(checkMaxOccOne(candidates, region));
			}
		}

		return results;
	}

	public int determineMinimumSize() {
		int minSize = Integer.MAX_VALUE;

		for (Region region : regions) {
			if (region.getMinSize() < minSize) {
				minSize = region.getMinSize();
			}
		}

		return minSize;
	}

	public Collection<Candidate> calcCandidates(Collection<MatOfPoint> contours) {
		ArrayList<Candidate> candidates = new ArrayList<Candidate>();
		int minSize = determineMinimumSize();

		for (final MatOfPoint contour : contours) {
			Rect boundingRect = Imgproc.boundingRect(contour);

			if (boundingRect.area() > minSize) {
				Candidate candidate = new Candidate(contour, boundingRect);
				candidates.add(candidate);
			}
		}

		return candidates;
	}

	// get rid of ignore and image regions, place maxOcc = 1 regions first and
	// paragraph regions last
	public void preprocessRegions(Collection<Region> regions) {
		ArrayList<Region> processedRegions = new ArrayList<Region>();
		ArrayList<Region> maxOccOne = new ArrayList<Region>();
		Region paragraphRegion = null;

		for (Region region : regions) {
			if (!region.getType().getType().equals(RegionType.ImageRegion) && 
					!region.getType().equals(new PAGERegionType(RegionType.TextRegion,RegionSubType.ignore))) {
				if (RegionSubType.paragraph.equals(region.getType().getSubtype())) {
					paragraphRegion = region;
				} else {
					if (region.getMaxOccurances() == 1) {
						maxOccOne.add(region);
					} else {
						processedRegions.add(region);
					}
				}
			}
		}

		processedRegions.addAll(maxOccOne);
		processedRegions.add(paragraphRegion);
		setRegions(processedRegions);
	}

	public void setRegions(Collection<Region> regions) {
		this.regions = regions;
	}

	public void setResults(Collection<RegionSegment> results) {
		this.results = results;
	}

	public void setCandidates(Collection<Candidate> candidates) {
		this.candidates = candidates;
	}
}