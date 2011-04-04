/*
 * Created on Dec 11, 2007
 */
package com.codecommit.wicket;

/**
 * @author Daniel Spiewak
 */
public interface IChartGrid {
	
	public double getXStepSize();
	
	public double getYStepSize();
	
	public int getSegmentLength();
	
	public int getBlankLength();
}
