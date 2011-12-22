/*
 * Copyright 2011 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.wicket.charting;

import java.text.MessageFormat;

/**
 * Builds an interactive line chart using the Visualization API.
 * 
 * @author James Moger
 * 
 */
public class GoogleLineChart extends GoogleChart {

	private static final long serialVersionUID = 1L;

	public GoogleLineChart(String tagId, String title, String keyName, String valueName) {
		super(tagId, title, keyName, valueName);
	}

	@Override
	protected void appendChart(StringBuilder sb) {
		String dName = "data_" + dataName;
		line(sb, MessageFormat.format("var {0} = new google.visualization.DataTable();", dName));
		line(sb, MessageFormat.format("{0}.addColumn(''string'', ''{1}'');", dName, keyName));
		line(sb, MessageFormat.format("{0}.addColumn(''number'', ''{1}'');", dName, valueName));
		line(sb, MessageFormat.format("{0}.addRows({1,number,0});", dName, values.size()));

		for (int i = 0; i < values.size(); i++) {
			ChartValue value = values.get(i);
			line(sb, MessageFormat.format("{0}.setValue({1,number,0}, 0, ''{2}'');", dName, i,
					value.name));
			line(sb, MessageFormat.format("{0}.setValue({1,number,0}, 1, {2,number,0.0});", dName,
					i, value.value));
		}

		String cName = "chart_" + dataName;
		line(sb, MessageFormat.format(
				"var {0} = new google.visualization.LineChart(document.getElementById(''{1}''));",
				cName, tagId));
		line(sb,
				MessageFormat
						.format("{0}.draw({1}, '{'width: {2,number,0}, height: {3,number,0}, pointSize: 4, chartArea:'{'left:20,top:20'}', vAxis: '{' textPosition: ''none'' '}', legend: ''none'', title: ''{4}'' '}');",
								cName, dName, width, height, title));
		line(sb, "");
	}
}