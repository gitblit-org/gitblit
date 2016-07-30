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

import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.wicket.Page;
import org.apache.wicket.request.IRequestParameters;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Url;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.flow.RedirectToUrlException;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.Session;
import org.apache.wicket.protocol.http.RequestUtils;
import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.protocol.http.request.WebClientInfo;

import com.gitblit.GitBlitServer.Params;
import com.gitblit.models.UserModel;

public final class GitBlitWebSession extends WebSession {

	private static final long serialVersionUID = 1L;

	protected TimeZone timezone;

	private UserModel user;

	private String errorMessage;

	private String requestUrl;

	private AtomicBoolean isForking;

	public GitBlitWebSession(Request request) {
		super(request);
		isForking = new AtomicBoolean();
	}

	@Override
	public void invalidate() {
		super.invalidate();
		user = null;
	}

	/**
	 * Cache the requested protected resource pending successful authentication.
	 *
	 * @param pageClass
	 */
	public void cacheRequest(Class<? extends Page> pageClass) {
		// build absolute url with correctly encoded parameters?!
		Request req = RequestCycle.get().getRequest();
		IRequestParameters params = req.getRequestParameters();
		PageParameters pageParams = new PageParameters();
		params.getParameterNames().forEach(name->{
			pageParams.add(name, params.getParameterValue(name));
		});
		String relativeUrl = RequestCycle.get().urlFor(pageClass, pageParams).toString();
		requestUrl = RequestUtils.toAbsolutePath(relativeUrl);
//		String relativeUrl = RequestCycle.get().urlFor(pageClass, pageParams).toString();
//		requestUrl = RequestCycle.get().getUrlRenderer().renderFullUrl(Url.parse(relativeUrl));
		
		if (isTemporary())
		{
			// we must bind the temporary session into the session store
			// so that we can re-use this session for reporting an error message
			// on the redirected page and continuing the request after
			// authentication.
			bind();
		}
	}

	/**
	 * Continue any cached request.  This is used when a request for a protected
	 * resource is aborted/redirected pending proper authentication.  Gitblit
	 * no longer uses Wicket's built-in mechanism for this because of Wicket's
	 * failure to properly handle parameters with forward-slashes.  This is a
	 * constant source of headaches with Wicket.
	 *
	 * @return false if there is no cached request to process
	 */
	public boolean continueRequest() {
		if (requestUrl != null) {
			String url = requestUrl;
			requestUrl = null;
			throw new RedirectToUrlException(url);
		}
		return false;
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

	public String getUsername() {
		return user == null ? "anonymous" : user.username;
	}

	public UserModel getUser() {
		return user;
	}

	public void setUser(UserModel user) {
		this.user = user;
		if (user != null) {
			Locale preferredLocale = user.getPreferences().getLocale();
			if (preferredLocale != null) {
				// set the user's preferred locale
				setLocale(preferredLocale);
			}
		}
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

	public boolean isForking() {
		return isForking.get();
	}

	public void isForking(boolean val) {
		isForking.set(val);
	}

	public static GitBlitWebSession get() {
		return (GitBlitWebSession) Session.get();
	}
}