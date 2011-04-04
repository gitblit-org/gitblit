/*
 * Created on Dec 11, 2007
 */
package com.codecommit.wicket;

import java.awt.Color;
import java.awt.Dimension;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Daniel Spiewak
 */
public class ChartProvider implements IChartProvider, Serializable {
	private static final long serialVersionUID = 1L;
	private List<IChartAxis> axes = new ArrayList<IChartAxis>();
	private IChartFill backgroundFill;
	private int barGroupSpacing = -1;
	private int barWidth = -1;
	private IChartFill chartFill;
	private Color[] colors;
	private List<IFillArea> fillAreas = new ArrayList<IFillArea>();
	private IChartGrid grid;
	private String[] legend;
	private ILineStyle[] lineStyles;
	private String[] pieLabels;
	private List<IRangeMarker> rangeMarkers = new ArrayList<IRangeMarker>();
	private List<IShapeMarker> shapeMarkers = new ArrayList<IShapeMarker>();
	private Dimension size;
	private String title;
	private ChartType type;
	private IChartData data;
	
	public ChartProvider(Dimension size, ChartType type, IChartData data) {
		this.size = size;
		this.type = type;
		this.data = data;
	}
	
	public IChartAxis[] getAxes() {
		return axes.toArray(new IChartAxis[axes.size()]);
	}

	public IChartFill getBackgroundFill() {
		return backgroundFill;
	}

	public int getBarGroupSpacing() {
		return barGroupSpacing;
	}

	public int getBarWidth() {
		return barWidth;
	}

	public IChartFill getChartFill() {
		return chartFill;
	}

	public Color[] getColors() {
		return colors;
	}

	public IFillArea[] getFillAreas() {
		return fillAreas.toArray(new IFillArea[fillAreas.size()]);
	}

	public IChartGrid getGrid() {
		return grid;
	}

	public String[] getLegend() {
		return legend;
	}

	public ILineStyle[] getLineStyles() {
		return lineStyles;
	}

	public String[] getPieLabels() {
		return pieLabels;
	}

	public IRangeMarker[] getRangeMarkers() {
		return rangeMarkers.toArray(new IRangeMarker[rangeMarkers.size()]);
	}

	public IShapeMarker[] getShapeMarkers() {
		return shapeMarkers.toArray(new IShapeMarker[shapeMarkers.size()]);
	}

	public Dimension getSize() {
		return size;
	}

	public String getTitle() {
		return title;
	}

	public ChartType getType() {
		return type;
	}
	
	public void addFillArea(IFillArea fillArea) {
		fillAreas.add(fillArea);
	}

	public void addAxis(IChartAxis axis) {
		axes.add(axis);
	}

	public void setBackgroundFill(IChartFill backgroundFill) {
		this.backgroundFill = backgroundFill;
	}

	public void setBarGroupSpacing(int barGroupSpacing) {
		this.barGroupSpacing = barGroupSpacing;
	}

	public void setBarWidth(int barWidth) {
		this.barWidth = barWidth;
	}

	public void setChartFill(IChartFill chartFill) {
		this.chartFill = chartFill;
	}

	public void setColors(Color[] colors) {
		this.colors = colors;
	}

	public void setGrid(IChartGrid grid) {
		this.grid = grid;
	}

	public void setLegend(String[] legend) {
		this.legend = legend;
	}

	public void setLineStyles(ILineStyle[] lineStyles) {
		this.lineStyles = lineStyles;
	}

	public void setPieLabels(String[] pieLabels) {
		this.pieLabels = pieLabels;
	}

	public void addRangeMarker(IRangeMarker rangeMarker) {
		rangeMarkers.add(rangeMarker);
	}

	public void addShapeMarker(IShapeMarker shapeMarker) {
		shapeMarkers.add(shapeMarker);
	}

	public void setSize(Dimension size) {
		this.size = size;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public void setType(ChartType type) {
		this.type = type;
	}

	public IChartData getData() {
		return data;
	}

	public void setData(IChartData data) {
		this.data = data;
	}
}
