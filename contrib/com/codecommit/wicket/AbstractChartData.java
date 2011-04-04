/*
 * Created on Dec 11, 2007
 */
package com.codecommit.wicket;

import java.io.Serializable;

/**
 * @author Daniel Spiewak
 */
public abstract class AbstractChartData implements IChartData, Serializable {
	
	private static final long serialVersionUID = 1L;

	private ChartDataEncoding encoding;
	private double max;
	
	public AbstractChartData() {
		this(62);
	}
	
	public AbstractChartData(double max) {
		this(ChartDataEncoding.SIMPLE, max);
	}
	
	public AbstractChartData(ChartDataEncoding encoding, double max) {
		this.encoding = encoding;
		this.max = max;
	}

	public ChartDataEncoding getEncoding() {
		return encoding;
	}
	
	public double getMax() {
		return max;
	}
	
	public void setEncoding(ChartDataEncoding encoding) {
		this.encoding = encoding;
	}
	
	public void setMax(double max) {
		this.max = max;
	}
}
