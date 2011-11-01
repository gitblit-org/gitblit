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
package com.gitblit.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import com.gitblit.Constants;
import com.gitblit.GitBlitException;
import com.gitblit.models.SyndicatedEntryModel;
import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndContentImpl;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndEntryImpl;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.feed.synd.SyndFeedImpl;
import com.sun.syndication.feed.synd.SyndImageImpl;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.SyndFeedOutput;
import com.sun.syndication.io.XmlReader;

/**
 * Utility class for RSS feeds.
 * 
 * @author James Moger
 * 
 */
public class SyndicationUtils {

	/**
	 * Outputs an RSS feed of the list of entries to the outputstream.
	 * 
	 * @param hostUrl
	 * @param title
	 * @param description
	 * @param repository
	 * @param entryModels
	 * @param os
	 * @throws IOException
	 * @throws FeedException
	 */
	public static void toRSS(String hostUrl, String title, String description, String repository,
			List<SyndicatedEntryModel> entryModels, OutputStream os) throws IOException,
			FeedException {

		SyndFeed feed = new SyndFeedImpl();
		feed.setFeedType("rss_2.0");
		feed.setEncoding("UTF-8");
		feed.setTitle(title);
		feed.setLink(MessageFormat.format("{0}/summary/{1}", hostUrl,
				StringUtils.encodeURL(repository)));
		feed.setDescription(description);
		SyndImageImpl image = new SyndImageImpl();
		image.setTitle(Constants.NAME);
		image.setUrl(hostUrl + "/gitblt_25.png");
		image.setLink(hostUrl);
		feed.setImage(image);

		List<SyndEntry> entries = new ArrayList<SyndEntry>();
		for (SyndicatedEntryModel entryModel : entryModels) {
			SyndEntry entry = new SyndEntryImpl();
			entry.setTitle(entryModel.title);
			entry.setAuthor(entryModel.author);
			entry.setLink(entryModel.link);
			entry.setPublishedDate(entryModel.published);

			SyndContent content = new SyndContentImpl();
			content.setType(entryModel.contentType);
			content.setValue(entryModel.content);
			entry.setDescription(content);
			entries.add(entry);
		}
		feed.setEntries(entries);

		OutputStreamWriter writer = new OutputStreamWriter(os, "UTF-8");
		SyndFeedOutput output = new SyndFeedOutput();
		output.output(feed, writer);
		writer.close();
	}

	/**
	 * Reads a Gitblit RSS feed.
	 * 
	 * @param url
	 *            the url of the Gitblit server
	 * @param repository
	 *            the repository name
	 * @param branch
	 *            the branch name (optional)
	 * @param numberOfEntries
	 *            the number of entries to retrieve. if <= 0 the server default
	 *            is used.
	 * @param username
	 * @param password
	 * @return a list of SyndicationModel entries
	 * @throws {@link IOException}
	 */
	public static List<SyndicatedEntryModel> readFeed(String url, String repository, String branch,
			int numberOfEntries, String username, char[] password) throws IOException {
		String feedUrl;
		if (StringUtils.isEmpty(branch)) {
			// no branch specified
			if (numberOfEntries > 0) {
				// fixed number of entries
				feedUrl = MessageFormat.format("{0}/feed/{1}?l={2,number,0}", url, repository);
			} else {
				// server default number of entries
				feedUrl = MessageFormat.format("{0}/feed/{1}", url, repository);
			}
		} else {
			// branch specified
			if (numberOfEntries > 0) {
				// fixed number of entries
				feedUrl = MessageFormat.format("{0}/feed/{1}?h={2}&l={3,number,0}", url,
						repository, branch, numberOfEntries);
			} else {
				// server default number of entries
				feedUrl = MessageFormat.format("{0}/feed/{1}?h={2}", url, repository, branch);
			}
		}
		URLConnection conn = ConnectionUtils.openReadConnection(feedUrl, username, password);
		InputStream is = conn.getInputStream();
		SyndFeedInput input = new SyndFeedInput();
		SyndFeed feed = null;
		try {
			feed = input.build(new XmlReader(is));
		} catch (FeedException f) {
			throw new GitBlitException(f);
		}
		is.close();
		List<SyndicatedEntryModel> entries = new ArrayList<SyndicatedEntryModel>();
		for (Object o : feed.getEntries()) {
			SyndEntryImpl entry = (SyndEntryImpl) o;
			SyndicatedEntryModel model = new SyndicatedEntryModel();
			model.repository = repository;
			model.branch = branch;
			model.title = entry.getTitle();
			model.author = entry.getAuthor();
			model.published = entry.getPublishedDate();
			model.link = entry.getLink();
			model.content = entry.getDescription().getValue();
			model.contentType = entry.getDescription().getType();
			entries.add(model);
		}
		return entries;
	}
}
