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

import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.ResourceReference;
import org.apache.wicket.core.request.ClientInfo;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.MarkupStream;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.protocol.http.ClientProperties;
import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.protocol.http.request.WebClientInfo;
import org.apache.wicket.request.Response;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.util.value.IValueMap;

/**
 * https://cwiki.apache.org/WICKET/object-container-adding-flash-to-a-wicket-application.html
 */
public abstract class ObjectContainer extends WebMarkupContainer {

	private static final long serialVersionUID = 1L;

	// Some general attributes for the object tag:
	private static final String ATTRIBUTE_CONTENTTYPE = "type";
	private static final String ATTRIBUTE_CLASSID = "classid";
	private static final String ATTRIBUTE_CODEBASE = "codebase";

	// This is used for browser specific adjustments
	private ClientProperties clientProperties = null;

	public ObjectContainer(String id) {
		super(id);
	}

	// Set an attribute/property
	public abstract void setValue(String name, String value);

	// Get an attribute/property
	public abstract String getValue(String name);

	// Set the object's content type
	protected abstract String getContentType();

	// Set the object's clsid (for IE)
	protected abstract String getClsid();

	// Where to get the browser plugin (for IE)
	protected abstract String getCodebase();

	// Object's valid attribute names
	protected abstract List<String> getAttributeNames();

	// Object's valid parameter names
	protected abstract List<String> getParameterNames();

	// Utility function to get the URL for the object's data
	protected String resolveResource(String src) {
		// if it's an absolute path, return it:
		if (src.startsWith("/") || src.startsWith("http://") || src.startsWith("https://"))
			return (src);

		// use the parent container class to resolve the resource reference
		Component parent = getParent();
		if (parent instanceof Fragment) {
			// must check for fragment, otherwise we end up in Wicket namespace
			parent = parent.getParent();
		}
		if (parent != null) {
			ResourceReference resRef = new ResourceReference(parent.getClass(), src, false);
			return (urlFor(resRef).toString());
		}

		return (src);
	}

	@Override
	public void onComponentTag(ComponentTag tag) {
		super.onComponentTag(tag);

		// get the attributes from the html-source
		IValueMap attributeMap = tag.getAttributes();

		// set the content type
		String contentType = getContentType();
		if (contentType != null && !"".equals(contentType))
			attributeMap.put(ATTRIBUTE_CONTENTTYPE, contentType);

		// set clsid and codebase for IE
		if (getClientProperties().isBrowserInternetExplorer()) {
			String clsid = getClsid();
			String codeBase = getCodebase();

			if (clsid != null && !"".equals(clsid))
				attributeMap.put(ATTRIBUTE_CLASSID, clsid);
			if (codeBase != null && !"".equals(codeBase))
				attributeMap.put(ATTRIBUTE_CODEBASE, codeBase);
		}

		// add all attributes
		for (String name : getAttributeNames()) {
			String value = getValue(name);
			if (value != null)
				attributeMap.put(name, value);
		}
	}

	@Override
	public void onComponentTagBody(MarkupStream markupStream, ComponentTag openTag) {
		Response response = getResponse();
		response.write("\n");

		// add all object's parameters:
		for (String name : getParameterNames()) {
			String value = getValue(name);
			if (value != null) {
				response.write("<param name=\"");
				response.write(name);
				response.write("\" value=\"");
				response.write(value);
				response.write("\"/>\n");
			}
		}

		super.onComponentTagBody(markupStream, openTag);
	}

	// shortcut to the client properties:
	protected ClientProperties getClientProperties() {
		if (clientProperties == null) {
			ClientInfo clientInfo = WebSession.get().getClientInfo();

			if (clientInfo == null || !(clientInfo instanceof WebClientInfo)) {
				clientInfo = new WebClientInfo(getRequestCycle());
				WebSession.get().setClientInfo(clientInfo);
			}

			clientProperties = ((WebClientInfo) clientInfo).getProperties();
		}
		return (clientProperties);
	}
}