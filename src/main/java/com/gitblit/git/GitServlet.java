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

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.ISessionManager;
import com.gitblit.manager.IUserManager;

/**
 * The GitServlet provides http/https access to Git repositories.
 * Access to this servlet is protected by the GitFilter.
 *
 * @author James Moger
 *
 */
@Singleton
public class GitServlet extends org.eclipse.jgit.http.server.GitServlet {

	private static final long serialVersionUID = 1L;

	private final IRuntimeManager runtimeManager;

	private final IUserManager userManager;

	private final ISessionManager sessionManager;

	private final IRepositoryManager repositoryManager;

	@Inject
	public GitServlet(
			IRuntimeManager runtimeManager,
			IUserManager userManager,
			ISessionManager sessionManager,
			IRepositoryManager repositoryManager) {
		super();
		this.runtimeManager = runtimeManager;
		this.userManager = userManager;
		this.sessionManager = sessionManager;
		this.repositoryManager = repositoryManager;
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		setRepositoryResolver(new RepositoryResolver<HttpServletRequest>(sessionManager, repositoryManager));
		setUploadPackFactory(new GitblitUploadPackFactory<HttpServletRequest>(sessionManager));
		setReceivePackFactory(new GitblitReceivePackFactory<HttpServletRequest>(runtimeManager, userManager, repositoryManager));
		super.init(config);
	}
}
