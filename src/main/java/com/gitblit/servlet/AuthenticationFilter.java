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
package com.gitblit.servlet;

import java.io.IOException;
import java.security.Principal;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.Constants.Role;
import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.models.UserModel;
import com.gitblit.utils.DeepCopier;
import com.gitblit.utils.StringUtils;

/**
 * The AuthenticationFilter is a servlet filter that preprocesses requests that
 * match its url pattern definition in the web.xml file.
 *
 * http://en.wikipedia.org/wiki/Basic_access_authentication
 *
 * @author James Moger
 *
 */
public abstract class AuthenticationFilter implements Filter {

	protected static final String CHALLENGE = "Basic realm=\"" + Constants.NAME + "\"";

	protected static final String SESSION_SECURED = "com.gitblit.secured";

	protected transient Logger logger = LoggerFactory.getLogger(getClass());

	protected IAuthenticationManager authenticationManager;

	protected AuthenticationFilter(IAuthenticationManager authenticationManager) {
		this.authenticationManager = authenticationManager;
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
	}

	@Override
	public void destroy() {
	}

	/**
	 * doFilter does the actual work of preprocessing the request to ensure that
	 * the user may proceed.
	 *
	 * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
	 *      javax.servlet.ServletResponse, javax.servlet.FilterChain)
	 */
	@Override
	public abstract void doFilter(final ServletRequest request, final ServletResponse response,
			final FilterChain chain) throws IOException, ServletException;

	/**
	 * Allow the filter to require a client certificate to continue processing.
	 *
	 * @return true, if a client certificate is required
	 */
	protected boolean requiresClientCertificate() {
		return false;
	}

	/**
	 * Returns the full relative url of the request.
	 *
	 * @param httpRequest
	 * @return url
	 */
	protected String getFullUrl(HttpServletRequest httpRequest) {
		String servletUrl = httpRequest.getContextPath() + httpRequest.getServletPath();
		String url = httpRequest.getRequestURI().substring(servletUrl.length());
		String params = httpRequest.getQueryString();
		if (url.length() > 0 && url.charAt(0) == '/') {
			url = url.substring(1);
		}
		String fullUrl = url + (StringUtils.isEmpty(params) ? "" : ("?" + params));
		return fullUrl;
	}

	/**
	 * Returns the user making the request, if the user has authenticated.
	 *
	 * @param httpRequest
	 * @return user
	 */
	protected UserModel getUser(HttpServletRequest httpRequest) {
		UserModel user = authenticationManager.authenticate(httpRequest, requiresClientCertificate());
		return user;
	}

	/**
	 * Taken from Jetty's LoginAuthenticator.renewSessionOnAuthentication()
	 */
	protected void newSession(HttpServletRequest request, HttpServletResponse response) {
		HttpSession oldSession = request.getSession(false);
		if (oldSession != null && oldSession.getAttribute(SESSION_SECURED) == null) {
			synchronized (this) {
				Map<String, Object> attributes = new HashMap<String, Object>();
				Enumeration<String> e = oldSession.getAttributeNames();
				while (e.hasMoreElements()) {
					String name = e.nextElement();
					attributes.put(name, oldSession.getAttribute(name));
					oldSession.removeAttribute(name);
				}
				oldSession.invalidate();

				HttpSession newSession = request.getSession(true);
				newSession.setAttribute(SESSION_SECURED, Boolean.TRUE);
				for (Map.Entry<String, Object> entry : attributes.entrySet()) {
					newSession.setAttribute(entry.getKey(), entry.getValue());
				}
			}
		}
	}

	/**
	 * Wraps a standard HttpServletRequest and overrides user principal methods.
	 */
	public static class AuthenticatedRequest extends HttpServletRequestWrapper {

		private UserModel user;

		public AuthenticatedRequest(HttpServletRequest req) {
			super(req);
			user = DeepCopier.copy(UserModel.ANONYMOUS);
		}

		UserModel getUser() {
			return user;
		}

		void setUser(UserModel user) {
			this.user = user;
		}

		@Override
		public String getRemoteUser() {
			return user.username;
		}

		@Override
		public boolean isUserInRole(String role) {
			if (role.equals(Role.ADMIN.getRole())) {
				return user.canAdmin();
			}
			// Gitblit does not currently use actual roles in the traditional
			// servlet container sense.  That is the reason this is marked
			// deprecated, but I may want to revisit this.
			return user.hasRepositoryPermission(role);
		}

		@Override
		public Principal getUserPrincipal() {
			return user;
		}
	}
}
