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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.Constants;
import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndContentImpl;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndEntryImpl;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.feed.synd.SyndFeedImpl;
import com.sun.syndication.feed.synd.SyndImageImpl;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedOutput;

public class SyndicationUtils {

	public static void toRSS(String hostUrl, String title, String description, String repository,
			List<RevCommit> commits, OutputStream os) throws IOException, FeedException {

		SyndFeed feed = new SyndFeedImpl();
		feed.setFeedType("rss_2.0");
		feed.setTitle(title);
		feed.setLink(MessageFormat.format("{0}/summary/{1}", hostUrl,
				StringUtils.encodeURL(repository)));
		feed.setDescription(description);
		SyndImageImpl image = new SyndImageImpl();
		image.setTitle(Constants.NAME);
		image.setUrl(hostUrl + Constants.RESOURCE_PATH + "gitblt_25.png");
		image.setLink(hostUrl);
		feed.setImage(image);

		List<SyndEntry> entries = new ArrayList<SyndEntry>();
		for (RevCommit commit : commits) {
			SyndEntry entry = new SyndEntryImpl();
			entry.setTitle(commit.getShortMessage());
			entry.setAuthor(commit.getAuthorIdent().getName());
			entry.setLink(MessageFormat.format("{0}/commit/{1}/{2}", hostUrl,
					StringUtils.encodeURL(repository), commit.getName()));
			entry.setPublishedDate(commit.getCommitterIdent().getWhen());

			SyndContent content = new SyndContentImpl();
			content.setType("text/html");
			String html = StringUtils.escapeForHtml(commit.getFullMessage(), false);
			content.setValue(StringUtils.breakLinesForHtml(html));
			entry.setDescription(content);
			entries.add(entry);
		}
		feed.setEntries(entries);

		OutputStreamWriter writer = new OutputStreamWriter(os);
		SyndFeedOutput output = new SyndFeedOutput();
		output.output(feed, writer);
		writer.close();
	}
}
