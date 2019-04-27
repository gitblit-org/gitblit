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

import java.util.List;
import java.util.TimeZone;

import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

import com.gitblit.models.Metric;
import com.gitblit.utils.MetricUtils;

public class MetricUtilsTest extends GitblitUnitTest {

	@Test
	public void testMetrics() throws Exception {
		testMetrics(GitBlitSuite.getHelloworldRepository());
		testMetrics(GitBlitSuite.getJGitRepository());
	}

	private void testMetrics(Repository repository) throws Exception {
		List<Metric> metrics = MetricUtils.getDateMetrics(repository, null, true, null,
				TimeZone.getDefault());
		repository.close();
		assertTrue("No date metrics found!", metrics.size() > 0);
	}

	@Test
	public void testAuthorMetrics() throws Exception {
		Repository repository = GitBlitSuite.getHelloworldRepository();
		List<Metric> byEmail = MetricUtils.getAuthorMetrics(repository, null, true);
		List<Metric> byName = MetricUtils.getAuthorMetrics(repository, null, false);
		repository.close();
		assertEquals("No author metrics found!", GitBlitSuite.helloworldSettings.getInteger(HelloworldKeys.users.byEmail, -1), byEmail.size());
		assertEquals("No author metrics found!", GitBlitSuite.helloworldSettings.getInteger(HelloworldKeys.users.byName, -1), byName.size());
	}
}