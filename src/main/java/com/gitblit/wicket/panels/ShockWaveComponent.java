/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.wicket.panels;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.MarkupStream;
import org.apache.wicket.request.Response;
import org.apache.wicket.util.value.IValueMap;

/**
 * https://cwiki.apache.org/WICKET/object-container-adding-flash-to-a-wicket-application.html
 *
 * @author Jan Kriesten
 * @author manuelbarzi
 * @author James Moger
 *
 */
public class ShockWaveComponent extends ObjectContainer {
	private static final long serialVersionUID = 1L;

	private static final String CONTENTTYPE = "application/x-shockwave-flash";
	private static final String CLSID = "clsid:D27CDB6E-AE6D-11cf-96B8-444553540000";
	private static final String CODEBASE = "http://fpdownload.adobe.com/pub/shockwave/cabs/flash/swflash.cab#version=7,0,0,0";

	// valid attributes
	private static final List<String> attributeNames = Arrays.asList(new String[] { "classid",
			"width", "height", "codebase", "align", "base", "data", "flashvars" });
	// valid parameters
	private static final List<String> parameterNames = Arrays.asList(new String[] { "devicefont",
			"movie", "play", "loop", "quality", "bgcolor", "scale", "salign", "menu", "wmode",
			"allowscriptaccess", "seamlesstabbing", "flashvars" });

	// combined options (to iterate over them)
	private static final List<String> optionNames = new ArrayList<String>(attributeNames.size()
			+ parameterNames.size());
	static {
		optionNames.addAll(attributeNames);
		optionNames.addAll(parameterNames);
	}

	private Map<String, String> attributes;
	private Map<String, String> parameters;

	public ShockWaveComponent(String id) {
		super(id);

		attributes = new HashMap<String, String>();
		parameters = new HashMap<String, String>();
	}

	public ShockWaveComponent(String id, String movie) {
		this(id);
		setValue("movie", movie);
	}

	public ShockWaveComponent(String id, String movie, String width, String height) {
		this(id);

		setValue("movie", movie);
		setValue("width", width);
		setValue("height", height);
	}

	@Override
	public void setValue(String name, String value) {
		// IE and other browsers handle movie/data differently. So movie is used
		// for IE, whereas
		// data is used for all other browsers. The class uses movie parameter
		// to handle url and
		// puts the values to the maps depending on the browser information
		String parameter = name.toLowerCase();
		if ("data".equals(parameter))
			parameter = "movie";

		if ("movie".equals(parameter) && !getClientProperties().isBrowserInternetExplorer())
			attributes.put("data", value);

		if (attributeNames.contains(parameter))
			attributes.put(parameter, value);
		else if (parameterNames.contains(parameter))
			parameters.put(parameter, value);
	}

	@Override
	public String getValue(String name) {
		String parameter = name.toLowerCase();
		String value = null;

		if ("data".equals(parameter)) {
			if (getClientProperties().isBrowserInternetExplorer())
				return null;
			parameter = "movie";
		}

		if (attributeNames.contains(parameter))
			value = attributes.get(parameter);
		else if (parameterNames.contains(parameter))
			value = parameters.get(parameter);

		// special treatment of movie to resolve to the url
		if (value != null && parameter.equals("movie"))
			value = resolveResource(value);

		return value;
	}

	@Override
	public void onComponentTag(ComponentTag tag) {
		// get options from the markup
		IValueMap valueMap = tag.getAttributes();

		// Iterate over valid options
		for (String s : optionNames) {
			if (valueMap.containsKey(s)) {
				// if option isn't set programmatically, set value from markup
				if (!attributes.containsKey(s) && !parameters.containsKey(s))
					setValue(s, valueMap.getString(s));
				// remove attribute - they are added in super.onComponentTag()
				// to
				// the right place as attribute or param
				valueMap.remove(s);
			}
		}

		super.onComponentTag(tag);
	}

	@Override
	public void onComponentTagBody(MarkupStream markupStream, ComponentTag openTag) {

		super.onComponentTagBody(markupStream, openTag);

		Response response = getResponse();

		// add all object's parameters in embed tag too:
		response.write("<embed");
		addParameter(response, "type", CONTENTTYPE);
		for (String name : getParameterNames()) {
			String value = getValue(name);
			if (value != null) {
				name = "movie".equals(name) ? "src" : name;
				addParameter(response, name, value);
			}
		}
		for (String name : getAttributeNames()) {
			if ("width".equals(name) || "height".equals(name)) {
				String value = getValue(name);
				if (value != null) {
					addParameter(response, name, value);
				}
			}
		}
		response.write(" />\n");

	}

	private void addParameter(Response response, String name, String value) {
		response.write(" ");
		response.write(name);
		response.write("=\"");
		response.write(value);
		response.write("\"");
	}

	@Override
	protected String getClsid() {
		return CLSID;
	}

	@Override
	protected String getCodebase() {
		return CODEBASE;
	}

	@Override
	protected String getContentType() {
		return CONTENTTYPE;
	}

	@Override
	protected List<String> getAttributeNames() {
		return attributeNames;
	}

	@Override
	protected List<String> getParameterNames() {
		return parameterNames;
	}
}