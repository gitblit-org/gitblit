package com.gitblit.wicket;

import java.util.TimeZone;

import org.apache.wicket.Request;
import org.apache.wicket.Session;
import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.protocol.http.request.WebClientInfo;

public final class GitBlitWebSession extends WebSession {

	private static final long serialVersionUID = 1L;

	protected TimeZone timezone = null;

	private User user = null;

	public GitBlitWebSession(Request request) {
		super(request);
	}

	public void invalidate() {
		super.invalidate();
		user = null;
	}

	public boolean isLoggedIn() {
		return user != null;
	}

	public boolean canAdmin() {
		if (user == null) {
			return false;
		}
		return user.canAdmin();
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
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