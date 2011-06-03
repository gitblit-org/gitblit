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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.Metric;
import com.gitblit.models.RefModel;

public class MetricUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(MetricUtils.class);

	public static List<Metric> getDateMetrics(Repository r, boolean includeTotal, String format) {
		Metric total = new Metric("TOTAL");
		final Map<String, Metric> metricMap = new HashMap<String, Metric>();

		if (JGitUtils.hasCommits(r)) {
			final List<RefModel> tags = JGitUtils.getTags(r, -1);
			final Map<ObjectId, RefModel> tagMap = new HashMap<ObjectId, RefModel>();
			for (RefModel tag : tags) {
				tagMap.put(tag.getReferencedObjectId(), tag);
			}
			try {
				RevWalk walk = new RevWalk(r);
				ObjectId object = r.resolve(Constants.HEAD);
				RevCommit lastCommit = walk.parseCommit(object);
				walk.markStart(lastCommit);

				DateFormat df;
				if (StringUtils.isEmpty(format)) {
					// dynamically determine date format
					RevCommit firstCommit = JGitUtils.getFirstCommit(r, Constants.HEAD);
					int diffDays = (lastCommit.getCommitTime() - firstCommit.getCommitTime())
							/ (60 * 60 * 24);
					total.duration = diffDays;
					if (diffDays <= 90) {
						// Days
						df = new SimpleDateFormat("yyyy-MM-dd");
					} else if (diffDays > 90 && diffDays < 365) {
						// Weeks
						df = new SimpleDateFormat("yyyy-MM (w)");
					} else {
						// Months
						df = new SimpleDateFormat("yyyy-MM");
					}
				} else {
					// use specified date format
					df = new SimpleDateFormat(format);
				}

				Iterable<RevCommit> revlog = walk;
				for (RevCommit rev : revlog) {
					Date d = JGitUtils.getCommitDate(rev);
					String p = df.format(d);
					if (!metricMap.containsKey(p)) {
						metricMap.put(p, new Metric(p));
					}
					Metric m = metricMap.get(p);
					m.count++;
					total.count++;
					if (tagMap.containsKey(rev.getId())) {
						m.tag++;
						total.tag++;
					}
				}
			} catch (Throwable t) {
				LOGGER.error("Failed to mine log history for date metrics", t);
			}
		}
		List<String> keys = new ArrayList<String>(metricMap.keySet());
		Collections.sort(keys);
		List<Metric> metrics = new ArrayList<Metric>();
		for (String key : keys) {
			metrics.add(metricMap.get(key));
		}
		if (includeTotal) {
			metrics.add(0, total);
		}
		return metrics;
	}

	public static List<Metric> getAuthorMetrics(Repository r, boolean byEmail) {
		final Map<String, Metric> metricMap = new HashMap<String, Metric>();

		if (JGitUtils.hasCommits(r)) {
			try {
				RevWalk walk = new RevWalk(r);
				ObjectId object = r.resolve(Constants.HEAD);
				RevCommit lastCommit = walk.parseCommit(object);
				walk.markStart(lastCommit);

				Iterable<RevCommit> revlog = walk;
				for (RevCommit rev : revlog) {
					String p;
					if (byEmail) {
						p = rev.getAuthorIdent().getEmailAddress();
						if (StringUtils.isEmpty(p)) {
							p = rev.getAuthorIdent().getName();
						}
					} else {
						p = rev.getAuthorIdent().getName();
						if (StringUtils.isEmpty(p)) {
							p = rev.getAuthorIdent().getEmailAddress();
						}
					}
					if (!metricMap.containsKey(p)) {
						metricMap.put(p, new Metric(p));
					}
					Metric m = metricMap.get(p);
					m.count++;
				}
			} catch (Throwable t) {
				LOGGER.error("Failed to mine log history for author metrics", t);
			}
		}
		List<String> keys = new ArrayList<String>(metricMap.keySet());
		Collections.sort(keys);
		List<Metric> metrics = new ArrayList<Metric>();
		for (String key : keys) {
			metrics.add(metricMap.get(key));
		}
		return metrics;
	}
}
