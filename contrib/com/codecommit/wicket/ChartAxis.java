/*
 * Created on Dec 11, 2007
 */
package com.codecommit.wicket;

import java.awt.Color;

/**
 * @author Daniel Spiewak
 */
public class ChartAxis implements IChartAxis {
	private AxisAlignment alignment;
	private Color color;
	private int fontSize = -1;
	private String[] labels;
	private double[] positions;
	private Range range;
	private ChartAxisType type;
	
	public ChartAxis(ChartAxisType type) {
		this.type = type;
	}

	public AxisAlignment getAlignment() {
		return alignment;
	}

	public Color getColor() {
		return color;
	}

	public int getFontSize() {
		return fontSize;
	}

	public String[] getLabels() {
		return labels;
	}

	public double[] getPositions() {
		return positions;
	}

	public Range getRange() {
		return range;
	}

	public ChartAxisType getType() {
		return type;
	}

	public void setAlignment(AxisAlignment alignment) {
		this.alignment = alignment;
	}

	public void setColor(Color color) {
		this.color = color;
	}

	public void setFontSize(int fontSize) {
		this.fontSize = fontSize;
	}

	public void setLabels(String[] labels) {
		this.labels = labels;
	}

	public void setPositions(double[] positions) {
		this.positions = positions;
	}

	public void setRange(Range range) {
		this.range = range;
	}

	public void setType(ChartAxisType type) {
		this.type = type;
	}
}
