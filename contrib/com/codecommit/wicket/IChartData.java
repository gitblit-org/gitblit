/*
 * Created on Dec 11, 2007
 */
package com.codecommit.wicket;

/**
 * @author Daniel Spiewak
 */
public interface IChartData {
	
	public ChartDataEncoding getEncoding();
	
	public double[][] getData();
	
	public double getMax();
}
