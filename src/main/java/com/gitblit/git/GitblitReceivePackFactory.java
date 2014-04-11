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

import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.Transport;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IGitblit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.transport.git.GitDaemonClient;
import com.gitblit.transport.ssh.SshDaemonClient;
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
		int timeout = 0;
		Transport transport = null;

		if (req instanceof HttpServletRequest) {
			// http/https request may or may not be authenticated
			HttpServletRequest client = (HttpServletRequest) req;
			repositoryName = client.getAttribute("gitblitRepositoryName").toString();
			origin = client.getRemoteHost();
			gitblitUrl = HttpUtils.getGitblitURL(client);

			// determine pushing user
			String username = client.getRemoteUser();
			if (!StringUtils.isEmpty(username)) {
				UserModel u = gitblit.getUserModel(username);
				if (u != null) {
					user = u;
				}
			}

			// determine the transport
			if ("http".equals(client.getScheme())) {
				transport = Transport.HTTP;
			} else if ("https".equals(client.getScheme())) {
				transport = Transport.HTTPS;
			}
		} else if (req instanceof GitDaemonClient) {
			// git daemon request is always anonymous
			GitDaemonClient client = (GitDaemonClient) req;
			repositoryName = client.getRepositoryName();
			origin = client.getRemoteAddress().getHostAddress();

			// set timeout from Git daemon
			timeout = client.getDaemon().getTimeout();

			transport = Transport.GIT;
		} else if (req instanceof SshDaemonClient) {
			// SSH request is always authenticated
			SshDaemonClient client = (SshDaemonClient) req;
			repositoryName = client.getRepositoryName();
			origin = client.getRemoteAddress().toString();
			user = client.getUser();

			transport = Transport.SSH;
		}

		if (!acceptPush(transport)) {
			throw new ServiceNotAuthorizedException();
		}

		boolean allowAnonymousPushes = settings.getBoolean(Keys.git.allowAnonymousPushes, false);
		if (!allowAnonymousPushes && UserModel.ANONYMOUS.equals(user)) {
			// prohibit anonymous pushes
			throw new ServiceNotEnabledException();
		}

		String url = settings.getString(Keys.web.canonicalUrl, null);
		if (StringUtils.isEmpty(url)) {
			url = gitblitUrl;
		}

		final RepositoryModel repository = gitblit.getRepositoryModel(repositoryName);

		// Determine which receive pack to use for pushes
		final GitblitReceivePack rp;
		if (gitblit.getTicketService().isAcceptingNewPatchsets(repository)) {
			rp = new PatchsetReceivePack(gitblit, db, repository, user);
		} else {
			rp = new GitblitReceivePack(gitblit, db, repository, user);
		}

		rp.setGitblitUrl(url);
		rp.setRefLogIdent(new PersonIdent(user.username, user.username + "@" + origin));
		rp.setTimeout(timeout);

		return rp;
	}

	protected boolean acceptPush(Transport byTransport) {
		if (byTransport == null) {
			logger.info("Unknown transport, push rejected!");
			return false;
		}

		Set<Transport> transports = new HashSet<Transport>();
		for (String value : gitblit.getSettings().getStrings(Keys.git.acceptedPushTransports)) {
			Transport transport = Transport.fromString(value);
			if (transport == null) {
				logger.info(String.format("Ignoring unknown registered transport %s", value));
				continue;
			}

			transports.add(transport);
		}

		if (transports.isEmpty()) {
			// no transports are explicitly specified, all are acceptable
			return true;
		}

		// verify that the transport is permitted
		return transports.contains(byTransport);
	}
}