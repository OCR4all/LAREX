package de.uniwue.web.model;

import java.util.ArrayList;
import java.util.LinkedList;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.uniwue.algorithm.geometry.PointList;

/**
 * A representation of a Polygon that is parsed to the gui. Contains positional
 * points. Polygon can be created relative or absolute. - A absolut Polygon is
 * positioned via pixel (e.g. 10:10 -> 10px:10px) - A relative Polygon is
 * positioned via percentage (e.g. 0.1:0.1 -> 10%:10%)
 *
 */
public class Polygon {

	@JsonProperty("points")
	protected ArrayList<Point> points;
	@JsonProperty("isRelative")
	protected boolean isRelative;

	@JsonCreator
	public Polygon(
			@JsonProperty("points") ArrayList<Point> points,
			@JsonProperty("isRelative") boolean isRelative) {
		this.points = points;
		this.isRelative = isRelative;
	}

	public Polygon(ArrayList<Point> points) {
		this.points = points;
		this.isRelative = false;
	}

	public Polygon(org.primaresearch.maths.geometry.Polygon points){
		this.points = new ArrayList<>();
		for (int i = 0; i < points.getSize(); i++) {
			org.primaresearch.maths.geometry.Point point = points.getPoint(i);
			this.addPoint(new Point(point.x, point.y));
		}
		this.isRelative = false;
	}

	public Polygon(boolean isRelative){
		this.points = new ArrayList<>();
		this.isRelative = isRelative;
	}

	public Polygon(){
		this.points = new ArrayList<>();
		this.isRelative = false;
	}

	public void addPoint(Point point){
		points.add(point);
	}

	public LinkedList<Point> getPoints() {
		return new LinkedList<Point>(points);
	}

	public boolean isRelative() {
		return isRelative;
	}

	public PointList toPointList(){
		ArrayList<java.awt.Point> points = new ArrayList<>();
		for (Point point : this.getPoints()) {
			points.add(point.toAwtPoint());
		}
		return new PointList(points);
	}

	public org.primaresearch.maths.geometry.Polygon toPrimaPolygon(){
		org.primaresearch.maths.geometry.Polygon polygon = new org.primaresearch.maths.geometry.Polygon();

		for(Point point : points){
			polygon.addPoint((int) point.getX(), (int) point.getY());
		}

		return polygon;
	}
}
