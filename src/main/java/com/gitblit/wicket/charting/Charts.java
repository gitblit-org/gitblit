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

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.behavior.Behavior;
import org.apache.wicket.markup.html.IHeaderContributor;

/**
 * Abstract parent class for Flotr2 Charts
 *
 * @author Tim Ryan
 *
 */
public abstract class Charts extends Behavior {

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
