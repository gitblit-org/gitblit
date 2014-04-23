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
package com.gitblit.servlet;

import java.io.IOException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.dagger.DaggerServlet;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.models.PathModel;
import com.gitblit.models.RefModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.ByteFormat;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.MarkdownUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.MarkupProcessor;
import com.gitblit.wicket.MarkupProcessor.MarkupDocument;

import dagger.ObjectGraph;

/**
 * Serves the content of a gh-pages branch.
 *
 * @author James Moger
 *
 */
public class PagesServlet extends DaggerServlet {

	private static final long serialVersionUID = 1L;

	private transient Logger logger = LoggerFactory.getLogger(PagesServlet.class);

	private IStoredSettings settings;

	private IRepositoryManager repositoryManager;

	@Override
	protected void inject(ObjectGraph dagger) {
		this.settings = dagger.get(IStoredSettings.class);
		this.repositoryManager = dagger.get(IRepositoryManager.class);
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
			r = repositoryManager.getRepository(repository, false);
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
				return;
			}

			MarkupProcessor processor = new MarkupProcessor(settings);
			String [] encodings = settings.getStrings(Keys.web.blobEncodings).toArray(new String[0]);

			RevTree tree = commit.getTree();

			String res = resource;
			if (res.endsWith("/")) {
				res = res.substring(0, res.length() - 1);
			}

			List<PathModel> pathEntries = JGitUtils.getFilesInPath(r, res, commit);

			byte[] content = null;
			if (pathEntries.isEmpty()) {
				// not a path, a specific resource
				try {
					String contentType = context.getMimeType(res);
					if (contentType == null) {
						contentType = "text/plain";
					}
					if (contentType.startsWith("text")) {
						content = JGitUtils.getStringContent(r, tree, res, encodings).getBytes(
								Constants.ENCODING);
					} else {
						content = JGitUtils.getByteContent(r, tree, res, false);
					}
					response.setContentType(contentType);
				} catch (Exception e) {
				}
			} else {
				// path request
				if (!request.getPathInfo().endsWith("/")) {
					// redirect to trailing '/' url
					response.sendRedirect(request.getServletPath() + request.getPathInfo() + "/");
					return;
				}

				Map<String, String> names = new TreeMap<String, String>();
				for (PathModel entry : pathEntries) {
					names.put(entry.name.toLowerCase(), entry.name);
				}

				List<String> extensions = new ArrayList<String>();
				extensions.add("html");
				extensions.add("htm");
				extensions.addAll(processor.getMarkupExtensions());
				for (String ext : extensions) {
					String key = "index." + ext;

					if (names.containsKey(key)) {
						String fileName = names.get(key);
						String fullPath = fileName;
						if (!res.isEmpty()) {
							fullPath = res + "/" + fileName;
						}
						String stringContent = JGitUtils.getStringContent(r, tree, fullPath, encodings);
						if (stringContent == null) {
							continue;
						}
						content = stringContent.getBytes(Constants.ENCODING);
						if (content != null) {
							res = fullPath;
							// assume text/html unless the servlet container
							// overrides
							response.setContentType("text/html; charset=" + Constants.ENCODING);
							break;
						}
					}
				}
			}

			// no content, document list or custom 404 page
			if (ArrayUtils.isEmpty(content)) {
				if (pathEntries.isEmpty()) {
					// 404
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
				} else {
					// document list
					response.setContentType("text/html");
					response.getWriter().append("<style>table th, table td { min-width: 150px; text-align: left; }</style>");
					response.getWriter().append("<table>");
					response.getWriter().append("<thead><tr><th>path</th><th>mode</th><th>size</th></tr>");
					response.getWriter().append("</thead>");
					response.getWriter().append("<tbody>");
					String pattern = "<tr><td><a href=\"{0}/{1}\">{1}</a></td><td>{2}</td><td>{3}</td></tr>";
					final ByteFormat byteFormat = new ByteFormat();
					if (!pathEntries.isEmpty()) {
						if (pathEntries.get(0).path.indexOf('/') > -1) {
							// we are in a subdirectory, add parent directory link
							pathEntries.add(0, new PathModel("..", resource + "/..", 0, FileMode.TREE.getBits(), null, null));
						}
					}

					String basePath = request.getServletPath() + request.getPathInfo();
					if (basePath.charAt(basePath.length() - 1) == '/') {
						// strip trailing slash
						basePath = basePath.substring(0, basePath.length() - 1);
					}
					for (PathModel entry : pathEntries) {
						response.getWriter().append(MessageFormat.format(pattern, basePath, entry.name,
								JGitUtils.getPermissionsFromMode(entry.mode), byteFormat.format(entry.size)));
					}
					response.getWriter().append("</tbody>");
					response.getWriter().append("</table>");
				}
				return;
			}

			// check to see if we should transform markup files
			String ext = StringUtils.getFileExtension(resource);
			if (processor.getMarkupExtensions().contains(ext)) {
				String markup = new String(content, Constants.ENCODING);
				MarkupDocument markupDoc = processor.parse(repository, commit.getName(), resource, markup);
				content = markupDoc.html.getBytes("UTF-8");
				response.setContentType("text/html; charset=" + Constants.ENCODING);
			}

			try {
				// output the content
				response.setHeader("Cache-Control", "public, max-age=3600, must-revalidate");
				response.setDateHeader("Last-Modified", JGitUtils.getCommitDate(commit).getTime());
				response.getOutputStream().write(content);
				response.flushBuffer();
			} catch (Throwable t) {
				logger.error("Failed to write page to client", t);
			}

		} catch (Throwable t) {
			logger.error("Failed to write page to client", t);
		} finally {
			r.close();
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
