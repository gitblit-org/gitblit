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
package com.gitblit;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.Principal;
import java.text.MessageFormat;
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
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.eclipse.jgit.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;

/**
 * 
 * http://en.wikipedia.org/wiki/Basic_access_authentication
 */
public abstract class AccessRestrictionFilter implements Filter {

	private static final String BASIC = "Basic";

	private static final String CHALLENGE = BASIC + " realm=\"" + Constants.NAME + "\"";

	private static final String SESSION_SECURED = "com.gitblit.secured";

	protected transient Logger logger;

	public AccessRestrictionFilter() {
		logger = LoggerFactory.getLogger(getClass());
	}

	protected abstract String extractRepositoryName(String url);

	protected abstract String getUrlRequestType(String url);

	protected abstract boolean requiresAuthentication(RepositoryModel repository);

	protected abstract boolean canAccess(RepositoryModel repository, UserModel user,
			String restrictedUrl);

	@Override
	public void doFilter(final ServletRequest request, final ServletResponse response,
			final FilterChain chain) throws IOException, ServletException {

		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		// Wrap the HttpServletRequest with the AccessRestrictionRequest which
		// overrides the servlet container user principal methods.
		// JGit requires either:
		//
		// 1. servlet container authenticated user
		// 2. http.receivepack = true in each repository's config
		//
		// Gitblit must conditionally authenticate users per-repository so just
		// enabling http.receivepack is insufficient.

		AccessRestrictionRequest accessRequest = new AccessRestrictionRequest(httpRequest);

		String url = httpRequest.getRequestURI().substring(httpRequest.getServletPath().length());
		String params = httpRequest.getQueryString();
		if (url.length() > 0 && url.charAt(0) == '/') {
			url = url.substring(1);
		}
		String fullUrl = url + (StringUtils.isEmpty(params) ? "" : ("?" + params));

		String repository = extractRepositoryName(url);

		// Determine if the request URL is restricted
		String fullSuffix = fullUrl.substring(repository.length());
		String urlRequestType = getUrlRequestType(fullSuffix);

		// Load the repository model
		RepositoryModel model = GitBlit.self().getRepositoryModel(repository);
		if (model == null) {
			// repository not found. send 404.
			logger.info("ARF: " + fullUrl + " (" + HttpServletResponse.SC_NOT_FOUND + ")");
			httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}

		// BASIC authentication challenge and response processing
		if (!StringUtils.isEmpty(urlRequestType) && requiresAuthentication(model)) {
			// look for client authorization credentials in header
			final String authorization = httpRequest.getHeader("Authorization");
			if (authorization != null && authorization.startsWith(BASIC)) {
				// Authorization: Basic base64credentials
				String base64Credentials = authorization.substring(BASIC.length()).trim();
				String credentials = new String(Base64.decode(base64Credentials),
						Charset.forName("UTF-8"));
				// credentials = username:password
				final String[] values = credentials.split(":");

				if (values.length == 2) {
					String username = values[0];
					char[] password = values[1].toCharArray();
					UserModel user = GitBlit.self().authenticate(username, password);
					if (user != null) {
						accessRequest.setUser(user);
						if (user.canAdmin || canAccess(model, user, urlRequestType)) {
							// authenticated request permitted.
							// pass processing to the restricted servlet.
							newSession(accessRequest, httpResponse);
							logger.info("ARF: " + fullUrl + " (" + HttpServletResponse.SC_CONTINUE
									+ ") authenticated");
							chain.doFilter(accessRequest, httpResponse);
							return;
						}
						// valid user, but not for requested access. send 403.
						if (GitBlit.isDebugMode()) {
							logger.info("ARF: " + fullUrl + " (" + HttpServletResponse.SC_FORBIDDEN
									+ ")");
							logger.info(MessageFormat.format("AUTH: {0} forbidden to access {1}",
									user.username, url));
						}
						httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
						return;
					}
				}
				if (GitBlit.isDebugMode()) {
					logger.info(MessageFormat
							.format("AUTH: invalid credentials ({0})", credentials));
				}
			}

			// challenge client to provide credentials. send 401.
			if (GitBlit.isDebugMode()) {
				logger.info("ARF: " + fullUrl + " (" + HttpServletResponse.SC_UNAUTHORIZED + ")");
				logger.info("AUTH: Challenge " + CHALLENGE);
			}
			httpResponse.setHeader("WWW-Authenticate", CHALLENGE);
			httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		if (GitBlit.isDebugMode()) {
			logger.info("ARF: " + fullUrl + " (" + HttpServletResponse.SC_CONTINUE
					+ ") unauthenticated");
		}
		// unauthenticated request permitted.
		// pass processing to the restricted servlet.
		chain.doFilter(accessRequest, httpResponse);
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

	@Override
	public void init(final FilterConfig config) throws ServletException {
	}

	@Override
	public void destroy() {
	}

	/**
	 * Wraps a standard HttpServletRequest and overrides user principal methods.
	 */
	public static class AccessRestrictionRequest extends ServletRequestWrapper {

		private UserModel user;

		public AccessRestrictionRequest(HttpServletRequest req) {
			super(req);
			user = new UserModel("anonymous");
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
			if (role.equals(Constants.ADMIN_ROLE)) {
				return user.canAdmin;
			}
			return user.canAccessRepository(role);
		}

		@Override
		public Principal getUserPrincipal() {
			return user;
		}
	}
}