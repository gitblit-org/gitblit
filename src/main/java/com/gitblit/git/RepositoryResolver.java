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
package com.gitblit.git;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.resolver.FileResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.GitBlit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;

/**
 * Resolves repositories and grants export access.
 * 
 * @author James Moger
 *
 */
public class RepositoryResolver<X> extends FileResolver<X> {

	private final Logger logger = LoggerFactory.getLogger(RepositoryResolver.class);
	
	public RepositoryResolver(File repositoriesFolder) {
		super(repositoriesFolder, true);
	}

	/**
	 * Open the repository and inject the repository name into the settings.
	 */
	@Override
	public Repository open(final X req, final String name)
			throws RepositoryNotFoundException, ServiceNotEnabledException {
		Repository repo = super.open(req, name);
		
		// Set repository name for the pack factories
		// We do this because the JGit API does not have a consistent way to
		// retrieve the repository name from the pack factories or the hooks.
		if (req instanceof HttpServletRequest) {
			// http/https request
			HttpServletRequest client = (HttpServletRequest) req;
			client.setAttribute("gitblitRepositoryName", name);
		} else if (req instanceof GitDaemonClient) {
			// git request
			GitDaemonClient client = (GitDaemonClient) req;
			client.setRepositoryName(name);
		}
		return repo;
	}
	
	/**
	 * Check if this repository can be served by the requested client connection.
	 */
	@Override
	protected boolean isExportOk(X req, String repositoryName, Repository db) throws IOException {
		RepositoryModel model = GitBlit.self().getRepositoryModel(repositoryName);

		String scheme = null;
		UserModel user = null;
		String origin = null;
		
		if (req instanceof GitDaemonClient) {
			// git daemon request
			// this is an anonymous/unauthenticated protocol
			GitDaemonClient client = (GitDaemonClient) req;
			scheme = "git";
			origin = client.getRemoteAddress().toString();
			user = UserModel.ANONYMOUS;
		} else if (req instanceof HttpServletRequest) {
			// http/https request
			HttpServletRequest httpRequest = (HttpServletRequest) req;
			scheme = httpRequest.getScheme(); 
			origin = httpRequest.getRemoteAddr();
			user = GitBlit.self().authenticate(httpRequest);
			if (user == null) {
				user = UserModel.ANONYMOUS;
			}
		}

		if (user.canClone(model)) {
			// user can access this git repo
			logger.debug(MessageFormat.format("{0}:// access of {1} by {2} from {3} PERMITTED",
					scheme, repositoryName, user.username, origin));
			return true;
		}
		
		// user can not access this git repo
		logger.warn(MessageFormat.format("{0}:// access of {1} by {2} from {3} DENIED",
				scheme, repositoryName, user.username, origin));
		return false;
	}
}
