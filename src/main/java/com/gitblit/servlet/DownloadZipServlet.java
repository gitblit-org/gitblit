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

import java.io.IOException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.Date;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.utils.CompressionUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.MarkdownUtils;
import com.gitblit.utils.StringUtils;

/**
 * Streams out a zip file from the specified repository for any tree path at any
 * revision.
 *
 * @author James Moger
 *
 */
@Singleton
public class DownloadZipServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private transient Logger logger = LoggerFactory.getLogger(DownloadZipServlet.class);

	private static IStoredSettings settings;

	private IRepositoryManager repositoryManager;

	public static enum Format {
		zip(".zip"), tar(".tar"), gz(".tar.gz"), xz(".tar.xz"), bzip2(".tar.bzip2");

		public final String extension;

		Format(String ext) {
			this.extension = ext;
		}

		public static Format fromName(String name) {
			for (Format format : values()) {
				if (format.name().equalsIgnoreCase(name)) {
					return format;
				}
			}
			return zip;
		}
	}

	@Inject
	public DownloadZipServlet(IStoredSettings settings, IRepositoryManager repositoryManager) {
		DownloadZipServlet.settings = settings;
		this.repositoryManager = repositoryManager;
	}

	/**
	 * Returns an url to this servlet for the specified parameters.
	 *
	 * @param baseURL
	 * @param repository
	 * @param objectId
	 * @param path
	 * @param format
	 * @return an url
	 */
	public static String asLink(String baseURL, String repository, String objectId, String path, Format format) {
		if (baseURL.length() > 0 && baseURL.charAt(baseURL.length() - 1) == '/') {
			baseURL = baseURL.substring(0, baseURL.length() - 1);
		}
		StringBuilder url = new StringBuilder(baseURL + Constants.ZIP_PATH);
		boolean firstParam = true;

		// Mimic the wicket page mount parameters, key off of same config value
		if (settings.getBoolean(Keys.web.mountParameters, true)) {
			// Replace slashes with replacement character if configured
			char c = settings.getChar(Keys.web.forwardSlashCharacter, '/');
			if (c != '/') {
				repository = repository.replace('/', c);
			}
			url.append(repository);
		} else {
			if (!StringUtils.isEmpty(repository)) {
				url.append(firstParam ? "?" : "&");
				url.append("r=" + repository);
				firstParam = false;
			}
		}

		// TODO: support these as URL path parameters if mount option is enabled
		if (!StringUtils.isEmpty(path)) {
			url.append(firstParam ? "?" : "&");
			url.append("p=" + path);
			firstParam = false;
		}
		if (!StringUtils.isEmpty(objectId)) {
			url.append(firstParam ? "?" : "&");
			url.append("h=" + objectId);
			firstParam = false;
		}
		if (format != null) {
			url.append(firstParam ? "?" : "&");
			url.append("format=" + format.name());
			firstParam = false;
		}
		return url.toString();
	}

	/**
	 * Creates a zip stream from the repository of the requested data.
	 *
	 * @param request
	 * @param response
	 * @throws javax.servlet.ServletException
	 * @throws java.io.IOException
	 */
	private void processRequest(javax.servlet.http.HttpServletRequest request,
			javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException,
			java.io.IOException {
		if (!settings.getBoolean(Keys.web.allowZipDownloads, true)) {
			logger.warn("Zip downloads are disabled");
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		Format format = Format.zip;
		String repository = null;
		// Mimic the wicket page mount parameters, key off of same config value
		if (settings.getBoolean(Keys.web.mountParameters, true)) {
			repository = request.getPathInfo();
			if (!StringUtils.isEmpty(repository)) {
				// Remove leading slash from path info
				repository = repository.substring(1);
				char c = settings.getChar(Keys.web.forwardSlashCharacter, '/');
				repository = repository.replace('!', '/').replace(c, '/');
			}
		} else {
			repository = request.getParameter("r");
		}

		if (StringUtils.isEmpty(repository)) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.getWriter().append("Bad request");
			return;
		}

		// TODO: convert these to support URL path parameters for mount option
		String basePath = request.getParameter("p");
		String objectId = request.getParameter("h");
		String f = request.getParameter("format");
		if (!StringUtils.isEmpty(f)) {
			format = Format.fromName(f);
		}

		try {
			String name = repository;
			if (name.indexOf('/') > -1) {
				name = name.substring(name.lastIndexOf('/') + 1);
			}
			name = StringUtils.stripDotGit(name);

			if (!StringUtils.isEmpty(basePath)) {
				name += "-" + basePath.replace('/', '_');
			}
			if (!StringUtils.isEmpty(objectId)) {
				name += "-" + objectId;
			}

			Repository r = repositoryManager.getRepository(repository);
			if (r == null) {
				if (repositoryManager.isCollectingGarbage(repository)) {
					error(response, MessageFormat.format("# Error\nGitblit is busy collecting garbage in {0}", repository));
					return;
				} else {
					error(response, MessageFormat.format("# Error\nFailed to find repository {0}", repository));
					return;
				}
			}
			RevCommit commit = JGitUtils.getCommit(r, objectId);
			if (commit == null) {
				error(response, MessageFormat.format("# Error\nFailed to find commit {0}", objectId));
				r.close();
				return;
			}
			Date date = JGitUtils.getCommitDate(commit);

			String contentType = "application/octet-stream";
			response.setContentType(contentType + "; charset=" + response.getCharacterEncoding());
			response.setHeader("Content-Disposition", "attachment; filename=\"" + name + format.extension + "\"");
			response.setDateHeader("Last-Modified", date.getTime());
			response.setHeader("Cache-Control", "no-cache");
			response.setHeader("Pragma", "no-cache");
			response.setDateHeader("Expires", 0);

			try {
				switch (format) {
				case zip:
					CompressionUtils.zip(r, basePath, objectId, response.getOutputStream());
					break;
				case tar:
					CompressionUtils.tar(r, basePath, objectId, response.getOutputStream());
					break;
				case gz:
					CompressionUtils.gz(r, basePath, objectId, response.getOutputStream());
					break;
				case xz:
					CompressionUtils.xz(r, basePath, objectId, response.getOutputStream());
					break;
				case bzip2:
					CompressionUtils.bzip2(r, basePath, objectId, response.getOutputStream());
					break;
				}

				response.flushBuffer();
			} catch (IOException t) {
				String message = t.getMessage() == null ? "" : t.getMessage().toLowerCase();
				if (message.contains("reset") || message.contains("broken pipe")) {
					logger.error("Client aborted zip download: " + message);
				} else {
					logger.error("Failed to write attachment to client", t);
				}
			} catch (Throwable t) {
				logger.error("Failed to write attachment to client", t);
			}

			// close the repository
			r.close();
		} catch (Throwable t) {
			logger.error("Failed to write attachment to client", t);
		}
	}

	private void error(HttpServletResponse response, String mkd) throws ServletException,
			IOException, ParseException {
		String content = MarkdownUtils.transformMarkdown(mkd);
		response.setContentType("text/html; charset=" + Constants.ENCODING);
		response.getWriter().write(content);
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
