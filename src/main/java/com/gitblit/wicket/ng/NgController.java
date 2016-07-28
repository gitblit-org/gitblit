/*
 Copyright 2013 gitblit.com.

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
package com.gitblit.wicket.ng;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.IHeaderContributor;
import org.apache.wicket.request.resource.PackageResourceReference;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Simple AngularJS data controller which injects scoped objects as static,
 * embedded JSON within the generated page.  This allows use of AngularJS
 * client-side databinding (magic) with server-generated pages.
 *
 * @author James Moger
 *
 */
public class NgController implements IHeaderContributor {

	private static final long serialVersionUID = 1L;

	final String name;

	final Map<String, Object> variables;

	public NgController(String name) {
		this.name = name;
		this.variables = new HashMap<String, Object>();
	}

	public void addVariable(String name, Object o) {
		variables.put(name,  o);
	}

	@Override
	public void renderHead(IHeaderResponse response) {
		// add Google AngularJS reference
		response.render(JavaScriptHeaderItem.forReference(new PackageResourceReference(NgController.class, "angular.js")));

		Gson gson = new GsonBuilder().create();

		StringBuilder sb = new StringBuilder();
		line(sb, MessageFormat.format("<!-- AngularJS {0} data controller -->", name));
		line(sb, MessageFormat.format("function {0}($scope) '{'", name));
		for (Map.Entry<String, Object> entry : variables.entrySet()) {
			String var = entry.getKey();
			Object o = entry.getValue();
			String json = gson.toJson(o);
			line(sb, MessageFormat.format("\t$scope.{0} = {1};", var, json));
		}
		line(sb, "}");

		response.render(JavaScriptHeaderItem.forScript(sb.toString(), "angularController"));
	}

	private void line(StringBuilder sb, String line) {
		sb.append(line);
		sb.append('\n');
	}
}