/*
 * Created on Dec 11, 2007
 */
package com.codecommit.wicket;

import java.io.Serializable;

/**
 * @author Daniel Spiewak
 */
public enum MarkerType implements Serializable {
	ARROW("a"),
	CROSS("c"),
	DIAMOND("d"),
	CIRCLE("o"),
	SQUARE("s"),
	
	VERTICAL_TO_DATA("v"),
	VERTICAL_TO_TOP("V"),
	HORIZONTAL_ACROSS("h"),
	
	X("x");
	
	private final String rendering;
	
	private MarkerType(String rendering) {
		this.rendering = rendering;
	}
	
	public String getRendering() {
		return rendering;
	}
}
