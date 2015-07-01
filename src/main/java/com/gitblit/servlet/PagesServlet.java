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
import java.io.InputStream;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.Constants;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.RuntimeManager;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Serves the content of a gh-pages branch.
 *
 * @author James Moger
 *
 */
@Singleton
public class PagesServlet extends RawServlet {

	private static final long serialVersionUID = 1L;


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
		IStoredSettings settings = GitblitContext.getManager(IRuntimeManager.class).getSettings();
		boolean firstParam = true;
		StringBuilder referenceUrl = new StringBuilder(baseURL + Constants.PAGES);

		// Mimic the wicket page mount parameters, key off same config value
		if (settings.getBoolean(Keys.web.mountParameters, true)) {
			char fsc = settings.getChar(Keys.web.forwardSlashCharacter, '/');
			repository = repository.replace('/', fsc);

			referenceUrl.append(repository);

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
			if (!StringUtils.isEmpty(path)) {
				referenceUrl.append(firstParam ? "?" : "&");
				referenceUrl.append("f=" + path);
				firstParam = false;
			}
		}
		return referenceUrl.toString();
	}

	@Inject
	public PagesServlet(
			IRuntimeManager runtimeManager,
			IRepositoryManager repositoryManager) {

		super(runtimeManager, repositoryManager);
	}

	@Override
	protected String getBranch(String repository, HttpServletRequest request) {
		return "gh-pages";
	}

	@Override
	protected String getPath(String repository, String branch, HttpServletRequest request) {
		return super.getPath(repository, null, request);
	}

	@Override
	protected boolean renderIndex() {
		return true;
	}

	@Override
	protected void setContentType(HttpServletResponse response, String contentType) {
		response.setContentType(contentType);;
	}

	@Override
	protected boolean streamFromRepo(HttpServletRequest request, HttpServletResponse response, Repository repository,
			RevCommit commit, String requestedPath) throws IOException {

		response.setDateHeader("Last-Modified", JGitUtils.getCommitDate(commit).getTime());
		response.setHeader("Cache-Control", "public, max-age=3600, must-revalidate");

		return super.streamFromRepo(request, response, repository, commit, requestedPath);
	}

	@Override
	protected void sendContent(HttpServletResponse response, Date date, InputStream is) throws ServletException, IOException {
		response.setDateHeader("Last-Modified", date.getTime());
		response.setHeader("Cache-Control", "public, max-age=3600, must-revalidate");

		super.sendContent(response, date, is);
	}
}
