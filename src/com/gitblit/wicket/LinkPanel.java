package com.gitblit.wicket;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

public class LinkPanel extends Panel {

	private static final long serialVersionUID = 1L;

	private IModel<String> labelModel = new Model<String>();

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public LinkPanel(String wicketId, final String linkCssClass, String label, Class<? extends WebPage> clazz, PageParameters parameters) {
		super(wicketId);
		Link<?> link = null;
		if (parameters == null) {
			link = new BookmarkablePageLink("link", clazz);
		} else {
			link = new BookmarkablePageLink("link", clazz, parameters);
		}
		if (linkCssClass != null) {
			link.add(new AttributeModifier("class", true, new AbstractReadOnlyModel<String>() {
				private static final long serialVersionUID = 1L;

				@Override
				public String getObject() {
					return linkCssClass;
				}
			}));
		}
		labelModel.setObject(label);
		link.add(new Label("label", labelModel));
		add(link);
	}

}
