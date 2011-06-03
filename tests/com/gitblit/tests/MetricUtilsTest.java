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

import junit.framework.TestCase;

import org.eclipse.jgit.lib.Repository;

import com.gitblit.models.Metric;
import com.gitblit.utils.MetricUtils;

public class MetricUtilsTest extends TestCase {

	public void testMetrics() throws Exception {
		Repository repository = GitBlitSuite.getHelloworldRepository();
		List<Metric> metrics = MetricUtils.getDateMetrics(repository, true, null);
		repository.close();
		assertTrue("No date metrics found!", metrics.size() > 0);
	}
	
	public void testAuthorMetrics() throws Exception {
		Repository repository = GitBlitSuite.getHelloworldRepository();
		List<Metric> byEmail = MetricUtils.getAuthorMetrics(repository, true);
		List<Metric> byName = MetricUtils.getAuthorMetrics(repository, false);
		repository.close();
		assertTrue("No author metrics found!", byEmail.size() == 9);
		assertTrue("No author metrics found!", byName.size() == 8);
	}
}