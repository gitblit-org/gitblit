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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gitblit.utils.StringUtils;

/**
 * Builds an interactive pie chart using the Visualization API.
 *
 * @author James Moger
 *
 */
public class GooglePieChart extends GoogleChart {

	private static final long serialVersionUID = 1L;

	public GooglePieChart(String tagId, String title, String keyName, String valueName) {
		super(tagId, title, keyName, valueName);
	}

	@Override
	protected void appendChart(StringBuilder sb) {
		// create dataset
		String dName = "data_" + dataName;
		line(sb, MessageFormat.format("var {0} = new google.visualization.DataTable();", dName));
		line(sb, MessageFormat.format("{0}.addColumn(''string'', ''{1}'');", dName, keyName));
		line(sb, MessageFormat.format("{0}.addColumn(''number'', ''{1}'');", dName, valueName));
		line(sb, MessageFormat.format("{0}.addRows({1,number,0});", dName, values.size()));

		Collections.sort(values);
		List<ChartValue> list = new ArrayList<ChartValue>();

		int maxSlices = 10;

		if (values.size() > maxSlices) {
			list.addAll(values.subList(0,  maxSlices));
		} else {
			list.addAll(values);
		}

		StringBuilder colors = new StringBuilder("colors:[");
		for (int i = 0; i < list.size(); i++) {
			ChartValue value = list.get(i);
			colors.append('\'');
			colors.append(StringUtils.getColor(value.name));
			colors.append('\'');
			if (i < values.size() - 1) {
				colors.append(',');
			}
			line(sb, MessageFormat.format("{0}.setValue({1,number,0}, 0, ''{2}'');", dName, i,
					value.name));
			line(sb, MessageFormat.format("{0}.setValue({1,number,0}, 1, {2,number,0.0});", dName,
					i, value.value));
		}
		colors.append(']');

		// instantiate chart
		String cName = "chart_" + dataName;
		line(sb, MessageFormat.format(
				"var {0} = new google.visualization.PieChart(document.getElementById(''{1}''));",
				cName, tagId));
		line(sb,
				MessageFormat
						.format("{0}.draw({1}, '{' title: ''{4}'', {5}, legend: '{' position:''{6}'' '}' '}');",
								cName, dName, width, height, title, colors.toString(), showLegend ? "right" : "none"));
		line(sb, "");
	}
}