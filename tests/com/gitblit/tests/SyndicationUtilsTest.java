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
package com.gitblit.tests;

import java.io.ByteArrayOutputStream;
import java.util.List;

import junit.framework.TestCase;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.models.SyndicatedEntryModel;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.SyndicationUtils;

public class SyndicationUtilsTest extends TestCase {

	public void testSyndication() throws Exception {
		Repository repository = GitBlitSuite.getHelloworldRepository();
		List<RevCommit> commits = JGitUtils.getRevLog(repository, 1);
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		SyndicationUtils.toRSS("http://localhost", "Title", "Description", "Repository", commits,
				os);
		String feed = os.toString();
		os.close();
		assertTrue(feed.length() > 100);
		assertTrue(feed.indexOf("<title>Title</title>") > -1);
		assertTrue(feed.indexOf("<description>Description</description>") > -1);
	}

	public void testFeedRead() throws Exception {
		List<SyndicatedEntryModel> feed = SyndicationUtils.readFeed("https://localhost:8443",
				"ticgit.git", "master", 5, "admin", "admin".toCharArray());
		assertTrue(feed != null);
		assertTrue(feed.size() > 0);
		assertEquals(5, feed.size());
	}
}