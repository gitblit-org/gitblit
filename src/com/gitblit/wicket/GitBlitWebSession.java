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
package com.gitblit.wicket;

import java.util.TimeZone;

import org.apache.wicket.Request;
import org.apache.wicket.Session;
import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.protocol.http.request.WebClientInfo;

import com.gitblit.models.UserModel;

public final class GitBlitWebSession extends WebSession {

	private static final long serialVersionUID = 1L;

	protected TimeZone timezone;

	private UserModel user;

	private String errorMessage;

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
		return user.canAdmin;
	}

	public UserModel getUser() {
		return user;
	}

	public void setUser(UserModel user) {
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

	public void cacheErrorMessage(String message) {
		this.errorMessage = message;
	}

	public String clearErrorMessage() {
		String msg = errorMessage;
		errorMessage = null;
		return msg;
	}

	public static GitBlitWebSession get() {
		return (GitBlitWebSession) Session.get();
	}
}