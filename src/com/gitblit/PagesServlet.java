/*
 * Copyright 2012 gitblit.com.
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

import java.io.IOException;
import java.text.MessageFormat;
import java.text.ParseException;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.RefModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.MarkdownUtils;
import com.gitblit.utils.StringUtils;

/**
 * Serves the content of a gh-pages branch.
 * 
 * @author James Moger
 * 
 */
public class PagesServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private transient Logger logger = LoggerFactory.getLogger(PagesServlet.class);

	public PagesServlet() {
		super();
	}

	/**
	 * Returns an url to this servlet for the specified parameters.
	 * 
	 * @param baseURL
	 * @param repository
	 * @param path
	 * @return an url
	 */
	public static String asLink(String baseURL, String repository, String path) {
		if (baseURL.length() > 0 && baseURL.charAt(baseURL.length() - 1) == '/') {
			baseURL = baseURL.substring(0, baseURL.length() - 1);
		}
		return baseURL + Constants.PAGES + repository + "/" + (path == null ? "" : ("/" + path));
	}

	/**
	 * Retrieves the specified resource from the gh-pages branch of the
	 * repository.
	 * 
	 * @param request
	 * @param response
	 * @throws javax.servlet.ServletException
	 * @throws java.io.IOException
	 */
	private void processRequest(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		String path = request.getPathInfo();
		if (path.toLowerCase().endsWith(".git")) {
			// forward to url with trailing /
			// this is important for relative pages links
			response.sendRedirect(request.getServletPath() + path + "/");
			return;
		}
		if (path.charAt(0) == '/') {
			// strip leading /
			path = path.substring(1);
		}

		// determine repository and resource from url
		String repository = "";
		String resource = "";
		Repository r = null;
		int offset = 0;
		while (r == null) {
			int slash = path.indexOf('/', offset);
			if (slash == -1) {
				repository = path;
			} else {
				repository = path.substring(0, slash);
			}
			r = GitBlit.self().getRepository(repository, false);
			offset = slash + 1;
			if (offset > 0) {
				resource = path.substring(offset);
			}
			if (repository.equals(path)) {
				// either only repository in url or no repository found
				break;
			}
		}

		ServletContext context = request.getSession().getServletContext();

		try {
			if (r == null) {
				// repository not found!
				String mkd = MessageFormat.format(
						"# Error\nSorry, no valid **repository** specified in this url: {0}!",
						repository);
				error(response, mkd);
				return;
			}

			// retrieve the content from the repository
			RefModel pages = JGitUtils.getPagesBranch(r);
			RevCommit commit = JGitUtils.getCommit(r, pages.getObjectId().getName());

			if (commit == null) {
				// branch not found!
				String mkd = MessageFormat.format(
						"# Error\nSorry, the repository {0} does not have a **gh-pages** branch!",
						repository);
				error(response, mkd);
				r.close();
				return;
			}
			response.setDateHeader("Last-Modified", JGitUtils.getCommitDate(commit).getTime());

			String [] encodings = GitBlit.getEncodings();

			RevTree tree = commit.getTree();
			byte[] content = null;
			if (StringUtils.isEmpty(resource)) {
				// find resource
				String[] files = { "index.html", "index.htm", "index.mkd" };
				for (String file : files) {
					content = JGitUtils.getStringContent(r, tree, file, encodings)
							.getBytes(Constants.ENCODING);
					if (content != null) {
						resource = file;
						// assume text/html unless the servlet container
						// overrides
						response.setContentType("text/html; charset=" + Constants.ENCODING);
						break;
					}
				}
			} else {
				// specific resource
				try {
					String contentType = context.getMimeType(resource);
					if (contentType == null) {
						contentType = "text/plain";
					}
					if (contentType.startsWith("text")) {
						content = JGitUtils.getStringContent(r, tree, resource, encodings).getBytes(
								Constants.ENCODING);
					} else {
						content = JGitUtils.getByteContent(r, tree, resource);
					}
					response.setContentType(contentType);
				} catch (Exception e) {
				}
			}

			// no content, try custom 404 page
			if (ArrayUtils.isEmpty(content)) {
				String custom404 = JGitUtils.getStringContent(r, tree, "404.html", encodings);
				if (!StringUtils.isEmpty(custom404)) {
					content = custom404.getBytes(Constants.ENCODING);
				}

				// still no content
				if (ArrayUtils.isEmpty(content)) {
					String str = MessageFormat.format(
							"# Error\nSorry, the requested resource **{0}** was not found.",
							resource);
					content = MarkdownUtils.transformMarkdown(str).getBytes(Constants.ENCODING);
				}

				try {
					// output the content
					logger.warn("Pages 404: " + resource);
					response.setStatus(HttpServletResponse.SC_NOT_FOUND);
					response.getOutputStream().write(content);
					response.flushBuffer();
				} catch (Throwable t) {
					logger.error("Failed to write page to client", t);
				}
				return;
			}

			// check to see if we should transform markdown files
			for (String ext : GitBlit.getStrings(Keys.web.markdownExtensions)) {
				if (resource.endsWith(ext)) {
					String mkd = new String(content, Constants.ENCODING);
					content = MarkdownUtils.transformMarkdown(mkd).getBytes(Constants.ENCODING);
					break;
				}
			}

			try {
				// output the content
				response.getOutputStream().write(content);
				response.flushBuffer();
			} catch (Throwable t) {
				logger.error("Failed to write page to client", t);
			}

			// close the repository
			r.close();
		} catch (Throwable t) {
			logger.error("Failed to write page to client", t);
		}
	}

	private void error(HttpServletResponse response, String mkd) throws ServletException,
			IOException, ParseException {
		String content = MarkdownUtils.transformMarkdown(mkd);
		response.setContentType("text/html; charset=" + Constants.ENCODING);
		response.getWriter().write(content);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		processRequest(request, response);
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		processRequest(request, response);
	}
}
