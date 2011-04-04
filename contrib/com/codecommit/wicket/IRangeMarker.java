/*
 * Created on Dec 11, 2007
 */
package com.codecommit.wicket;

import java.awt.Color;

/**
 * @author Daniel Spiewak
 */
public interface IRangeMarker {

	public RangeType getType();
	
	public Color getColor();
	
	public double getStart();
	
	public double getEnd();
}
