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
import java.text.MessageFormat;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.manager.IProjectManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.models.ProjectModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;

/**
 * The SyndicationFilter is an AuthenticationFilter which ensures that feed
 * requests for projects or view-restricted repositories have proper authentication
 * credentials and are authorized for the requested feed.
 *
 * @author James Moger
 *
 */
@Singleton
public class SyndicationFilter extends AuthenticationFilter {

	private IRuntimeManager runtimeManager;
	private IRepositoryManager repositoryManager;
	private IProjectManager projectManager;

	@Inject
	public SyndicationFilter(
			IRuntimeManager runtimeManager,
			IAuthenticationManager authenticationManager,
			IRepositoryManager repositoryManager,
			IProjectManager projectManager) {
		super(authenticationManager);

		this.runtimeManager = runtimeManager;
		this.repositoryManager = repositoryManager;
		this.projectManager = projectManager;
	}

	/**
	 * Extract the repository name from the url.
	 *
	 * @param url
	 * @return repository name
	 */
	protected String extractRequestedName(String url) {
		if (url.indexOf('?') > -1) {
			return url.substring(0, url.indexOf('?'));
		}
		return url;
	}

	/**
	 * doFilter does the actual work of preprocessing the request to ensure that
	 * the user may proceed.
	 *
	 * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
	 *      javax.servlet.ServletResponse, javax.servlet.FilterChain)
	 */
	@Override
	public void doFilter(final ServletRequest request, final ServletResponse response,
			final FilterChain chain) throws IOException, ServletException {

		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		String fullUrl = getFullUrl(httpRequest);
		String name = extractRequestedName(fullUrl);

		ProjectModel project = projectManager.getProjectModel(name);
		RepositoryModel model = null;

		if (project == null) {
			// try loading a repository model
			model = repositoryManager.getRepositoryModel(name);
			if (model == null) {
				// repository not found. send 404.
				logger.info(MessageFormat.format("ARF: {0} ({1})", fullUrl,
						HttpServletResponse.SC_NOT_FOUND));
				httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}
		}

		// Wrap the HttpServletRequest with the AccessRestrictionRequest which
		// overrides the servlet container user principal methods.
		// JGit requires either:
		//
		// 1. servlet container authenticated user
		// 2. http.receivepack = true in each repository's config
		//
		// Gitblit must conditionally authenticate users per-repository so just
		// enabling http.receivepack is insufficient.
		AuthenticatedRequest authenticatedRequest = new AuthenticatedRequest(httpRequest);
		UserModel user = getUser(httpRequest);
		if (user != null) {
			authenticatedRequest.setUser(user);
		}

		// BASIC authentication challenge and response processing
		if (model != null) {
			if (model.accessRestriction.atLeast(AccessRestrictionType.VIEW)) {
				if (user == null) {
					// challenge client to provide credentials. send 401.
					if (runtimeManager.isDebugMode()) {
						logger.info(MessageFormat.format("ARF: CHALLENGE {0}", fullUrl));
					}
					httpResponse.setHeader("WWW-Authenticate", CHALLENGE);
					httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
					return;
				} else {
					// check user access for request
					if (user.canView(model)) {
						// authenticated request permitted.
						// pass processing to the restricted servlet.
						newSession(authenticatedRequest, httpResponse);
						logger.info(MessageFormat.format("ARF: {0} ({1}) authenticated", fullUrl,
								HttpServletResponse.SC_CONTINUE));
						chain.doFilter(authenticatedRequest, httpResponse);
						return;
					}
					// valid user, but not for requested access. send 403.
					if (runtimeManager.isDebugMode()) {
						logger.info(MessageFormat.format("ARF: {0} forbidden to access {1}",
								user.username, fullUrl));
					}
					httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
					return;
				}
			}
		}

		if (runtimeManager.isDebugMode()) {
			logger.info(MessageFormat.format("ARF: {0} ({1}) unauthenticated", fullUrl,
					HttpServletResponse.SC_CONTINUE));
		}
		// unauthenticated request permitted.
		// pass processing to the restricted servlet.
		chain.doFilter(authenticatedRequest, httpResponse);
	}
}
