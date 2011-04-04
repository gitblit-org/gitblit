/*
 * Created on Dec 11, 2007
 */
package com.codecommit.wicket;

import java.awt.Color;

/**
 * @author Daniel Spiewak
 */
public class LinearGradientFill implements ILinearGradientFill {
	private int angle = -1;
	private Color[] colors;
	private double[] offsets;
	
	public LinearGradientFill(int angle, Color[] colors, double[] offsets) {
		if (colors.length != offsets.length) {
			throw new IllegalArgumentException("Must be same number of colors as offsets");
		}
		
		this.angle = angle;
		this.colors = colors;
		this.offsets = offsets;
	}

	public int getAngle() {
		return angle;
	}

	public Color[] getColors() {
		return colors;
	}

	public double[] getOffsets() {
		return offsets;
	}

	public void setAngle(int angle) {
		this.angle = angle;
	}

	public void setColors(Color[] colors) {
		this.colors = colors;
	}

	public void setOffsets(double[] offsets) {
		this.offsets = offsets;
	}
}
