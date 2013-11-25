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
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import com.gitblit.manager.IGitblit;

/**
 * The GitServlet provides http/https access to Git repositories.
 * Access to this servlet is protected by the GitFilter.
 *
 * @author James Moger
 *
 */
public class GitServlet extends org.eclipse.jgit.http.server.GitServlet {

	private static final long serialVersionUID = 1L;

	private final IGitblit gitblit;

	@Inject
	public GitServlet(IGitblit gitblit) {
		super();
		this.gitblit = gitblit;
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		setRepositoryResolver(new RepositoryResolver<HttpServletRequest>(gitblit));
		setUploadPackFactory(new GitblitUploadPackFactory<HttpServletRequest>(gitblit));
		setReceivePackFactory(new GitblitReceivePackFactory<HttpServletRequest>(gitblit));
		super.init(config);
	}
}
