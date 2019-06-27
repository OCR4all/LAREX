package larex.segmentation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import larex.data.MemoryCleaner;
import larex.geometry.regions.Region;
import larex.geometry.regions.RegionSegment;
import larex.geometry.regions.type.PAGERegionType;
import larex.geometry.regions.type.RegionSubType;
import larex.geometry.regions.type.RegionType;

public class RegionClassifier {


	/**
	 * Classify contours depending on the regions they are in
	 * 
	 * @param regions
	 * @param contours
	 * @return
	 */
	public static Collection<RegionSegment> classifyRegions(Set<Region> regions, Collection<MatOfPoint> contours) {
		Collection<RegionSegment> results = new ArrayList<>();
		List<Region> workregions = filterAndOrderRegions(regions);

		List<Candidate> candidates = calcCandidates(workregions, contours);
		for(Region region : workregions) {
			if (region.getMaxOccurances() == -1) {
				// Check max occurrence unbound
				Collection<Candidate> candidateIter = new ArrayList<>(candidates);
				for (Candidate candidate : candidateIter) {
					final Rect rect = candidate.getBoundingRect();

					final boolean isWithinRegion = region.getPositions().stream().anyMatch(
							p -> p.getOpenCVRect().contains(rect.tl()) && p.getOpenCVRect().contains(rect.br()));

					if (rect.area() > region.getMinSize() && isWithinRegion) {
						results.add(new RegionSegment(region.getType(), candidate.getContour()));
						candidates.remove(candidate);
					}
				}
			} else {
				// Check max occurrence one
				Candidate candidate = MaxOccOneFinder.findMaxOccOne(candidates, region);

				if (candidate != null) {
					RegionSegment newResult = new RegionSegment(region.getType(), candidate.getContour());
					results.add(newResult);

					candidates.remove(candidate);
				}
			}
		}
		
		// Clean unused candidates
		for (Candidate candidate : candidates) {
			MemoryCleaner.clean(candidate);
		}

		return results;
	}

	private static int determineMinimumSize(Collection<Region> regions) {
		int minSize = Integer.MAX_VALUE;

		for (Region region : regions) {
			if (region.getMinSize() < minSize) {
				minSize = region.getMinSize();
			}
		}

		return minSize;
	}

	private static List<Candidate> calcCandidates(Collection<Region> regions, Collection<MatOfPoint> contours) {
		ArrayList<Candidate> candidates = new ArrayList<Candidate>();
		int minSize = determineMinimumSize(regions);

		for (final MatOfPoint contour : contours) {
			Rect boundingRect = Imgproc.boundingRect(contour);

			if (boundingRect.area() > minSize) {
				candidates.add(new Candidate(contour, boundingRect));
			}
		}

		return candidates;
	}

	// get rid of ignore and image regions, place maxOcc = 1 regions first and
	// paragraph regions last
	private static List<Region> filterAndOrderRegions(Set<Region> regions) {
		List<Region> processedRegions = new ArrayList<Region>();
		Set<Region> maxOccOne = new HashSet<Region>();
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
		return processedRegions;
	}
}