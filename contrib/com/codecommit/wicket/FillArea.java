/*
 * Created on Dec 11, 2007
 */
package com.codecommit.wicket;

import java.awt.Color;

/**
 * @author Daniel Spiewak
 */
public class FillArea implements IFillArea {
	private Color color;
	private int endIndex = -1;
	private int startIndex = -1;
	private AreaFillType type = AreaFillType.BETWEEN;
	
	public FillArea(Color color, int startIndex, int endIndex) {
		this.color = color;
		this.startIndex = startIndex;
		this.endIndex = endIndex;
	}

	public Color getColor() {
		return color;
	}

	public int getEndIndex() {
		return endIndex;
	}

	public int getStartIndex() {
		return startIndex;
	}

	public AreaFillType getType() {
		return type;
	}

	public void setColor(Color color) {
		this.color = color;
	}

	public void setEndIndex(int endIndex) {
		this.endIndex = endIndex;
	}

	public void setStartIndex(int startIndex) {
		this.startIndex = startIndex;
	}

	public void setType(AreaFillType type) {
		this.type = type;
	}
}
