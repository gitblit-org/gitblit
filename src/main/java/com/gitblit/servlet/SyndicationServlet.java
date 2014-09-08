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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.servlet.http.HttpServlet;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IProjectManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.models.FeedEntryModel;
import com.gitblit.models.ProjectModel;
import com.gitblit.models.RefModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.servlet.AuthenticationFilter.AuthenticatedRequest;
import com.gitblit.utils.BugtraqProcessor;
import com.gitblit.utils.HttpUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.SyndicationUtils;

/**
 * SyndicationServlet generates RSS 2.0 feeds and feed links.
 *
 * Access to this servlet is protected by the SyndicationFilter.
 *
 * @author James Moger
 *
 */
@Singleton
public class SyndicationServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private transient Logger logger = LoggerFactory.getLogger(SyndicationServlet.class);

	private IStoredSettings settings;

	private IRepositoryManager repositoryManager;

	private IProjectManager projectManager;

	@Inject
	public SyndicationServlet(
			IStoredSettings settings,
			IRepositoryManager repositoryManager,
			IProjectManager projectManager) {

		this.settings = settings;
		this.repositoryManager = repositoryManager;
		this.projectManager = projectManager;
	}

	/**
	 * Create a feed link for the specified repository and branch/tag/commit id.
	 *
	 * @param baseURL
	 * @param repository
	 *            the repository name
	 * @param objectId
	 *            the branch, tag, or first commit for the feed
	 * @param length
	 *            the number of commits to include in the feed
	 * @return an RSS feed url
	 */
	public static String asLink(String baseURL, String repository, String objectId, int length) {
		if (baseURL.length() > 0 && baseURL.charAt(baseURL.length() - 1) == '/') {
			baseURL = baseURL.substring(0, baseURL.length() - 1);
		}
		StringBuilder url = new StringBuilder();
		url.append(baseURL);
		url.append(Constants.SYNDICATION_PATH);
		url.append(repository);
		if (!StringUtils.isEmpty(objectId) || length > 0) {
			StringBuilder parameters = new StringBuilder("?");
			if (StringUtils.isEmpty(objectId)) {
				parameters.append("l=");
				parameters.append(length);
			} else {
				parameters.append("h=");
				parameters.append(objectId);
				if (length > 0) {
					parameters.append("&l=");
					parameters.append(length);
				}
			}
			url.append(parameters);
		}
		return url.toString();
	}

	/**
	 * Determines the appropriate title for a feed.
	 *
	 * @param repository
	 * @param objectId
	 * @return title of the feed
	 */
	public static String getTitle(String repository, String objectId) {
		String id = objectId;
		if (!StringUtils.isEmpty(id)) {
			if (id.startsWith(org.eclipse.jgit.lib.Constants.R_HEADS)) {
				id = id.substring(org.eclipse.jgit.lib.Constants.R_HEADS.length());
			} else if (id.startsWith(org.eclipse.jgit.lib.Constants.R_REMOTES)) {
				id = id.substring(org.eclipse.jgit.lib.Constants.R_REMOTES.length());
			} else if (id.startsWith(org.eclipse.jgit.lib.Constants.R_TAGS)) {
				id = id.substring(org.eclipse.jgit.lib.Constants.R_TAGS.length());
			}
		}
		return MessageFormat.format("{0} ({1})", repository, id);
	}

	/**
	 * Generates the feed content.
	 *
	 * @param request
	 * @param response
	 * @throws javax.servlet.ServletException
	 * @throws java.io.IOException
	 */
	private void processRequest(javax.servlet.http.HttpServletRequest request,
			javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException,
			java.io.IOException {

		String servletUrl = request.getContextPath() + request.getServletPath();
		String url = request.getRequestURI().substring(servletUrl.length());
		if (url.length() > 1 && url.charAt(0) == '/') {
			url = url.substring(1);
		}
		String repositoryName = url;
		String objectId = request.getParameter("h");
		String l = request.getParameter("l");
		String page = request.getParameter("pg");
		String searchString = request.getParameter("s");
		Constants.SearchType searchType = Constants.SearchType.COMMIT;
		if (!StringUtils.isEmpty(request.getParameter("st"))) {
			Constants.SearchType type = Constants.SearchType.forName(request.getParameter("st"));
			if (type != null) {
				searchType = type;
			}
		}

		Constants.FeedObjectType objectType = Constants.FeedObjectType.COMMIT;
		if (!StringUtils.isEmpty(request.getParameter("ot"))) {
			Constants.FeedObjectType type = Constants.FeedObjectType.forName(request.getParameter("ot"));
			if (type != null) {
				objectType = type;
			}
		}

		int length = settings.getInteger(Keys.web.syndicationEntries, 25);
		if (StringUtils.isEmpty(objectId)) {
			objectId = org.eclipse.jgit.lib.Constants.HEAD;
		}
		if (!StringUtils.isEmpty(l)) {
			try {
				length = Integer.parseInt(l);
			} catch (NumberFormatException x) {
			}
		}
		int offset = 0;
		if (!StringUtils.isEmpty(page)) {
			try {
				offset = length * Integer.parseInt(page);
			} catch (NumberFormatException x) {
			}
		}

		response.setContentType("application/rss+xml; charset=UTF-8");

		boolean isProjectFeed = false;
		String feedName = "Gitblit";
		String feedTitle = null;
		String feedDescription = null;

		List<String> repositories = null;
		if (repositoryName.indexOf('/') == -1 && !repositoryName.toLowerCase().endsWith(".git")) {
			// try to find a project
			UserModel user = null;
			if (request instanceof AuthenticatedRequest) {
				user = ((AuthenticatedRequest) request).getUser();
			}
			ProjectModel project = projectManager.getProjectModel(repositoryName, user);
			if (project != null) {
				isProjectFeed = true;
				repositories = new ArrayList<String>(project.repositories);

				// project feed
				feedName = project.name;
				feedTitle = project.title;
				feedDescription = project.description;
			}
		}

		if (repositories == null) {
			// could not find project, assume this is a repository
			repositories = Arrays.asList(repositoryName);
		}


		boolean mountParameters = settings.getBoolean(Keys.web.mountParameters, true);

		String gitblitUrl = settings.getString(Keys.web.canonicalUrl, null);
		if (StringUtils.isEmpty(gitblitUrl)) {
			gitblitUrl = HttpUtils.getGitblitURL(request);
		}
		char fsc = settings.getChar(Keys.web.forwardSlashCharacter, '/');

		List<FeedEntryModel> entries = new ArrayList<FeedEntryModel>();

		for (String name : repositories) {
			Repository repository = repositoryManager.getRepository(name);
			RepositoryModel model = repositoryManager.getRepositoryModel(name);

			if (repository == null) {
				if (model != null && model.isCollectingGarbage) {
					logger.warn(MessageFormat.format("Temporarily excluding {0} from feed, busy collecting garbage", name));
				}
				continue;
			}
			if (!isProjectFeed) {
				// single-repository feed
				feedName = model.name;
				feedTitle = model.name;
				feedDescription = model.description;
			}

			if (objectType == Constants.FeedObjectType.TAG) {

				String urlPattern;
				if (mountParameters) {
					// mounted parameters
					urlPattern = "{0}/tag/{1}/{2}";
				} else {
					// parameterized parameters
					urlPattern = "{0}/tag/?r={1}&h={2}";
				}

				List<RefModel> tags = JGitUtils.getTags(repository, false, length, offset);

				for (RefModel tag : tags) {
					FeedEntryModel entry = new FeedEntryModel();
					entry.title = tag.getName();
					entry.author = tag.getAuthorIdent().getName();
					entry.link = MessageFormat.format(urlPattern, gitblitUrl,
							StringUtils.encodeURL(model.name.replace('/', fsc)), tag.getObjectId().getName());
					entry.published = tag.getDate();
					entry.contentType = "text/html";
					entry.content = tag.getFullMessage();
					entry.repository = model.name;
					entry.branch = objectId;

					entry.tags = new ArrayList<String>();

					// add tag id and referenced commit id
					entry.tags.add("tag:" + tag.getObjectId().getName());
					entry.tags.add("commit:" + tag.getReferencedObjectId().getName());

					entries.add(entry);
				}
			} else {

				String urlPattern;
				if (mountParameters) {
					// mounted parameters
					urlPattern = "{0}/commit/{1}/{2}";
				} else {
					// parameterized parameters
					urlPattern = "{0}/commit/?r={1}&h={2}";
				}

				List<RevCommit> commits;
				if (StringUtils.isEmpty(searchString)) {
					// standard log/history lookup
					commits = JGitUtils.getRevLog(repository, objectId, offset, length);
				} else {
					// repository search
					commits = JGitUtils.searchRevlogs(repository, objectId, searchString, searchType,
							offset, length);
				}
				Map<ObjectId, List<RefModel>> allRefs = JGitUtils.getAllRefs(repository, model.showRemoteBranches);
				BugtraqProcessor processor = new BugtraqProcessor(settings);

				// convert RevCommit to SyndicatedEntryModel
				for (RevCommit commit : commits) {
					FeedEntryModel entry = new FeedEntryModel();
					entry.title = commit.getShortMessage();
					entry.author = commit.getAuthorIdent().getName();
					entry.link = MessageFormat.format(urlPattern, gitblitUrl,
							StringUtils.encodeURL(model.name.replace('/', fsc)), commit.getName());
					entry.published = commit.getCommitterIdent().getWhen();
					entry.contentType = "text/html";
					String message = processor.processCommitMessage(repository, model, commit.getFullMessage());
					entry.content = message;
					entry.repository = model.name;
					entry.branch = objectId;
					entry.tags = new ArrayList<String>();

					// add commit id and parent commit ids
					entry.tags.add("commit:" + commit.getName());
					for (RevCommit parent : commit.getParents()) {
						entry.tags.add("parent:" + parent.getName());
					}

					// add refs to tabs list
					List<RefModel> refs = allRefs.get(commit.getId());
					if (refs != null && refs.size() > 0) {
						for (RefModel ref : refs) {
							entry.tags.add("ref:" + ref.getName());
						}
					}
					entries.add(entry);
				}
			}
		}

		// sort & truncate the feed
		Collections.sort(entries);
		if (entries.size() > length) {
			// clip the list
			entries = entries.subList(0, length);
		}

		String feedLink;
		if (isProjectFeed) {
			// project feed
			if (mountParameters) {
				// mounted url
				feedLink = MessageFormat.format("{0}/project/{1}", gitblitUrl,
						StringUtils.encodeURL(feedName));
			} else {
				// parameterized url
				feedLink = MessageFormat.format("{0}/project/?p={1}", gitblitUrl,
						StringUtils.encodeURL(feedName));
			}
		} else {
			// repository feed
			if (mountParameters) {
				// mounted url
				feedLink = MessageFormat.format("{0}/summary/{1}", gitblitUrl,
						StringUtils.encodeURL(feedName));
			} else {
				// parameterized url
				feedLink = MessageFormat.format("{0}/summary/?r={1}", gitblitUrl,
						StringUtils.encodeURL(feedName));
			}
		}

		try {
			SyndicationUtils.toRSS(gitblitUrl, feedLink, getTitle(feedTitle, objectId),
					feedDescription, entries, response.getOutputStream());
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
