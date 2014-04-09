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
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

/**
 * Implementation of a Line chart using the flotr2 library
 *
 * @author Tim Ryan
 *
 */
public class Flotr2LineChart extends Chart {

	private static final long serialVersionUID = 1L;
	boolean xAxisIsDate = false;

	public Flotr2LineChart(String tagId, String title, String keyName,
			String valueName) {
		super(tagId, title, keyName, valueName);

	}

	@Override
	protected void appendChart(StringBuilder sb) {
		
		String dName = "data_" + dataName;
		sb.append("var labels_" + dataName + " = [");
		if(xAxisIsDate){
			// Generate labels for the dates
			SimpleDateFormat df = new SimpleDateFormat(dateFormat);
			df.setTimeZone(getTimeZone());
			
			// Sort the values first
			Collections.sort(values, new Comparator<ChartValue>() {

		        public int compare(ChartValue o1, ChartValue o2) {
		        	long long1 = Long.parseLong(o1.name);
		        	long long2 = Long.parseLong(o2.name);
		            return (int) (long2 - long1);
		        }
		        
		    });			
			
			for (int i = 0; i < values.size(); i++) {
				ChartValue value = values.get(i);
				Date date = new Date(Long.parseLong(value.name));
				String label = df.format(date);
				if(i > 0){
					sb.append(",");
				}
				sb.append("\"" + label + "\"");
			}
			
		}
		else {
			for (int i = 0; i < values.size(); i++) {
				ChartValue value = values.get(i);
				if(i > 0){
					sb.append(",");
				}
				sb.append("\"" + value.name + "\"");
			}
		}
		line(sb, "];");
		
		line(sb, MessageFormat.format("var {0} = Flotr.draw(document.getElementById(''{1}''),", dName, tagId));
		
		// Add the data
		line(sb, "[");
		line(sb, "{ data : [ ");
		for (int i = 0; i < values.size(); i++) {
			ChartValue value = values.get(i);
			if(i > 0){
				sb.append(",");
			}
			line(sb, MessageFormat.format("[{0}, {1}] ",  value.name, Float.toString(value.value)));
		}
		line(sb, MessageFormat.format(" ], label : ''{0}'', color: ''#ff9900'' '}'", valueName));
		line(sb, "]");
		
		// Add the options
		line(sb, ", {");
		if(title != null && title.isEmpty() == false){
			line(sb, MessageFormat.format("title : ''{0}'',", title));
		}
		line(sb, "lines : {");
		line(sb, "  show : true");
		line(sb, "},");
		line(sb, "points: {");
		line(sb, "  show: true,");	
		line(sb, "  fill: true,");	
		line(sb, "  fillColor:'#002060'");
		line(sb, "},");	
		line(sb, "mouse: {");
		line(sb, "  track: true,");
		line(sb, "  lineColor: '#002060',");
		line(sb, "  position: 'ne',");
		line(sb, "  trackFormatter: function (obj) {");
		line(sb, "    return labels_" + dataName + "[obj.index] + ': ' + parseInt(obj.y) + ' ' +  obj.series.label;");
		line(sb, "  }");
		line(sb, "},");
		line(sb, "xaxis: {");
		line(sb, "  showLabels: false,");
		line(sb, "  showMinorLabels: false,");
		line(sb, "  autoscale: true,");
		line(sb, "  autoscaleMargin: 0,");
		line(sb, "  margin: 10");	
		line(sb, "},");
		line(sb, "yaxis: {");
		line(sb, "  showLabels: false,");
		line(sb, "  showMinorLabels: false,");
		line(sb, "  tickDecimals: 0,");
		line(sb, "  autoscale: true,");
		line(sb, "  autoscaleMargin: 1,");
		line(sb, "  margin: 10");
		line(sb, "},");
		line(sb, "grid: {");
		line(sb, "  verticalLines: false,");
		line(sb, "  minorVerticalLines: null,");
		line(sb, "  horizontalLines: true,");
		line(sb, "  minorHorizontalLines: null,");
		line(sb, "  outlineWidth: 1,");
		line(sb, "  outline: 's'");
		line(sb, "},");
		line(sb, "legend: {");
		line(sb, "  show: false");
		line(sb, "}");
		line(sb, "});");
		
	}

	@Override
	public void addValue(Date date, int value) {
		xAxisIsDate = true;
		String name = String.valueOf(date.getTime());
		super.addValue(name, value);
	}
	
	

}
