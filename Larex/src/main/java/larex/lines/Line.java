package larex.lines;

import org.opencv.core.Mat;
import org.opencv.core.Point;

public class Line {

	private Point start;
	private Point end;
	
	private LineType type;
	
	public Line(Point lineStartPoint, Point lineEndPoint, Mat image) {
		setStart(lineStartPoint);
		setEnd(lineEndPoint);
		checkLine(image);
	}
	
	//TODO
	public Line(java.awt.Point start, java.awt.Point end, Mat image) {
		this(new Point(start.x, start.y), new Point(end.x, end.y), image);
	}

	public void checkLine(Mat image) {
		double deltaX = Math.abs(start.x - end.x);
		double deltaY = Math.abs(start.y - end.y);
		
		if(deltaX > deltaY) {
			if(end.y > image.height() / 2) {
				setType(LineType.HORIZONTAL_BOTTOM);
			} else {
				setType(LineType.HORIZONTAL_TOP);
			}
		} else {
			if(end.x > image.width() / 2) {
				setType(LineType.VERTICAL_RIGHT);
			} else {
				setType(LineType.VERTICAL_LEFT);
			}
		}
	}
	
	public Point getStart() {
		return start;
	}

	public void setStart(Point start) {
		this.start = start;
	}

	public Point getEnd() {
		return end;
	}

	public void setEnd(Point end) {
		this.end = end;
	}

	public LineType getType() {
		return type;
	}

	public void setType(LineType type) {
		this.type = type;
	}	
}