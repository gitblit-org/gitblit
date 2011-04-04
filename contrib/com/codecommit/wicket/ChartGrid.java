/*
 * Created on Dec 11, 2007
 */
package com.codecommit.wicket;

/**
 * @author Daniel Spiewak
 */
public class ChartGrid implements IChartGrid {
	private int blankLength = -1;
	private int segmentLength = -1;
	private int xStepSize = -1;
	private int yStepSize = -1;
	
	public ChartGrid(int xStepSize, int yStepSize) {
		this.xStepSize = xStepSize;
		this.yStepSize = yStepSize;
	}

	public int getBlankLength() {
		return blankLength;
	}

	public int getSegmentLength() {
		return segmentLength;
	}

	public double getXStepSize() {
		return xStepSize;
	}

	public double getYStepSize() {
		return yStepSize;
	}

	public void setBlankLength(int blankLength) {
		this.blankLength = blankLength;
	}

	public void setSegmentLength(int segmentLength) {
		this.segmentLength = segmentLength;
	}

	public void setXStepSize(int stepSize) {
		xStepSize = stepSize;
	}

	public void setYStepSize(int stepSize) {
		yStepSize = stepSize;
	}
}
