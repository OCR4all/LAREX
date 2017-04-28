package larex.geometry;

import java.util.ArrayList;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;

public class Polygon {
	private java.awt.Polygon polyAwt;

	private int x_coords[];
	private int y_coords[];
	private int n;

	public Polygon(MatOfPoint input) {
		Point[] points = input.toArray();
		int[] x_coords = new int[points.length];
		int[] y_coords = new int[points.length];

		for (int i = 0; i < points.length; i++) {
			x_coords[i] = (int) points[i].x;
			y_coords[i] = (int) points[i].y;
		}

		setX_coords(x_coords);
		setY_coords(y_coords);
		setN(points.length);

		setPolyAwt(new java.awt.Polygon(x_coords, y_coords, n));
	}

	public Polygon(ArrayList<java.awt.Point> pointList) {
		java.awt.Point[] points = pointList.toArray(new java.awt.Point[pointList.size()]);
		int[] x_coords = new int[points.length];
		int[] y_coords = new int[points.length];

		for (int i = 0; i < points.length; i++) {
			x_coords[i] = (int) points[i].x;
			y_coords[i] = (int) points[i].y;
		}

		setX_coords(x_coords);
		setY_coords(y_coords);
		setN(points.length);

		setPolyAwt(new java.awt.Polygon(x_coords, y_coords, n));
	}

	public int[] getX_coords() {
		return x_coords;
	}

	public void setX_coords(int[] x_coords) {
		this.x_coords = x_coords;
	}

	public int[] getY_coords() {
		return y_coords;
	}

	public void setY_coords(int[] y_coords) {
		this.y_coords = y_coords;
	}

	public int getN() {
		return n;
	}

	public void setN(int n) {
		this.n = n;
	}

	public java.awt.Polygon getPolyAwt() {
		return polyAwt;
	}

	public void setPolyAwt(java.awt.Polygon polyAwt) {
		this.polyAwt = polyAwt;
	}
}