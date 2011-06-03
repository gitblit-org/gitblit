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
package com.gitblit.wicket.pages;

import java.util.HashMap;
import java.util.Map;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.WicketUtils;

public class RawPage extends WebPage {

	public RawPage(PageParameters params) {
		super(params);

		if (!params.containsKey("r")) {
			error("Repository not specified!");
			redirectToInterceptPage(new RepositoriesPage());
		}
		final String repositoryName = WicketUtils.getRepositoryName(params);
		final String objectId = WicketUtils.getObject(params);
		final String blobPath = WicketUtils.getPath(params);

		Repository r = GitBlit.self().getRepository(repositoryName);
		if (r == null) {
			error("Can not load repository " + repositoryName);
			redirectToInterceptPage(new RepositoriesPage());
			return;
		}

		if (StringUtils.isEmpty(blobPath)) {
			// objectid referenced raw view
			Label blobLabel = new Label("rawText", JGitUtils.getStringContent(r, objectId));
			WicketUtils.setCssClass(blobLabel, "plainprint");
			add(blobLabel);
		} else {
			// standard raw blob view
			RevCommit commit = JGitUtils.getCommit(r, objectId);

			String extension = null;
			if (blobPath.lastIndexOf('.') > -1) {
				extension = blobPath.substring(blobPath.lastIndexOf('.') + 1);
			}

			// Map the extensions to types
			Map<String, Integer> map = new HashMap<String, Integer>();
			for (String ext : GitBlit.getStrings(Keys.web.imageExtensions)) {
				map.put(ext.toLowerCase(), 2);
			}
			for (String ext : GitBlit.getStrings(Keys.web.binaryExtensions)) {
				map.put(ext.toLowerCase(), 3);
			}

			if (extension != null) {
				int type = 0;
				if (map.containsKey(extension)) {
					type = map.get(extension);
				}
				Component c = null;
				switch (type) {
				case 2:
					// TODO image blobs
					c = new Label("rawText", "Image File");
					break;
				case 3:
					// TODO binary blobs
					c = new Label("rawText", "Binary File");
					break;
				default:
					// plain text
					c = new Label("rawText", JGitUtils.getStringContent(r, commit.getTree(),
							blobPath));
					WicketUtils.setCssClass(c, "plainprint");
				}
				add(c);
			} else {
				// plain text
				Label blobLabel = new Label("rawText", JGitUtils.getStringContent(r,
						commit.getTree(), blobPath));
				WicketUtils.setCssClass(blobLabel, "plainprint");
				add(blobLabel);
			}
		}
		r.close();
	}
}
