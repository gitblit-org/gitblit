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
package com.gitblit.git;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IGitblit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.HttpUtils;
import com.gitblit.utils.StringUtils;

/**
 * The receive pack factory creates the receive pack which processes pushes.
 *
 * @author James Moger
 *
 * @param <X> the connection type
 */
public class GitblitReceivePackFactory<X> implements ReceivePackFactory<X> {

	protected final Logger logger = LoggerFactory.getLogger(GitblitReceivePackFactory.class);

	private final IStoredSettings settings;

	private final IGitblit gitblit;

	public GitblitReceivePackFactory(IGitblit gitblit) {
		super();
		this.settings = gitblit.getSettings();
		this.gitblit = gitblit;
	}

	@Override
	public ReceivePack create(X req, Repository db)
			throws ServiceNotEnabledException, ServiceNotAuthorizedException {

		UserModel user = UserModel.ANONYMOUS;
		String repositoryName = "";
		String origin = "";
		String gitblitUrl = "";
		String repositoryUrl = "";
		int timeout = 0;

		if (req instanceof HttpServletRequest) {
			// http/https request may or may not be authenticated
			HttpServletRequest request = (HttpServletRequest) req;
			repositoryName = request.getAttribute("gitblitRepositoryName").toString();
			origin = request.getRemoteHost();
			gitblitUrl = HttpUtils.getGitblitURL(request);
			repositoryUrl = request.getRequestURI();

			// determine pushing user
			String username = request.getRemoteUser();
			if (!StringUtils.isEmpty(username)) {
				UserModel u = gitblit.getUserModel(username);
				if (u != null) {
					user = u;
				}
			}
		} else if (req instanceof GitDaemonClient) {
			// git daemon request is always anonymous
			GitDaemonClient client = (GitDaemonClient) req;
			repositoryName = client.getRepositoryName();
			origin = client.getRemoteAddress().getHostAddress();

			// set timeout from Git daemon
			timeout = client.getDaemon().getTimeout();
		}

		boolean allowAnonymousPushes = settings.getBoolean(Keys.git.allowAnonymousPushes, false);
		if (!allowAnonymousPushes && UserModel.ANONYMOUS.equals(user)) {
			// prohibit anonymous pushes
			throw new ServiceNotEnabledException();
		}

		final RepositoryModel repository = gitblit.getRepositoryModel(repositoryName);

		final GitblitReceivePack rp = new GitblitReceivePack(gitblit, db, repository, user);
		rp.setGitblitUrl(gitblitUrl);
		rp.setRepositoryUrl(repositoryUrl);
		rp.setRefLogIdent(new PersonIdent(user.username, user.username + "@" + origin));
		rp.setTimeout(timeout);

		return rp;
	}
}