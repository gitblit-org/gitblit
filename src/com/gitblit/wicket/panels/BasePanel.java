package com.gitblit.wicket.panels;

import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.StringResourceModel;

public abstract class BasePanel extends Panel {

	private static final long serialVersionUID = 1L;

	public BasePanel(String id) {
		super(id);
	}
	
	public StringResourceModel stringModel(String key) {
		return new StringResourceModel(key, this, null);
	}
}
