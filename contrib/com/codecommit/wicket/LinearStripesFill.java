/*
 * Created on Dec 11, 2007
 */
package com.codecommit.wicket;

import java.awt.Color;

/**
 * @author Daniel Spiewak
 */
public class LinearStripesFill implements ILinearStripesFill {
	private int angle = -1;
	private Color[] colors;
	private double[] widths;
	
	public LinearStripesFill(int angle, Color[] colors, double[] widths) {
		if (colors.length != widths.length) {
			throw new IllegalArgumentException("Must be same number of colors as widths");
		}
		
		this.angle = angle;
		this.colors = colors;
		this.widths = widths;
	}
	
	public int getAngle() {
		return angle;
	}

	public Color[] getColors() {
		return colors;
	}

	public double[] getWidths() {
		return widths;
	}

	public void setAngle(int angle) {
		this.angle = angle;
	}

	public void setColors(Color[] colors) {
		this.colors = colors;
	}

	public void setWidths(double[] widths) {
		this.widths = widths;
	}
}
