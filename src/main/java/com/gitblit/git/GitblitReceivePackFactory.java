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

import com.gitblit.GitBlit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.HttpUtils;

/**
 * The receive pack factory creates a receive pack which accepts pushes from
 * clients.
 * 
 * @author James Moger
 *
 * @param <X> the connection type
 */
public class GitblitReceivePackFactory<X> implements ReceivePackFactory<X> {

	protected final Logger logger = LoggerFactory.getLogger(GitblitReceivePackFactory.class);
	
	@Override
	public ReceivePack create(X req, Repository db)
			throws ServiceNotEnabledException, ServiceNotAuthorizedException {

		final ReceivePack rp = new ReceivePack(db);
		UserModel user = UserModel.ANONYMOUS;
		String repositoryName = "";
		String origin = "";
		String gitblitUrl = "";
		int timeout = 0;
		
		if (req instanceof HttpServletRequest) {
			// http/https request may or may not be authenticated 
			HttpServletRequest request = (HttpServletRequest) req;
			repositoryName = request.getAttribute("gitblitRepositoryName").toString();
			origin = request.getRemoteHost();
			gitblitUrl = HttpUtils.getGitblitURL(request);

			// determine pushing user
			String username = request.getRemoteUser();
			if (username != null && !"".equals(username)) {
				user = GitBlit.self().getUserModel(username);
				if (user == null) {
					// anonymous push, create a temporary usermodel
					user = new UserModel(username);
				}
			}
		} else if (req instanceof GitDaemonClient) {
			// git daemon request is alway anonymous
			GitDaemonClient client = (GitDaemonClient) req;
			repositoryName = client.getRepositoryName();
			origin = client.getRemoteAddress().getHostAddress();
			// set timeout from Git daemon
			timeout = client.getDaemon().getTimeout();
		}

		// set pushing user identity for reflog
		rp.setRefLogIdent(new PersonIdent(user.username, user.username + "@" + origin));
		rp.setTimeout(timeout);
		
		// set advanced ref permissions
		RepositoryModel repository = GitBlit.self().getRepositoryModel(repositoryName);
		rp.setAllowCreates(user.canCreateRef(repository));
		rp.setAllowDeletes(user.canDeleteRef(repository));
		rp.setAllowNonFastForwards(user.canRewindRef(repository));

		// setup the receive hook
		ReceiveHook hook = new ReceiveHook();
		hook.user = user;
		hook.repository = repository;
		hook.gitblitUrl = gitblitUrl;

		rp.setPreReceiveHook(hook);
		rp.setPostReceiveHook(hook);

		return rp;
	}
}
