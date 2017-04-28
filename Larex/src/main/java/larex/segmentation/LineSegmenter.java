package larex.segmentation;

import java.util.ArrayList;

import larex.lines.Line;
import larex.lines.LineType;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;

import larex.segmentation.parameters.Parameters;

public class LineSegmenter {

	private static final int LINE_THICKNESS = 2;
	
	private ArrayList<Line> processedLines;
	
//	private Mat binaryMarginalia;
//	private Mat binaryPageNumber;
	
	public LineSegmenter(ArrayList<Line> lines, Mat binary, Parameters parameters) {
		initLines(lines, parameters.getScaleFactor(), binary);
	}
	
	public void initLines(ArrayList<Line> lines, double scaleFactor, Mat image) {
		ArrayList<Line> processedLines = new ArrayList<Line>();
		
		for(Line line : lines) {
			Point start = new Point(line.getStart().x * scaleFactor, line.getStart().y * scaleFactor);
			Point end = new Point(line.getEnd().x * scaleFactor, line.getEnd().y * scaleFactor);
			
			Line newLine = new Line(start, end, image);
			processedLines.add(newLine);
		}
		setProcessedLines(processedLines);
	}
	
	public void drawLines(Mat binary) {
		for(Line line : processedLines) {
			Core.line(binary, line.getStart(), line.getEnd(), new Scalar(0), LINE_THICKNESS);
			
			if(line.getType().equals(LineType.VERTICAL_LEFT)) {
				Core.line(binary, new Point(0, line.getStart().y), line.getStart(), new Scalar(0), LINE_THICKNESS);
				Core.line(binary, new Point(0, line.getEnd().y), line.getEnd(), new Scalar(0), LINE_THICKNESS);
			} else if(line.getType().equals(LineType.VERTICAL_RIGHT)) {
				Core.line(binary, new Point(binary.width() - 1, line.getStart().y), line.getStart(), new Scalar(0), LINE_THICKNESS);
				Core.line(binary, new Point(binary.width() - 1, line.getEnd().y), line.getEnd(), new Scalar(0), LINE_THICKNESS);
			}
		}
	}

	public ArrayList<Line> getProcessedLines() {
		return processedLines;
	}

	public void setProcessedLines(ArrayList<Line> processedLines) {
		this.processedLines = processedLines;
	}
}