package com.gitblit.wicket.charting;

import javax.servlet.ServletContext;

import org.apache.wicket.markup.html.IHeaderResponse;
import org.apache.wicket.protocol.http.WebApplication;

public class Flotr2Charts extends Charts {

	private static final long serialVersionUID = 1L;

	@Override
	public void renderHead(IHeaderResponse response) {
		
		// add Google Chart JS API reference
		ServletContext servletContext = WebApplication.get().getServletContext();
		String contextPath = servletContext.getContextPath();
		
		response.renderJavascriptReference(contextPath + "/bootstrap/js/jquery.js");
		response.renderJavascriptReference(contextPath + "/flotr2.min.js");
		response.renderCSSReference(contextPath + "/flotr2.custom.css");
		
		// prepare draw chart function
		StringBuilder sb = new StringBuilder();
		
		line(sb, "$( document ).ready(function() {");
		// add charts to header
		for (Chart chart : charts) {
			chart.appendChart(sb);
		}

		// end draw chart function
		line(sb, "});");
		response.renderJavascript(sb.toString(), null);
	}

	@Override
	public Chart createPieChart(String tagId, String title, String keyName,
			String valueName) {
		return new Flotr2PieChart(tagId, title, keyName, valueName);
	}

	@Override
	public Chart createLineChart(String tagId, String title, String keyName,
			String valueName) {
		return new Flotr2LineChart(tagId, title, keyName, valueName);
	}

	@Override
	public Chart createBarChart(String tagId, String title, String keyName,
			String valueName) {
		return new Flotr2BarChart(tagId, title, keyName, valueName);
	}
	
}
