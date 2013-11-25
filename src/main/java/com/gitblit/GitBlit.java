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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.manager.GitblitManager;
import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.manager.IFederationManager;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IProjectManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.manager.ServicesManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.RepositoryUrl;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;

/**
 * GitBlit is the aggregate manager for the Gitblit webapp.  It provides all
 * management functions and also manages some long-running services.
 *
 * @author James Moger
 *
 */
public class GitBlit extends GitblitManager {

	private final ServicesManager servicesManager;

	public GitBlit(
			IRuntimeManager runtimeManager,
			INotificationManager notificationManager,
			IUserManager userManager,
			IAuthenticationManager authenticationManager,
			IRepositoryManager repositoryManager,
			IProjectManager projectManager,
			IFederationManager federationManager) {

		super(runtimeManager,
				notificationManager,
				userManager,
				authenticationManager,
				repositoryManager,
				projectManager,
				federationManager);

		this.servicesManager = new ServicesManager(this);
	}

	@Override
	public GitBlit start() {
		super.start();
		logger.info("Starting services manager...");
		servicesManager.start();
		return this;
	}

	@Override
	public GitBlit stop() {
		super.stop();
		servicesManager.stop();
		return this;
	}

	/**
	 * Returns a list of repository URLs and the user access permission.
	 *
	 * @param request
	 * @param user
	 * @param repository
	 * @return a list of repository urls
	 */
	@Override
	public List<RepositoryUrl> getRepositoryUrls(HttpServletRequest request, UserModel user, RepositoryModel repository) {
		if (user == null) {
			user = UserModel.ANONYMOUS;
		}
		String username = StringUtils.encodeUsername(UserModel.ANONYMOUS.equals(user) ? "" : user.username);

		List<RepositoryUrl> list = new ArrayList<RepositoryUrl>();
		// http/https url
		if (settings.getBoolean(Keys.git.enableGitServlet, true)) {
			AccessPermission permission = user.getRepositoryPermission(repository).permission;
			if (permission.exceeds(AccessPermission.NONE)) {
				list.add(new RepositoryUrl(getRepositoryUrl(request, username, repository), permission));
			}
		}

		// git daemon url
		String gitDaemonUrl = servicesManager.getGitDaemonUrl(request, user, repository);
		if (!StringUtils.isEmpty(gitDaemonUrl)) {
			AccessPermission permission = servicesManager.getGitDaemonAccessPermission(user, repository);
			if (permission.exceeds(AccessPermission.NONE)) {
				list.add(new RepositoryUrl(gitDaemonUrl, permission));
			}
		}

		// add all other urls
		// {0} = repository
		// {1} = username
		for (String url : settings.getStrings(Keys.web.otherUrls)) {
			if (url.contains("{1}")) {
				// external url requires username, only add url IF we have one
				if (!StringUtils.isEmpty(username)) {
					list.add(new RepositoryUrl(MessageFormat.format(url, repository.name, username), null));
				}
			} else {
				// external url does not require username
				list.add(new RepositoryUrl(MessageFormat.format(url, repository.name), null));
			}
		}
		return list;
	}
}
