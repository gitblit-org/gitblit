/*
 * Created on Dec 11, 2007
 */
package com.codecommit.wicket;

/**
 * @author Daniel Spiewak
 */
public enum ChartType {
	LINE("lc"),
	LINE_XY("lxy"),
	
	BAR_HORIZONTAL_SET("bhs"),
	BAR_VERTICAL_SET("bvs"),
	BAR_HORIZONTAL_GROUP("bhg"),
	BAR_VERTICAL_GROUP("bvg"),
	
	PIE("p"),
	PIE_3D("p3"),
	
	VENN("v"),
	
	SCATTER("s");
	
	private final String rendering;
	
	private ChartType(String rendering) {
		this.rendering = rendering;
	}
	
	public String getRendering() {
		return rendering;
	}
}
