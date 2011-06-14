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

import java.util.Date;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;

public class DownloadZipServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private transient Logger logger = LoggerFactory.getLogger(DownloadZipServlet.class);

	public DownloadZipServlet() {
		super();
	}

	public static String asLink(String baseURL, String repository, String objectId, String path) {
		if (baseURL.length() > 0 && baseURL.charAt(baseURL.length() - 1) == '/') {
			baseURL = baseURL.substring(0, baseURL.length() - 1);
		}
		return baseURL + Constants.ZIP_SERVLET_PATH + "?r=" + repository
				+ (path == null ? "" : ("&p=" + path))
				+ (objectId == null ? "" : ("&h=" + objectId));
	}

	private void processRequest(javax.servlet.http.HttpServletRequest request,
			javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException,
			java.io.IOException {
		if (!GitBlit.getBoolean(Keys.web.allowZipDownloads, true)) {
			logger.warn("Zip downloads are disabled");
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;

		}
		String repository = request.getParameter("r");
		String basePath = request.getParameter("p");
		String objectId = request.getParameter("h");

		try {
			String name = repository;
			if (name.indexOf('/') > -1) {
				name = name.substring(name.lastIndexOf('/') + 1);
			}

			// check roles first
			boolean authorized = request.isUserInRole(Constants.ADMIN_ROLE);
			authorized |= request.isUserInRole(repository);

			if (!authorized) {
				RepositoryModel model = GitBlit.self().getRepositoryModel(repository);
				if (model.accessRestriction.atLeast(AccessRestrictionType.VIEW)) {
					logger.warn("Unauthorized access via zip servlet for " + model.name);
					response.sendError(HttpServletResponse.SC_FORBIDDEN);
					return;
				}
			}
			if (!StringUtils.isEmpty(basePath)) {
				name += "-" + basePath.replace('/', '_');
			}
			if (!StringUtils.isEmpty(objectId)) {
				name += "-" + objectId;
			}

			Repository r = GitBlit.self().getRepository(repository);
			RevCommit commit = JGitUtils.getCommit(r, objectId);
			Date date = JGitUtils.getCommitDate(commit);
			String contentType = "application/octet-stream";
			response.setContentType(contentType + "; charset=" + response.getCharacterEncoding());
			// response.setContentLength(attachment.getFileSize());
			response.setHeader("Content-Disposition", "attachment; filename=\"" + name + ".zip"
					+ "\"");
			response.setDateHeader("Last-Modified", date.getTime());
			response.setHeader("Cache-Control", "no-cache");
			response.setHeader("Pragma", "no-cache");
			response.setDateHeader("Expires", 0);

			try {
				JGitUtils.zip(r, basePath, objectId, response.getOutputStream());
				response.flushBuffer();
			} catch (Throwable t) {
				logger.error("Failed to write attachment to client", t);
			}
		} catch (Throwable t) {
			logger.error("Failed to write attachment to client", t);
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
