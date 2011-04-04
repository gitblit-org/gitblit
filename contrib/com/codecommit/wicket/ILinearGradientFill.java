/*
 * Created on Dec 11, 2007
 */
package com.codecommit.wicket;

import java.awt.Color;

/**
 * @author Daniel Spiewak
 */
public interface ILinearGradientFill extends IChartFill {
	
	public int getAngle();
	
	public Color[] getColors();
	
	public double[] getOffsets();
}
