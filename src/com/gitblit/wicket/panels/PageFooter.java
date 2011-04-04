package com.gitblit.wicket.panels;

import java.util.Date;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;

import com.gitblit.StoredSettings;
import com.gitblit.wicket.GitBlitWebSession;


public class PageFooter extends Panel {

	private static final long serialVersionUID = 1L;

	public PageFooter(String id) {
		this(id, "");
	}

	public PageFooter(String id, String description) {
		super(id);
		add(new Label("cacheTime", "Page Last Updated: " + GitBlitWebSession.get().formatDateTimeLong(new Date())));
		add(new Label("footerText", description));
		if (StoredSettings.getBoolean("aggressiveGC", false)) {
			System.gc();
		}
	}
}