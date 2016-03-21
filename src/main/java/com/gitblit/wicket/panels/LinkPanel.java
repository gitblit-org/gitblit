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
package com.gitblit.wicket.panels;

import java.io.OutputStream;
import java.util.concurrent.Callable;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.behavior.SimpleAttributeModifier;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.target.resource.ResourceStreamRequestTarget;
import org.apache.wicket.util.resource.AbstractResourceStreamWriter;
import org.apache.wicket.util.resource.IResourceStream;

import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;

public class LinkPanel extends Panel {

	private static final long serialVersionUID = 1L;

	private final IModel<String> labelModel;

	public LinkPanel(String wicketId, String linkCssClass, String label,
			Class<? extends WebPage> clazz) {
		this(wicketId, null, linkCssClass, new Model<String>(label), clazz, null, false);
	}

	public LinkPanel(String wicketId, String linkCssClass, String label,
			Class<? extends WebPage> clazz, PageParameters parameters) {
		this(wicketId, null, linkCssClass, new Model<String>(label), clazz, parameters, false);
	}

	public LinkPanel(String wicketId, String linkCssClass, String label,
			Class<? extends WebPage> clazz, PageParameters parameters, boolean newWindow) {
		this(wicketId, null, linkCssClass, new Model<String>(label), clazz, parameters, newWindow);
	}

	public LinkPanel(String wicketId, String bootstrapIcon, String linkCssClass, String label,
			Class<? extends WebPage> clazz, PageParameters parameters, boolean newWindow) {
		this(wicketId, bootstrapIcon, linkCssClass, new Model<String>(label), clazz, parameters, newWindow);
	}

	public LinkPanel(String wicketId, String linkCssClass, IModel<String> model,
			Class<? extends WebPage> clazz, PageParameters parameters) {
		this(wicketId, null, linkCssClass, model, clazz, parameters, false);
	}

	public LinkPanel(String wicketId, String bootstrapIcon, String linkCssClass, IModel<String> model,
			Class<? extends WebPage> clazz, PageParameters parameters, boolean newWindow) {
		super(wicketId);
		this.labelModel = model;
		Link<Void> link = null;
		if (parameters == null) {
			link = new BookmarkablePageLink<Void>("link", clazz);
		} else {
			link = new BookmarkablePageLink<Void>("link", clazz, parameters);
		}
		if (newWindow) {
			link.add(new SimpleAttributeModifier("target", "_blank"));
		}
		if (linkCssClass != null) {
			link.add(new SimpleAttributeModifier("class", linkCssClass));
		}
		Label icon = new Label("icon");
		if (StringUtils.isEmpty(bootstrapIcon)) {
			link.add(icon.setVisible(false));
		} else {
			WicketUtils.setCssClass(icon, bootstrapIcon);
			link.add(icon);
		}
		link.add(new Label("label", labelModel).setRenderBodyOnly(true));
		add(link);
	}

	public LinkPanel(String wicketId, String linkCssClass, String label, String href) {
		this(wicketId, linkCssClass, label, href, false);
	}

	public LinkPanel(String wicketId, String linkCssClass, String label, String href,
			boolean newWindow) {
		super(wicketId);
		this.labelModel = new Model<String>(label);
		ExternalLink link = new ExternalLink("link", href);
		if (newWindow) {
			link.add(new SimpleAttributeModifier("target", "_blank"));
		}
		if (linkCssClass != null) {
			link.add(new SimpleAttributeModifier("class", linkCssClass));
		}
		link.add(new Label("icon").setVisible(false));
		link.add(new Label("label", labelModel));
		add(link);
	}

	public LinkPanel(String wicketId, String linkCssClass, String label, Link<?> link) {
		super(wicketId);
		
		this.labelModel = new Model<String>(label);
		
		if (linkCssClass != null) {
			link.add(new SimpleAttributeModifier("class", linkCssClass));
		}
		
		link.add(new Label("icon").setVisible(false));
		link.add(new Label("label", labelModel));
		add(link);
	}

	public void setNoFollow() {
		Component c = get("link");
		c.add(new SimpleAttributeModifier("rel", "nofollow"));
	}

	public void setTooltip(String tooltip) {
		Component c = get("link");
		c.add(new SimpleAttributeModifier("title", tooltip));
	}

}
