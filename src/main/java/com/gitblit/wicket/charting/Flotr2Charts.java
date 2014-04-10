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

import javax.servlet.ServletContext;

import org.apache.wicket.markup.html.IHeaderResponse;
import org.apache.wicket.protocol.http.WebApplication;

/**
 * Concrete class for Flotr2 charts
 *
 * @author Tim Ryan
 *
 */
public class Flotr2Charts extends Charts {

	private static final long serialVersionUID = 1L;

	@Override
	public void renderHead(IHeaderResponse response) {
		
		// add Google Chart JS API reference
		ServletContext servletContext = WebApplication.get().getServletContext();
		String contextPath = servletContext.getContextPath();
		
		response.renderJavascriptReference(contextPath + "/bootstrap/js/jquery.js");
		response.renderJavascriptReference(contextPath + "/flotr2/flotr2.min.js");
		response.renderCSSReference(contextPath + "/flotr2/flotr2.custom.css");
		
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
