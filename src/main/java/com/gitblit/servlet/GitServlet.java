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
package com.gitblit.servlet;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.IOException;
import java.util.Enumeration;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.http.server.GitFilter;

import com.gitblit.dagger.DaggerContext;
import com.gitblit.git.GitblitReceivePackFactory;
import com.gitblit.git.GitblitUploadPackFactory;
import com.gitblit.git.RepositoryResolver;
import com.gitblit.manager.IGitblit;

import dagger.ObjectGraph;

/**
 * The GitServlet provides http/https access to Git repositories.
 * Access to this servlet is protected by the GitFilter.
 *
 * @author James Moger
 *
 */
public class GitServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private final GitFilter gitFilter;

	public GitServlet() {
		gitFilter = new GitFilter();
	}

	@Override
	public void init(final ServletConfig config) throws ServletException {
		ServletContext context = config.getServletContext();
		ObjectGraph dagger = (ObjectGraph) context.getAttribute(DaggerContext.INJECTOR_NAME);
		IGitblit gitblit = dagger.get(IGitblit.class);
		gitFilter.setRepositoryResolver(new RepositoryResolver<HttpServletRequest>(gitblit));
		gitFilter.setUploadPackFactory(new GitblitUploadPackFactory<HttpServletRequest>(gitblit));
		gitFilter.setReceivePackFactory(new GitblitReceivePackFactory<HttpServletRequest>(gitblit));

		gitFilter.init(new FilterConfig() {
			@Override
			public String getFilterName() {
				return gitFilter.getClass().getName();
			}

			@Override
			public String getInitParameter(String name) {
				return config.getInitParameter(name);
			}

			@Override
			public Enumeration<String> getInitParameterNames() {
				return config.getInitParameterNames();
			}

			@Override
			public ServletContext getServletContext() {
				return config.getServletContext();
			}
		});

		init();
	}

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse res)
			throws ServletException, IOException {
		gitFilter.doFilter(req, res, new FilterChain() {
			@Override
			public void doFilter(ServletRequest request,
					ServletResponse response) throws IOException,
					ServletException {
				((HttpServletResponse) response).sendError(SC_NOT_FOUND);
			}
		});
	}

	@Override
	public void destroy() {
		gitFilter.destroy();
	}
}
