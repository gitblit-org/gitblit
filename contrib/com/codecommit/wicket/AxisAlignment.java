/*
 * Created on Dec 11, 2007
 */
package com.codecommit.wicket;

/**
 * @author Daniel Spiewak
 */
public enum AxisAlignment {
	LEFT(-1),
	CENTER(0),
	RIGHT(1);
	
	private final int rendering;
	
	private AxisAlignment(int rendering) {
		this.rendering = rendering;
	}
	
	public int getRendering() {
		return rendering;
	}
}
