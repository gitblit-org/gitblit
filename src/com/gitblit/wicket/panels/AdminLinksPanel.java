package com.gitblit.wicket.panels;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;

public class AdminLinksPanel extends Panel {

	private static final long serialVersionUID = 1L;

	public AdminLinksPanel(String id) {
		super(id);

		add(new Label("newRepository", "new repository"));
		add(new Label("newUser", "new user"));
	}
}