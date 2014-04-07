package com.gitblit.wicket.charting;

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.markup.html.IHeaderContributor;

public abstract class Charts implements IHeaderContributor {

	private static final long serialVersionUID = 1L;
	
	public final List<Chart> charts = new ArrayList<Chart>();

	public void addChart(Chart chart) {
		charts.add(chart);
	}
	
	protected void line(StringBuilder sb, String line) {
		sb.append(line);
		sb.append('\n');
	}
	
	public abstract Chart createPieChart(String tagId, String title, String keyName, String valueName);
	public abstract Chart createLineChart(String tagId, String title, String keyName, String valueName);
	public abstract Chart createBarChart(String tagId, String title, String keyName, String valueName);

}
