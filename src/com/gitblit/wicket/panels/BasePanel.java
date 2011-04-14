package com.gitblit.wicket.panels;

import java.util.TimeZone;

import org.apache.wicket.markup.html.panel.Panel;

import com.gitblit.Keys;
import com.gitblit.StoredSettings;
import com.gitblit.wicket.GitBlitWebSession;

public abstract class BasePanel extends Panel {

	private static final long serialVersionUID = 1L;

	public BasePanel(String wicketId) {
		super(wicketId);
	}

	protected TimeZone getTimeZone() {
		return StoredSettings.getBoolean(Keys.web_useClientTimezone, false) ? GitBlitWebSession.get().getTimezone() : TimeZone.getDefault();
	}
}
