/*
 * Copyright 2014 gitblit.com.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.tika.Tika;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.models.PathModel;
import com.gitblit.utils.ByteFormat;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.MarkdownUtils;
import com.gitblit.utils.StringUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Serves the content of a branch.
 *
 * @author James Moger
 *
 */
@Singleton
public class RawServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private transient Logger logger = LoggerFactory.getLogger(RawServlet.class);

	private final IRuntimeManager runtimeManager;

	private final IRepositoryManager repositoryManager;

	@Inject
	public RawServlet(
			IRuntimeManager runtimeManager,
			IRepositoryManager repositoryManager) {

		this.runtimeManager = runtimeManager;
		this.repositoryManager = repositoryManager;
	}

	/**
	 * Returns an url to this servlet for the specified parameters.
	 *
	 * @param baseURL
	 * @param repository
	 * @param branch
	 * @param path
	 * @return an url
	 */
	public static String asLink(String baseURL, String repository, String branch, String path) {
		if (baseURL.length() > 0 && baseURL.charAt(baseURL.length() - 1) == '/') {
			baseURL = baseURL.substring(0, baseURL.length() - 1);
		}

		IStoredSettings settings = GitblitContext.getManager(IRuntimeManager.class).getSettings();
		boolean firstParam = true;
		StringBuilder referenceUrl = new StringBuilder(baseURL + Constants.RAW_PATH);

		// Mimic the wicket page mount parameters, key off same config value
		if (settings.getBoolean(Keys.web.mountParameters, true)) {
			char fsc = settings.getChar(Keys.web.forwardSlashCharacter, '/');
			repository = repository.replace('/', fsc);

			referenceUrl.append(repository);

			if (!StringUtils.isEmpty(branch)) {
				branch = Repository.shortenRefName(branch).replace('/', fsc);
				referenceUrl.append("/" + branch);
			}

			if (!StringUtils.isEmpty(path)) {
				path = path.replace('/', fsc);
				referenceUrl.append("/" + path);
			}
		} else {
			if (!StringUtils.isEmpty(repository)) {
				referenceUrl.append(firstParam ? "?" : "&");
				referenceUrl.append("r=" + repository);
				firstParam = false;
			}
			if (!StringUtils.isEmpty(branch)) {
				referenceUrl.append(firstParam ? "?" : "&");
				referenceUrl.append("h=" + branch);
				firstParam = false;
			}
			if (!StringUtils.isEmpty(path)) {
				referenceUrl.append(firstParam ? "?" : "&");
				referenceUrl.append("f=" + path);
				firstParam = false;
			}
		}
		return referenceUrl.toString();
	}

	protected String getBranch(String repository, HttpServletRequest request) {
		IStoredSettings settings = GitblitContext.getManager(IRuntimeManager.class).getSettings();

		// Mimic the wicket page mount parameters, key off same config value
		if (settings.getBoolean(Keys.web.mountParameters, true)) {
			String pi = request.getPathInfo().substring(1);

			char c = runtimeManager.getSettings().getChar(Keys.web.forwardSlashCharacter, '/');
			pi = pi.replace('!', '/').replace(c, '/');

			String branch = pi.substring(pi.indexOf(repository) + repository.length() + 1);
			int fs = branch.indexOf('/');
			if (fs > -1) {
				branch = branch.substring(0, fs);
			}
			return branch;
		} else {
			return request.getParameter("h");
		}

	}

	protected String getPath(String repository, String branch, HttpServletRequest request) {
		IStoredSettings settings = GitblitContext.getManager(IRuntimeManager.class).getSettings();

		// Mimic the wicket page mount parameters, key off same config value
		if (settings.getBoolean(Keys.web.mountParameters, true)) {
			String base = repository + "/" + branch;
			String pi = request.getPathInfo().substring(1);
			char c = runtimeManager.getSettings().getChar(Keys.web.forwardSlashCharacter, '/');
			pi = pi.replace('!', '/').replace(c, '/');
			if (pi.equals(base)) {
				return "";
			}

			// Need to add an additional character to strip the slash separator between ref and path
			String path = pi.substring(pi.indexOf(base) + base.length() + 1);
			if (path.endsWith("/")) {
				path = path.substring(0, path.length() - 1);
			}
			return path;
		} else {
			return request.getParameter("f");
		}
	}

	protected boolean renderIndex() {
		return false;
	}

	/**
	 * Retrieves the specified resource from the specified branch of the
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

		// determine repository and resource
		String repository = "";
		Repository r = null;
		// Mimic the wicket page mount parameters, key off same config value
		IStoredSettings settings = GitblitContext.getManager(IRuntimeManager.class).getSettings();
		if (settings.getBoolean(Keys.web.mountParameters, true)) {
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

			char c = runtimeManager.getSettings().getChar(Keys.web.forwardSlashCharacter, '/');
			path = path.replace('!', '/').replace(c, '/');

			int offset = 0;
			while (r == null) {
				int slash = path.indexOf('/', offset);
				if (slash == -1) {
					repository = path;
				} else {
					repository = path.substring(0, slash);
				}
				offset = ( slash + 1 );
				r = repositoryManager.getRepository(repository, false);
				if (repository.equals(path)) {
					// either only repository in url or no repository found
					break;
				}
			}

		} else {
			repository = request.getParameter("r");
			r = repositoryManager.getRepository(repository, false);
		}

		ServletContext context = request.getSession().getServletContext();

		try {
			if (r == null) {
				// repository not found!
				String mkd = MessageFormat.format(
						"# Error\nSorry, no valid **repository** specified in this url: {0}!",
						path);
				error(response, mkd);
				return;
			}

			// identify the branch
			String branch = getBranch(repository, request);
			if (StringUtils.isEmpty(branch)) {
				branch = r.getBranch();
				if (branch == null) {
					// no branches found!  empty?
					String mkd = MessageFormat.format(
							"# Error\nSorry, no valid **branch** specified in this url: {0}!",
							path);
					error(response, mkd);
				} else {
					// redirect to default branch
					String base = request.getRequestURI();
					String url = base + branch + "/";
					response.sendRedirect(url);
				}
				return;
			}

			// identify the requested path
			String requestedPath = getPath(repository, branch, request);

			// identify the commit
			RevCommit commit = JGitUtils.getCommit(r, branch);
			if (commit == null) {
				// branch not found!
				String mkd = MessageFormat.format(
						"# Error\nSorry, the repository {0} does not have a **{1}** branch!",
						repository, branch);
				error(response, mkd);
				return;
			}

			Map<String, String> quickContentTypes = new HashMap<>();
			quickContentTypes.put("html", "text/html");
			quickContentTypes.put("htm", "text/html");
			quickContentTypes.put("xml", "application/xml");
			quickContentTypes.put("json", "application/json");

			List<PathModel> pathEntries = JGitUtils.getFilesInPath(r, requestedPath, commit);
			if (pathEntries.isEmpty()) {
				// requested a specific resource
				String file = StringUtils.getLastPathElement(requestedPath);
				try {

					String ext = StringUtils.getFileExtension(file).toLowerCase();
					String contentType = quickContentTypes.get(ext);

					if (contentType == null) {
						List<String> exts = runtimeManager.getSettings().getStrings(Keys.web.prettyPrintExtensions);
						if (exts.contains(ext)) {
							// extension is a registered text type for pretty printing
							contentType = "text/plain";
						} else {
							// query Tika for the content type
							Tika tika = new Tika();
							contentType = tika.detect(file);
						}
					}

					if (contentType == null) {
						// ask the container for the content type
						contentType = context.getMimeType(requestedPath);

						if (contentType == null) {
							// still unknown content type, assume binary
							contentType = "application/octet-stream";
						}
					}

					if (isTextType(contentType) || isTextDataType(contentType)) {

						// load, interpret, and serve text content as UTF-8
						String [] encodings = runtimeManager.getSettings().getStrings(Keys.web.blobEncodings).toArray(new String[0]);
						String content = JGitUtils.getStringContent(r, commit.getTree(), requestedPath, encodings);
						if (content == null) {
							logger.error("RawServlet Failed to load {} {} {}", repository, commit.getName(), path);
							notFound(response, requestedPath, branch);
							return;
						}

						byte [] bytes = content.getBytes(Constants.ENCODING);
						setContentType(response, contentType);
						response.setContentLength(bytes.length);
						ByteArrayInputStream is = new ByteArrayInputStream(bytes);
						sendContent(response, JGitUtils.getCommitDate(commit), is);

					} else {
						// stream binary content directly from the repository
						if (!streamFromRepo(request, response, r, commit, requestedPath)) {
							logger.error("RawServlet Failed to load {} {} {}", repository, commit.getName(), path);
							notFound(response, requestedPath, branch);
						}
					}
					return;
				} catch (Exception e) {
					logger.error(null, e);
				}
			} else {
				// path request
				if (!request.getPathInfo().endsWith("/")) {
					// redirect to trailing '/' url
					response.sendRedirect(request.getServletPath() + request.getPathInfo() + "/");
					return;
				}

				if (renderIndex()) {
					// locate and render an index file
					Map<String, String> names = new TreeMap<String, String>();
					for (PathModel entry : pathEntries) {
						names.put(entry.name.toLowerCase(), entry.name);
					}

					List<String> extensions = new ArrayList<String>();
					extensions.add("html");
					extensions.add("htm");

					String content = null;
					for (String ext : extensions) {
						String key = "index." + ext;

						if (names.containsKey(key)) {
							String fileName = names.get(key);
							String fullPath = fileName;
							if (!requestedPath.isEmpty()) {
								fullPath = requestedPath + "/" + fileName;
							}

							String [] encodings = runtimeManager.getSettings().getStrings(Keys.web.blobEncodings).toArray(new String[0]);
							String stringContent = JGitUtils.getStringContent(r, commit.getTree(), fullPath, encodings);
							if (stringContent == null) {
								continue;
							}
							content = stringContent;
							requestedPath = fullPath;
							break;
						}
					}

					response.setContentType("text/html; charset=" + Constants.ENCODING);
					byte [] bytes = content.getBytes(Constants.ENCODING);
					response.setContentLength(bytes.length);

					ByteArrayInputStream is = new ByteArrayInputStream(bytes);
					sendContent(response, JGitUtils.getCommitDate(commit), is);
					return;
				}
			}

			// no content, document list or 404 page
			if (pathEntries.isEmpty()) {
				// default 404 page
				notFound(response, requestedPath, branch);
				return;
			} else {
				//
				// directory list
				//
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
						String pp = URLEncoder.encode(requestedPath, Constants.ENCODING);
						pathEntries.add(0, new PathModel("..", pp + "/..", 0, FileMode.TREE.getBits(), null, null));
					}
				}

				String basePath = request.getServletPath() + request.getPathInfo();
				if (basePath.charAt(basePath.length() - 1) == '/') {
					// strip trailing slash
					basePath = basePath.substring(0, basePath.length() - 1);
				}
				for (PathModel entry : pathEntries) {
					String pp = URLEncoder.encode(entry.name, Constants.ENCODING);
					response.getWriter().append(MessageFormat.format(pattern, basePath, pp,
							JGitUtils.getPermissionsFromMode(entry.mode),
							entry.isFile() ? byteFormat.format(entry.size) : ""));
				}
				response.getWriter().append("</tbody>");
				response.getWriter().append("</table>");
			}
		} catch (Throwable t) {
			logger.error("Failed to write page to client", t);
		} finally {
			r.close();
		}
	}

	protected boolean isTextType(String contentType) {
		if (contentType.startsWith("text/")
				|| "application/json".equals(contentType)
				|| "application/xml".equals(contentType)) {
			return true;
		}
		return false;
	}

	protected boolean isTextDataType(String contentType) {
		if ("image/svg+xml".equals(contentType)) {
			return true;
		}
		return false;
	}

	/**
	 * Override all text types to be plain text.
	 *
	 * @param response
	 * @param contentType
	 */
	protected void setContentType(HttpServletResponse response, String contentType) {
		if (isTextType(contentType)) {
			response.setContentType("text/plain");
		} else {
			response.setContentType(contentType);
		}
	}

	protected boolean streamFromRepo(HttpServletRequest request, HttpServletResponse response, Repository repository,
			RevCommit commit, String requestedPath) throws IOException {

		boolean served = false;
		RevWalk rw = new RevWalk(repository);
		TreeWalk tw = new TreeWalk(repository);
		try {
			tw.reset();
			tw.addTree(commit.getTree());
			PathFilter f = PathFilter.create(requestedPath);
			tw.setFilter(f);
			tw.setRecursive(true);
			MutableObjectId id = new MutableObjectId();
			ObjectReader reader = tw.getObjectReader();
			while (tw.next()) {
				FileMode mode = tw.getFileMode(0);
				if (mode == FileMode.GITLINK || mode == FileMode.TREE) {
					continue;
				}
				tw.getObjectId(id, 0);

				String filename = StringUtils.getLastPathElement(requestedPath);
				try {
			    	String userAgent = request.getHeader("User-Agent");
					if (userAgent != null && userAgent.indexOf("MSIE 5.5") > -1) {
					      response.setHeader("Content-Disposition", "filename=\""
					    		  +  URLEncoder.encode(filename, Constants.ENCODING) + "\"");
					} else if (userAgent != null && userAgent.indexOf("MSIE") > -1) {
					      response.setHeader("Content-Disposition", "attachment; filename=\""
					    		  +  URLEncoder.encode(filename, Constants.ENCODING) + "\"");
					} else {
							response.setHeader("Content-Disposition", "attachment; filename=\""
							      + new String(filename.getBytes(Constants.ENCODING), "latin1") + "\"");
					}
				}
				catch (UnsupportedEncodingException e) {
					response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
				}

				long len = reader.getObjectSize(id, org.eclipse.jgit.lib.Constants.OBJ_BLOB);
				setContentType(response, "application/octet-stream");
				response.setIntHeader("Content-Length", (int) len);
				ObjectLoader ldr = repository.open(id);
				ldr.copyTo(response.getOutputStream());
				served = true;
			}
		} finally {
			tw.close();
			rw.dispose();
		}

		response.flushBuffer();
		return served;
	}

	protected void sendContent(HttpServletResponse response, Date date, InputStream is) throws ServletException, IOException {

		try {
			byte[] tmp = new byte[8192];
			int len = 0;
			while ((len = is.read(tmp)) > -1) {
				response.getOutputStream().write(tmp, 0, len);
			}
		} finally {
			is.close();
		}
		response.flushBuffer();
	}

	protected void notFound(HttpServletResponse response, String requestedPath, String branch)
			throws ParseException, ServletException, IOException {
		String str = MessageFormat.format(
				"# Error\nSorry, the requested resource **{0}** was not found in **{1}**.",
				requestedPath, branch);
		response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		error(response, str);
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
