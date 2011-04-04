/*
 * Created on Dec 11, 2007
 */
package com.codecommit.wicket;

/**
 * @author Daniel Spiewak
 */
public enum RangeType {
	HORIZONTAL("r"),
	VERTICAL("R");
	
	private final String rendering;
	
	private RangeType(String rendering) {
		this.rendering = rendering;
	}
	
	public String getRendering() {
		return rendering;
	}
}
