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
package com.gitblit.wicket;

import org.apache.wicket.PageParameters;
import org.apache.wicket.behavior.SimpleAttributeModifier;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

public class LinkPanel extends Panel {

	private static final long serialVersionUID = 1L;

	private final IModel<String> labelModel;

	public LinkPanel(String wicketId, String linkCssClass, String label, Class<? extends WebPage> clazz) {
		this(wicketId, linkCssClass, new Model<String>(label), clazz, null);
	}
	
	public LinkPanel(String wicketId, String linkCssClass, String label, Class<? extends WebPage> clazz, PageParameters parameters) {
		this(wicketId, linkCssClass, new Model<String>(label), clazz, parameters);
	}

	public LinkPanel(String wicketId, String linkCssClass, IModel<String> model, Class<? extends WebPage> clazz, PageParameters parameters) {
		super(wicketId);
		this.labelModel = model;
		Link<Void> link = null;
		if (parameters == null) {
			link = new BookmarkablePageLink<Void>("link", clazz);
		} else {
			link = new BookmarkablePageLink<Void>("link", clazz, parameters);
		}
		if (linkCssClass != null) {
			link.add(new SimpleAttributeModifier("class", linkCssClass));
		}
		link.add(new Label("label", labelModel));
		add(link);
	}

}
