package com.gitblit.wicket;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.apache.wicket.Request;
import org.apache.wicket.Session;
import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.protocol.http.request.WebClientInfo;

import com.gitblit.StoredSettings;


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

	public String formatTime(Date date) {
		DateFormat df = new SimpleDateFormat(StoredSettings.getString("timestampFormat", "h:mm a"));
		df.setTimeZone(getTimezone());
		return df.format(date);
	}

	public String formatDate(Date date) {
		DateFormat df = new SimpleDateFormat(StoredSettings.getString("datestampShortFormat", "MM/dd/yy"));
		df.setTimeZone(getTimezone());
		return df.format(date);
	}

	public String formatDateLong(Date date) {
		DateFormat df = new SimpleDateFormat(StoredSettings.getString("datestampLongFormat", "EEEE, MMMM d, yyyy"));
		df.setTimeZone(getTimezone());
		return df.format(date);
	}

	public String formatDateTime(Date date) {
		DateFormat df = new SimpleDateFormat(StoredSettings.getString("datetimestampShortFormat", "MM/dd/yy h:mm a"));
		df.setTimeZone(getTimezone());
		return df.format(date);
	}

	public String formatDateTimeLong(Date date) {
		DateFormat df = new SimpleDateFormat(StoredSettings.getString("datetimestampLongFormat", "EEEE, MMMM d, yyyy h:mm a"));
		df.setTimeZone(getTimezone());
		return df.format(date);
	}

	public static GitBlitWebSession get() {
		return (GitBlitWebSession) Session.get();
	}
}