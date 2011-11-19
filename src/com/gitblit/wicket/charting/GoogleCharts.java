/*
 Copyright 2011 gitblit.com.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package com.gitblit.wicket.charting;

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.markup.html.IHeaderContributor;
import org.apache.wicket.markup.html.IHeaderResponse;

/**
 * The Google Visualization API provides interactive JavaScript based charts and
 * graphs. This class implements the JavaScript header necessary to display
 * complete graphs and charts.
 * 
 * @author James Moger
 * 
 */
public class GoogleCharts implements IHeaderContributor {

	private static final long serialVersionUID = 1L;

	public final List<GoogleChart> charts = new ArrayList<GoogleChart>();

	public void addChart(GoogleChart chart) {
		charts.add(chart);
	}

	@Override
	public void renderHead(IHeaderResponse response) {
		// add Google Chart JS API reference
		response.renderJavascriptReference("https://www.google.com/jsapi");

		// prepare draw chart function
		StringBuilder sb = new StringBuilder();
		line(sb, "google.load(\"visualization\", \"1\", {packages:[\"corechart\"]});");
		line(sb, "google.setOnLoadCallback(drawChart);");
		line(sb, "function drawChart() {");

		// add charts to header
		for (GoogleChart chart : charts) {
			chart.appendChart(sb);
		}

		// end draw chart function
		line(sb, "}");
		response.renderJavascript(sb.toString(), null);
	}

	private void line(StringBuilder sb, String line) {
		sb.append(line);
		sb.append('\n');
	}
}