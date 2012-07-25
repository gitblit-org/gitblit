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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.wicket.IRequestTarget;
import org.apache.wicket.PageParameters;
import org.apache.wicket.RequestCycle;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.http.WebResponse;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.WicketUtils;

public class RawPage extends WebPage {

	private final Logger logger = LoggerFactory.getLogger(getClass().getSimpleName());

	public RawPage(final PageParameters params) {
		super(params);

		if (!params.containsKey("r")) {
			error(getString("gb.repositoryNotSpecified"));
			redirectToInterceptPage(new RepositoriesPage());
		}

		getRequestCycle().setRequestTarget(new IRequestTarget() {
			@Override
			public void detach(RequestCycle requestCycle) {
			}

			@Override
			public void respond(RequestCycle requestCycle) {
				WebResponse response = (WebResponse) requestCycle.getResponse();

				final String repositoryName = WicketUtils.getRepositoryName(params);
				final String objectId = WicketUtils.getObject(params);
				final String blobPath = WicketUtils.getPath(params);
				String[] encodings = GitBlit.getEncodings();

				Repository r = GitBlit.self().getRepository(repositoryName);
				if (r == null) {
					error(getString("gb.canNotLoadRepository") + " " + repositoryName);
					redirectToInterceptPage(new RepositoriesPage());
					return;
				}

				if (StringUtils.isEmpty(blobPath)) {
					// objectid referenced raw view
					byte [] binary = JGitUtils.getByteContent(r, objectId);
					response.setContentType("application/octet-stream");
					response.setContentLength(binary.length);
					try {
						response.getOutputStream().write(binary);
					} catch (Exception e) {
						logger.error("Failed to write binary response", e);
					}
				} else {
					// standard raw blob view
					RevCommit commit = JGitUtils.getCommit(r, objectId);

					String filename = blobPath;
					if (blobPath.indexOf('/') > -1) {
						filename = blobPath.substring(blobPath.lastIndexOf('/') + 1);
					}

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
						switch (type) {
						case 2:
							// image blobs
							byte[] image = JGitUtils.getByteContent(r, commit.getTree(), blobPath);
							response.setContentType("image/" + extension.toLowerCase());
							response.setContentLength(image.length);
							try {
								response.getOutputStream().write(image);
							} catch (IOException e) {
								logger.error("Failed to write image response", e);
							}
							break;
						case 3:
							// binary blobs (download)
							byte[] binary = JGitUtils.getByteContent(r, commit.getTree(), blobPath);
							response.setContentLength(binary.length);
							response.setContentType("application/octet-stream");
							response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
							try {
								response.getOutputStream().write(binary);
							} catch (IOException e) {
								logger.error("Failed to write binary response", e);
							}
							break;
						default:
							// plain text
							String content = JGitUtils.getStringContent(r, commit.getTree(),
									blobPath, encodings);
							response.setContentType("text/plain; charset=UTF-8");
							try {
								response.getOutputStream().write(content.getBytes("UTF-8"));
							} catch (Exception e) {
								logger.error("Failed to write text response", e);
							}
						}

					} else {
						// plain text
						String content = JGitUtils.getStringContent(r, commit.getTree(), blobPath,
								encodings);
						response.setContentType("text/plain; charset=UTF-8");
						try {
							response.getOutputStream().write(content.getBytes("UTF-8"));
						} catch (Exception e) {
							logger.error("Failed to write text response", e);
						}
					}
				}
				r.close();
			}
		});
	}
}
