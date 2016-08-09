/*
 * Copyright 2014 gitblit.com.
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
package com.gitblit.wicket.panels;

import java.text.MessageFormat;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.MarkupStream;
import org.apache.wicket.model.IModel;

public abstract class IconAjaxLink<T> extends AjaxLink<T> {

	private static final long serialVersionUID = 1L;

	private final String iconClass;

	public IconAjaxLink(String wicketId, String iconClass, IModel<T> model) {
		super(wicketId, model);
		this.iconClass = iconClass;
	}

	@Override
	public void onComponentTagBody(MarkupStream markupStream, ComponentTag openTag) {
		replaceComponentTagBody(markupStream, openTag, MessageFormat.format("<i class=\"{0}\"></i> {1}", iconClass, getModelObject().toString()));
	}

	public void setNoFollow() {
		Component c = get("link");
		c.add(new AttributeModifier("rel", "nofollow"));
	}
}
