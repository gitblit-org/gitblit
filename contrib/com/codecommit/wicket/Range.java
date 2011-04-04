/*
 * Created on Dec 11, 2007
 */
package com.codecommit.wicket;

/**
 * @author Daniel Spiewak
 */
public class Range {
	private double start, end;
	
	public Range(double start, double end) {
		this.start = start;
		this.end = end;
	}

	public double getStart() {
		return start;
	}

	public void setStart(double start) {
		this.start = start;
	}

	public double getEnd() {
		return end;
	}

	public void setEnd(double end) {
		this.end = end;
	}
}
