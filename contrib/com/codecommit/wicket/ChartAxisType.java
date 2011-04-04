/*
 * Created on Dec 11, 2007
 */
package com.codecommit.wicket;

/**
 * @author Daniel Spiewak
 */
public enum ChartAxisType {
	BOTTOM("x"),
	TOP("t"),
	
	LEFT("y"),
	RIGHT("r");
	
	private final String rendering;
	
	private ChartAxisType(String rendering) {
		this.rendering = rendering;
	}
	
	public String getRendering() {
		return rendering;
	}
}
