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
package com.gitblit;

import java.util.List;

import javax.servlet.http.HttpServlet;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.SyndicationUtils;

public class SyndicationServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private transient Logger logger = LoggerFactory.getLogger(SyndicationServlet.class);

	public static String asLink(String baseURL, String repository, String objectId, int length) {
		if (baseURL.charAt(baseURL.length() - 1) == '/') {
			baseURL = baseURL.substring(0, baseURL.length() - 1);
		}
		return baseURL + Constants.SYNDICATION_SERVLET_PATH + "?r=" + repository
				+ (objectId == null ? "" : ("&h=" + objectId)) + (length > 0 ? "&l=" + length : "");
	}

	private void processRequest(javax.servlet.http.HttpServletRequest request,
			javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException,
			java.io.IOException {
		String hostUrl = request.getRequestURL().toString();
		String servlet = request.getServletPath();
		hostUrl = hostUrl.substring(0, hostUrl.indexOf(servlet));
		String repositoryName = request.getParameter("r");
		String objectId = request.getParameter("h");
		String l = request.getParameter("l");
		int length = GitBlit.getInteger(Keys.web.syndicationEntries, 25);
		if (StringUtils.isEmpty(objectId)) {
			objectId = org.eclipse.jgit.lib.Constants.HEAD;
		}
		if (!StringUtils.isEmpty(l)) {
			try {
				length = Integer.parseInt(l);
			} catch (NumberFormatException x) {
			}
		}
		
		// TODO confirm repository is accessible!!

		Repository repository = GitBlit.self().getRepository(repositoryName);
		RepositoryModel model = GitBlit.self().getRepositoryModel(repositoryName);
		List<RevCommit> commits = JGitUtils.getRevLog(repository, objectId, 0, length);
		try {
			SyndicationUtils.toRSS(hostUrl, model.name + " " + objectId, model.description, model.name, commits, response.getOutputStream());
		} catch (Exception e) {
			logger.error("An error occurred during feed generation", e);
		}
	}

	@Override
	protected void doPost(javax.servlet.http.HttpServletRequest request,
			javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException,
			java.io.IOException {
		processRequest(request, response);
	}

	@Override
	protected void doGet(javax.servlet.http.HttpServletRequest request,
			javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException,
			java.io.IOException {
		processRequest(request, response);
	}
}
