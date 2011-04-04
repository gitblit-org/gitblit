/*
 * Created on Dec 11, 2007
 */
package com.codecommit.wicket;

import java.awt.Color;

/**
 * @author Daniel Spiewak
 */
public class ShapeMarker implements IShapeMarker {
	private Color color;
	private int index = -1;
	private double point = -1;
	private int size = -1;
	private MarkerType type;
	
	public ShapeMarker(MarkerType type, Color color, int index, double point, int size) {
		this.type = type;
		this.color = color;
		this.index = index;
		this.point = point;
		this.size = size;
	}
	
	public Color getColor() {
		return color;
	}

	public int getIndex() {
		return index;
	}

	public double getPoint() {
		return point;
	}

	public int getSize() {
		return size;
	}

	public MarkerType getType() {
		return type;
	}

	public void setColor(Color color) {
		this.color = color;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public void setPoint(double point) {
		this.point = point;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public void setType(MarkerType type) {
		this.type = type;
	}
}
