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
package com.gitblit.servlet;

import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.dagger.DaggerServlet;
import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;

import dagger.ObjectGraph;

/**
 * Handles requests for Sparkleshare Invites
 *
 * @author James Moger
 *
 */
public class SparkleShareInviteServlet extends DaggerServlet {

	private static final long serialVersionUID = 1L;

	private IStoredSettings settings;

	private IUserManager userManager;

	private IAuthenticationManager authenticationManager;

	private IRepositoryManager repositoryManager;

	@Override
	protected void inject(ObjectGraph dagger) {
		this.settings = dagger.get(IStoredSettings.class);
		this.userManager = dagger.get(IUserManager.class);
		this.authenticationManager = dagger.get(IAuthenticationManager.class);
		this.repositoryManager = dagger.get(IRepositoryManager.class);
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

		int sshPort = settings.getInteger(Keys.git.sshPort, 0);
		if (sshPort == 0) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.getWriter().append("SSH is not active on this server!");
			return;
		}
		int sshDisplayPort = settings.getInteger(Keys.git.sshDisplayPort, sshPort);

		// extract repo name from request
		String repoUrl = request.getPathInfo().substring(1);

		// trim trailing .xml
		if (repoUrl.endsWith(".xml")) {
			repoUrl = repoUrl.substring(0, repoUrl.length() - 4);
		}

		String username = null;
		String path;
		int fetchIndex = repoUrl.indexOf('@');
		if (fetchIndex > -1) {
			username = repoUrl.substring(0, fetchIndex);
			path = repoUrl.substring(fetchIndex + 1);
		} else {
			path = repoUrl;
		}

		String host = request.getServerName();
		String url = settings.getString(Keys.web.canonicalUrl, "https://localhost:8443");
		if (!StringUtils.isEmpty(url) && url.indexOf("localhost") == -1) {
			host = new URL(url).getHost();
		}
		String sshDisplayHost = settings.getString(Keys.git.sshDisplayHost, "");
		if(sshDisplayHost.isEmpty()) {
			sshDisplayHost = host;
		}

		UserModel user;
		if (StringUtils.isEmpty(username)) {
			user = authenticationManager.authenticate(request);
		} else {
			user = userManager.getUserModel(username);
		}
		if (user == null || user.disabled) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.getWriter().append("Access is not permitted!");
			return;
		}

		// ensure that the requested repository exists
		RepositoryModel model = repositoryManager.getRepositoryModel(path);
		if (model == null) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			response.getWriter().append(MessageFormat.format("Repository \"{0}\" not found!", path));
			return;
		}

		if (!user.canRewindRef(model)) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.getWriter().append(MessageFormat.format("{0} does not have RW+ permissions to \"{1}\"!", user.username, model.name));
		}

		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<sparkleshare><invite>\n");
		sb.append(MessageFormat.format("<address>ssh://{0}@{1}:{2,number,0}/</address>\n", user.username, sshDisplayHost, sshDisplayPort));
		sb.append(MessageFormat.format("<remote_path>/{0}</remote_path>\n", model.name));
		int fanoutPort = settings.getInteger(Keys.fanout.port, 0);
		if (fanoutPort > 0) {
			// Gitblit is running it's own fanout service for pubsub notifications
			sb.append(MessageFormat.format("<announcements_url>tcp://{0}:{1,number,0}</announcements_url>\n", request.getServerName(), fanoutPort));
		}
		sb.append("</invite></sparkleshare>\n");

		// write invite to client
		response.setContentType("application/xml");
		response.setContentLength(sb.length());
		response.getWriter().append(sb.toString());
	}
}
