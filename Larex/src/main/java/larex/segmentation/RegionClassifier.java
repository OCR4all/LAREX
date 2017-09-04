package larex.segmentation;

import java.util.ArrayList;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import larex.positions.Position;
import larex.regions.Region;
import larex.regions.type.RegionType;
import larex.segmentation.result.ResultRegion;

public class RegionClassifier {

	private Mat binary;
	private ArrayList<Region> regions;
	private ArrayList<ResultRegion> results;
	private ArrayList<Candidate> candidates;

	public RegionClassifier(Mat binary, ArrayList<Region> regions) {
		setBinary(binary);
		preprocessRegions(regions);
		setResults(new ArrayList<ResultRegion>());
	}

	public boolean isWithinRegion(Rect toCheck, Region region) {
		for (Position position : region.getPositions()) {
			if (position.getOpenCVRect().contains(toCheck.tl()) && position.getOpenCVRect().contains(toCheck.br())) {
				return true;
			}
		}

		return false;
	}

	public ArrayList<Candidate> checkMaxOccUnbounded(ArrayList<Candidate> candidates, Region region) {
		ArrayList<Candidate> remainingCandidates = new ArrayList<Candidate>();
		remainingCandidates.addAll(candidates);

		for (Candidate candidate : candidates) {
			Rect rect = candidate.getBoundingRect();

			if (rect.area() > region.getMinSize() && isWithinRegion(rect, region)) {
				ResultRegion newResult = new ResultRegion(region.getType(), candidate.getContour());
				results.add(newResult);
				remainingCandidates.remove(candidate);
			}
		}

		return remainingCandidates;
	}

	public ArrayList<Candidate> checkMaxOccOne(ArrayList<Candidate> candidates, Region region) {
		Candidate candidate = MaxOccOneFinder.findMaxOccOne(candidates, region);

		if (candidate != null) {
			ResultRegion newResult = new ResultRegion(region.getType(), candidate.getContour());
			results.add(newResult);

			candidates.remove(candidate);
		}

		return candidates;
	}

	public ArrayList<ResultRegion> classifyRegions(ArrayList<MatOfPoint> contours) {
		setCandidates(calcCandidates(contours));

		for (int i = 0; i < regions.size(); i++) {
			if (regions.get(i).getMaxOccurances() == -1) {
				ArrayList<Candidate> remainingCandidates = checkMaxOccUnbounded(candidates, regions.get(i));
				setCandidates(remainingCandidates);
			} else {
				setCandidates(checkMaxOccOne(candidates, regions.get(i)));
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

	public ArrayList<Candidate> calcCandidates(ArrayList<MatOfPoint> contours) {
		ArrayList<Candidate> candidates = new ArrayList<Candidate>();
		int minSize = determineMinimumSize();

		for (MatOfPoint contour : contours) {
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
	public void preprocessRegions(ArrayList<Region> regions) {
		ArrayList<Region> processedRegions = new ArrayList<Region>();
		ArrayList<Region> maxOccOne = new ArrayList<Region>();
		Region paragraphRegion = null;

		for (Region region : regions) {
			if (!region.getType().equals(RegionType.ignore) && !region.getType().equals(RegionType.image)) {
				if (region.getType().equals(RegionType.paragraph)) {
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

	public Mat getBinary() {
		return binary;
	}

	public void setBinary(Mat binary) {
		this.binary = binary;
	}

	public ArrayList<Region> getRegions() {
		return regions;
	}

	public void setRegions(ArrayList<Region> regions) {
		this.regions = regions;
	}

	public ArrayList<ResultRegion> getResults() {
		return results;
	}

	public void setResults(ArrayList<ResultRegion> results) {
		this.results = results;
	}

	public ArrayList<Candidate> getCandidates() {
		return candidates;
	}

	public void setCandidates(ArrayList<Candidate> candidates) {
		this.candidates = candidates;
	}
}