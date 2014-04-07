package com.gitblit.wicket.charting;

import java.text.MessageFormat;

import com.gitblit.utils.StringUtils;

public class Flotr2PieChart extends Chart {

	private static final long serialVersionUID = 1L;

	public Flotr2PieChart(String tagId, String title, String keyName, String valueName) {
		super(tagId, title, keyName, valueName);
	}

	@Override
	protected void appendChart(StringBuilder sb) {
			
		String dName = "data_" + dataName;
		line(sb, MessageFormat.format("var {0} = Flotr.draw(document.getElementById(''{1}''),", dName, tagId));
		
		// Add the data
		line(sb, "[");
		for (int i = 0; i < values.size(); i++) {
			ChartValue value = values.get(i);
			if(i > 0){
				sb.append(",");
			}
			line(sb, MessageFormat.format("'{'data : [ [0, {0}] ], label : ''{1}'', color: ''{2}'' '}'", value.value, value.name, StringUtils.getColor(value.name)));
		}
		line(sb, "]");
		
		// Add the options
		line(sb, ", {");
		line(sb, MessageFormat.format("title : ''{0}'',", title));
		line(sb, "pie : {");
		line(sb, "  show : true,");
		line(sb, "  labelFormatter : function (pie, slice) {");
		line(sb, "    if(slice / pie > .05)");
		line(sb, "      return Math.round(slice / pie * 100);");
		line(sb, "  }");
		line(sb, "}");
		line(sb, ", mouse: {");
		line(sb, "  track: true,");
		line(sb, "  lineColor: '#25A7DF',");
		line(sb, "  trackFormatter: function (obj)");
		line(sb, "  {");
		line(sb, "    return obj.series.label + \": \" + parseInt(obj.y) + \" (\" + Math.round(obj.fraction * 100) + \"%)\";" );
		line(sb, "  }");
		line(sb, "}");
		line(sb, ", xaxis: {");
		line(sb, "  margin: false,");
		line(sb, "  showLabels: false,");
		line(sb, "  showMinorLabels: false");
		line(sb, "}");
		line(sb, ", yaxis: {");
		line(sb, "  margin: false,");
		line(sb, "  min: 20,");
		line(sb, "  showLabels: false,");
		line(sb, "  showMinorLabels: false");
		line(sb, "}");
		line(sb, ", grid: {");
		line(sb, "  verticalLines: false,");
		line(sb, "  minorVerticalLines: null,");
		line(sb, "  horizontalLines: false,");
		line(sb, "  minorHorizontalLines: null,");
		line(sb, "  outlineWidth: 0");
		line(sb, "}");
		line(sb, ", legend: {");
		if(showLegend){		
			line(sb, "  show: true");
		}
		else {
			line(sb, "  show: false");
		}
		line(sb, "}");
		line(sb, "});");
		
	}

}
