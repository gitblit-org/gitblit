/*
 * Copyright 2013 Laurens Vrijnsen
 * Copyright 2013 gitblit.com.
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
 */package com.gitblit.servlet;

import java.io.IOException;
import java.text.MessageFormat;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.dagger.DaggerFilter;
import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.models.UserModel;

import dagger.ObjectGraph;

/**
 * This filter enforces authentication via HTTP Basic Authentication, if the settings indicate so.
 * It looks at the settings "web.authenticateViewPages" and "web.enforceHttpBasicAuthentication"; if
 * both are true, any unauthorized access will be met with a HTTP Basic Authentication header.
 *
 * @author Laurens Vrijnsen
 *
 */
public class EnforceAuthenticationFilter extends DaggerFilter {

	protected transient Logger logger = LoggerFactory.getLogger(getClass());

	private IStoredSettings settings;

	private IAuthenticationManager authenticationManager;

	@Override
	protected void inject(ObjectGraph dagger) {
		this.settings = dagger.get(IStoredSettings.class);
		this.authenticationManager = dagger.get(IAuthenticationManager.class);
	}

	/*
	 * This does the actual filtering: is the user authenticated? If not, enforce HTTP authentication (401)
	 *
	 * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
	 */
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

		Boolean mustForceAuth = settings.getBoolean(Keys.web.authenticateViewPages, false)
								&& settings.getBoolean(Keys.web.enforceHttpBasicAuthentication, false);

		HttpServletRequest  httpRequest  = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;
		UserModel user = authenticationManager.authenticate(httpRequest);

		if (mustForceAuth && (user == null)) {
			// not authenticated, enforce now:
			logger.debug(MessageFormat.format("EnforceAuthFilter: user not authenticated for URL {0}!", request.toString()));
			String challenge = MessageFormat.format("Basic realm=\"{0}\"", settings.getString(Keys.web.siteName, ""));
			httpResponse.setHeader("WWW-Authenticate", challenge);
			httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;

		} else {
			// user is authenticated, or don't care, continue handling
			chain.doFilter(request, response);
		}
	}


	/*
	 * @see javax.servlet.Filter#destroy()
	 */
	@Override
	public void destroy() {
	}
}
