/*
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
 */
package com.gitblit;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;

/**
 * Handles requests for Sparkleshare Invites
 * 
 * @author James Moger
 * 
 */
public class SparkleShareInviteServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	public SparkleShareInviteServlet() {
		super();
	}
	
	/**
	 * Returns an Sparkleshare invite url to this servlet for the repository.
	 * https://github.com/hbons/SparkleShare/wiki/Invites
	 * 
	 * @param baseURL
	 * @param repository
	 * @param username
	 * @return an url
	 */
	public static String asLink(String baseURL, String repository, String username) {
		if (baseURL.length() > 0 && baseURL.charAt(baseURL.length() - 1) == '/') {
			baseURL = baseURL.substring(0, baseURL.length() - 1);
		}
		String url = baseURL + Constants.SPARKLESHARE_INVITE_PATH
				+ ((StringUtils.isEmpty(username) ? "" : (username + "@")))
				+ repository + ".xml";
		url = url.replace("https://", "sparkleshare://");
		url = url.replace("http://", "sparkleshare-unsafe://");
		return url;
	}
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, java.io.IOException {
		processRequest(request, response);
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		processRequest(request, response);
	}

	protected void processRequest(javax.servlet.http.HttpServletRequest request,
			javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException,
			java.io.IOException {		
		
		// extract repo name from request
		String path = request.getPathInfo();
		if (path != null && path.length() > 1) {
			if (path.charAt(0) == '/') {
				path = path.substring(1);
			}
		}
		// trim trailing .xml
		if (path.endsWith(".xml")) {
			path = path.substring(0, path.length() - 4);
		}
		
		String username = null;
		int fetch = path.indexOf('@');
		if (fetch > -1) {
			username = path.substring(0, fetch);
			path = path.substring(fetch + 1);
		}
		UserModel user;
		if (StringUtils.isEmpty(username)) {
			user = GitBlit.self().authenticate(request);
		} else {
			user = GitBlit.self().getUserModel(username);
		}
		if (user == null) {
			user = UserModel.ANONYMOUS;
			username = "";
		}
		
		// ensure that the requested repository exists and is sparkleshared
		RepositoryModel model = GitBlit.self().getRepositoryModel(path);
		if (model == null) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			response.getWriter().append(MessageFormat.format("Repository \"{0}\" not found!", path));
			return;
		} else if (!model.isSparkleshared()) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.getWriter().append(MessageFormat.format("Repository \"{0}\" is not sparkleshared!", path));
			return;
		}
		
		if (GitBlit.getBoolean(Keys.git.enableGitServlet, true)
				|| GitBlit.getInteger(Keys.git.daemonPort, 0) > 0) {
			// Gitblit as server
			// determine username for repository url
			if (model.accessRestriction.exceeds(AccessRestrictionType.NONE)) {
				if (!user.canRewindRef(model)) {
					response.setStatus(HttpServletResponse.SC_FORBIDDEN);
					response.getWriter().append(MessageFormat.format("\"{0}\" does not have RW+ permissions for {1}!", user.username, path));
					return;
				}
			}
			
			if (model.accessRestriction.exceeds(AccessRestrictionType.NONE)) {
				username = user.username + "@";
			} else {
				username = "";
			}

			String serverPort = "";
			if (request.getScheme().equals("https")) {
				if (request.getServerPort() != 443) {
					serverPort = ":" + request.getServerPort();
				}
			} else if (request.getScheme().equals("http")) {
				if (request.getServerPort() != 80) {
					serverPort = ":" + request.getServerPort();
				}
			}

			// assume http/https serving
			String scheme = request.getScheme();
			String servletPath = Constants.GIT_PATH;

			// try to switch to git://, if git servlet disabled and repo has no restrictions
			if (!GitBlit.getBoolean(Keys.git.enableGitServlet, true)
					&& (GitBlit.getInteger(Keys.git.daemonPort, 0) > 0)
					&& AccessRestrictionType.NONE == model.accessRestriction) {
				scheme = "git";
				servletPath = "/";
				serverPort = GitBlit.getString(Keys.git.daemonPort, "");
			}

			// construct Sparkleshare invite
			StringBuilder sb = new StringBuilder();		
			sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			sb.append("<sparkleshare><invite>\n");
			sb.append(MessageFormat.format("<address>{0}://{1}{2}{3}{4}</address>\n", scheme, username, request.getServerName(), serverPort, request.getContextPath()));
			sb.append(MessageFormat.format("<remote_path>{0}{1}</remote_path>\n", servletPath, model.name));
			if (GitBlit.getInteger(Keys.fanout.port, 0) > 0) {
				// Gitblit is running it's own fanout service for pubsub notifications
				sb.append(MessageFormat.format("<announcements_url>tcp://{0}:{1}</announcements_url>\n", request.getServerName(), GitBlit.getString(Keys.fanout.port, "")));
			}
			sb.append("</invite></sparkleshare>\n");

			// write invite to client
			response.setContentType("application/xml");
			response.setContentLength(sb.length());
			response.getWriter().append(sb.toString());
		} else {
			// Gitblit as viewer, repository access handled externally so
			// assume RW+ permission
			List<String> others = GitBlit.getStrings(Keys.web.otherUrls);
			if (others.size() == 0) {
				return;
			}
			
			String address = MessageFormat.format(others.get(0), "", username);
			
			StringBuilder sb = new StringBuilder();		
			sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			sb.append("<sparkleshare><invite>\n");
			
			sb.append(MessageFormat.format("<address>{0}</address>\n", address));
			sb.append(MessageFormat.format("<remote_path>{0}</remote_path>\n", model.name));
			if (GitBlit.getInteger(Keys.fanout.port, 0) > 0) {
				// Gitblit is running it's own fanout service for pubsub notifications
				sb.append(MessageFormat.format("<announcements_url>tcp://{0}:{1}</announcements_url>\n", request.getServerName(), GitBlit.getString(Keys.fanout.port, "")));
			}
			sb.append("</invite></sparkleshare>\n");

			// write invite to client
			response.setContentType("application/xml");
			response.setContentLength(sb.length());
			response.getWriter().append(sb.toString());
		}
	}
}
