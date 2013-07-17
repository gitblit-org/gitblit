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
import java.text.MessageFormat;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;

/**
 * The AccessRestrictionFilter is an AuthenticationFilter that confirms that the
 * requested repository can be accessed by the anonymous or named user.
 * 
 * The filter extracts the name of the repository from the url and determines if
 * the requested action for the repository requires a Basic authentication
 * prompt. If authentication is required and no credentials are stored in the
 * "Authorization" header, then a basic authentication challenge is issued.
 * 
 * http://en.wikipedia.org/wiki/Basic_access_authentication
 * 
 * @author James Moger
 * 
 */
public abstract class AccessRestrictionFilter extends AuthenticationFilter {

	/**
	 * Extract the repository name from the url.
	 * 
	 * @param url
	 * @return repository name
	 */
	protected abstract String extractRepositoryName(String url);

	/**
	 * Analyze the url and returns the action of the request.
	 * 
	 * @param url
	 * @return action of the request
	 */
	protected abstract String getUrlRequestAction(String url);

	/**
	 * Determine if a non-existing repository can be created using this filter.
	 *  
	 * @return true if the filter allows repository creation
	 */
	protected abstract boolean isCreationAllowed();
	
	/**
	 * Determine if the action may be executed on the repository.
	 * 
	 * @param repository
	 * @param action
	 * @return true if the action may be performed
	 */
	protected abstract boolean isActionAllowed(RepositoryModel repository, String action);

	/**
	 * Determine if the repository requires authentication.
	 * 
	 * @param repository
	 * @param action
	 * @return true if authentication required
	 */
	protected abstract boolean requiresAuthentication(RepositoryModel repository, String action);

	/**
	 * Determine if the user can access the repository and perform the specified
	 * action.
	 * 
	 * @param repository
	 * @param user
	 * @param action
	 * @return true if user may execute the action on the repository
	 */
	protected abstract boolean canAccess(RepositoryModel repository, UserModel user, String action);

	/**
	 * Allows a filter to create a repository, if one does not exist.
	 * 
	 * @param user
	 * @param repository
	 * @param action
	 * @return the repository model, if it is created, null otherwise
	 */
	protected RepositoryModel createRepository(UserModel user, String repository, String action) {
		return null;
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
		String repository = extractRepositoryName(fullUrl);
		
		if (GitBlit.self().isCollectingGarbage(repository)) {
			logger.info(MessageFormat.format("ARF: Rejecting request for {0}, busy collecting garbage!", repository));
			httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		// Determine if the request URL is restricted
		String fullSuffix = fullUrl.substring(repository.length());
		String urlRequestType = getUrlRequestAction(fullSuffix);

		UserModel user = getUser(httpRequest);

		// Load the repository model
		RepositoryModel model = GitBlit.self().getRepositoryModel(repository);
		if (model == null) {
			if (isCreationAllowed()) {
				if (user == null) {
					// challenge client to provide credentials for creation. send 401.
					if (GitBlit.isDebugMode()) {
						logger.info(MessageFormat.format("ARF: CREATE CHALLENGE {0}", fullUrl));
					}
					httpResponse.setHeader("WWW-Authenticate", CHALLENGE);
					httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
					return;
				} else {
					// see if we can create a repository for this request
					model = createRepository(user, repository, urlRequestType);
				}
			}
			
			if (model == null) {
				// repository not found. send 404.
				logger.info(MessageFormat.format("ARF: {0} ({1})", fullUrl,
						HttpServletResponse.SC_NOT_FOUND));
				httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
				return;
			}
		}
		
		// Confirm that the action may be executed on the repository
		if (!isActionAllowed(model, urlRequestType)) {
			logger.info(MessageFormat.format("ARF: action {0} on {1} forbidden ({2})",
					urlRequestType, model, HttpServletResponse.SC_FORBIDDEN));
			httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
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
		if (user != null) {
			authenticatedRequest.setUser(user);
		}

		// BASIC authentication challenge and response processing
		if (!StringUtils.isEmpty(urlRequestType) && requiresAuthentication(model, urlRequestType)) {
			if (user == null) {
				// challenge client to provide credentials. send 401.
				if (GitBlit.isDebugMode()) {
					logger.info(MessageFormat.format("ARF: CHALLENGE {0}", fullUrl));
				}
				httpResponse.setHeader("WWW-Authenticate", CHALLENGE);
				httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
				return;
			} else {
				// check user access for request
				if (user.canAdmin() || canAccess(model, user, urlRequestType)) {
					// authenticated request permitted.
					// pass processing to the restricted servlet.
					newSession(authenticatedRequest, httpResponse);
					logger.info(MessageFormat.format("ARF: {0} ({1}) authenticated", fullUrl,
							HttpServletResponse.SC_CONTINUE));
					chain.doFilter(authenticatedRequest, httpResponse);
					return;
				}
				// valid user, but not for requested access. send 403.
				if (GitBlit.isDebugMode()) {
					logger.info(MessageFormat.format("ARF: {0} forbidden to access {1}",
							user.username, fullUrl));
				}
				httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
				return;
			}
		}

		if (GitBlit.isDebugMode()) {
			logger.info(MessageFormat.format("ARF: {0} ({1}) unauthenticated", fullUrl,
					HttpServletResponse.SC_CONTINUE));
		}
		// unauthenticated request permitted.
		// pass processing to the restricted servlet.
		chain.doFilter(authenticatedRequest, httpResponse);
	}
}