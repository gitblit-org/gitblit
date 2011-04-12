package com.gitblit.wicket;

import java.util.TimeZone;

import org.apache.wicket.Request;
import org.apache.wicket.Session;
import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.protocol.http.request.WebClientInfo;

public final class GitBlitWebSession extends WebSession {

	private static final long serialVersionUID = 1L;

	protected TimeZone timezone = null;

	public GitBlitWebSession(Request request) {
		super(request);
	}

	public void invalidate() {
		super.invalidate();
	}

	public TimeZone getTimezone() {
		if (timezone == null) {
			timezone = ((WebClientInfo) getClientInfo()).getProperties().getTimeZone();
		}
		// use server timezone if we can't determine the client timezone
		if (timezone == null) {
			timezone = TimeZone.getDefault();
		}
		return timezone;
	}

	public static GitBlitWebSession get() {
		return (GitBlitWebSession) Session.get();
	}
}