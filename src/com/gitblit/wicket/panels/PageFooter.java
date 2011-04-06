package com.gitblit.wicket.panels;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;

import com.gitblit.Constants;
import com.gitblit.StoredSettings;


public class PageFooter extends Panel {

	private static final long serialVersionUID = 1L;

	public PageFooter(String id) {
		this(id, "");
	}

	public PageFooter(String id, String description) {
		super(id);		
		add(new Label("footerText", description));
		add(new Label("gbVersion", "v" + Constants.VERSION));
		if (StoredSettings.getBoolean("aggressiveHeapManagement", false)) {
			System.gc();
		}
	}
}