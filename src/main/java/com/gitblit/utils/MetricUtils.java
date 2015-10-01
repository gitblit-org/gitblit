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
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.Metric;
import com.gitblit.models.RefModel;

/**
 * Utility class for collecting metrics on a branch, tag, or other ref within
 * the repository.
 *
 * @author James Moger
 *
 */
public class MetricUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(MetricUtils.class);

	/**
	 * Log an error message and exception.
	 *
	 * @param t
	 * @param repository
	 *            if repository is not null it MUST be the {0} parameter in the
	 *            pattern.
	 * @param pattern
	 * @param objects
	 */
	private static void error(Throwable t, Repository repository, String pattern, Object... objects) {
		List<Object> parameters = new ArrayList<Object>();
		if (objects != null && objects.length > 0) {
			for (Object o : objects) {
				parameters.add(o);
			}
		}
		if (repository != null) {
			parameters.add(0, repository.getDirectory().getAbsolutePath());
		}
		LOGGER.error(MessageFormat.format(pattern, parameters.toArray()), t);
	}

	/**
	 * Returns the list of metrics for the specified commit reference, branch,
	 * or tag within the repository. If includeTotal is true, the total of all
	 * the metrics will be included as the first element in the returned list.
	 *
	 * If the dateformat is unspecified an attempt is made to determine an
	 * appropriate date format by determining the time difference between the
	 * first commit on the branch and the most recent commit. This assumes that
	 * the commits are linear.
	 *
	 * @param repository
	 * @param objectId
	 *            if null or empty, HEAD is assumed.
	 * @param includeTotal
	 * @param dateFormat
	 * @param timezone
	 * @return list of metrics
	 */
	public static List<Metric> getDateMetrics(Repository repository, String objectId,
			boolean includeTotal, String dateFormat, TimeZone timezone) {
		Metric total = new Metric("TOTAL");
		final Map<String, Metric> metricMap = new HashMap<String, Metric>();

		if (JGitUtils.hasCommits(repository)) {
			final List<RefModel> tags = JGitUtils.getTags(repository, true, -1);
			final Map<ObjectId, RefModel> tagMap = new HashMap<ObjectId, RefModel>();
			for (RefModel tag : tags) {
				tagMap.put(tag.getReferencedObjectId(), tag);
			}
			RevWalk revWalk = null;
			try {
				// resolve branch
				ObjectId branchObject;
				if (StringUtils.isEmpty(objectId)) {
					branchObject = JGitUtils.getDefaultBranch(repository);
				} else {
					branchObject = repository.resolve(objectId);
				}

				revWalk = new RevWalk(repository);
				RevCommit lastCommit = revWalk.parseCommit(branchObject);
				revWalk.markStart(lastCommit);

				DateFormat df;
				if (StringUtils.isEmpty(dateFormat)) {
					// dynamically determine date format
					RevCommit firstCommit = JGitUtils.getFirstCommit(repository,
							branchObject.getName());
					int diffDays = (lastCommit.getCommitTime() - firstCommit.getCommitTime())
							/ (60 * 60 * 24);
					total.duration = diffDays;
					if (diffDays <= 365) {
						// Days
						df = new SimpleDateFormat("yyyy-MM-dd");
					} else {
						// Months
						df = new SimpleDateFormat("yyyy-MM");
					}
				} else {
					// use specified date format
					df = new SimpleDateFormat(dateFormat);
				}
				df.setTimeZone(timezone);

				Iterable<RevCommit> revlog = revWalk;
				for (RevCommit rev : revlog) {
					Date d = JGitUtils.getAuthorDate(rev);
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
				error(t, repository, "{0} failed to mine log history for date metrics of {1}",
						objectId);
			} finally {
				if (revWalk != null) {
					revWalk.dispose();
				}
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

	/**
	 * Returns a list of author metrics for the specified repository.
	 *
	 * @param repository
	 * @param objectId
	 *            if null or empty, HEAD is assumed.
	 * @param byEmailAddress
	 *            group metrics by author email address otherwise by author name
	 * @return list of metrics
	 */
	public static List<Metric> getAuthorMetrics(Repository repository, String objectId,
			boolean byEmailAddress) {
		final Map<String, Metric> metricMap = new HashMap<String, Metric>();
		if (JGitUtils.hasCommits(repository)) {
			try {
				RevWalk walk = new RevWalk(repository);
				// resolve branch
				ObjectId branchObject;
				if (StringUtils.isEmpty(objectId)) {
					branchObject = JGitUtils.getDefaultBranch(repository);
				} else {
					branchObject = repository.resolve(objectId);
				}
				RevCommit lastCommit = walk.parseCommit(branchObject);
				walk.markStart(lastCommit);

				Iterable<RevCommit> revlog = walk;
				for (RevCommit rev : revlog) {
					String p;
					if (byEmailAddress) {
						p = rev.getAuthorIdent().getEmailAddress().toLowerCase();
						if (StringUtils.isEmpty(p)) {
							p = rev.getAuthorIdent().getName().toLowerCase();
						}
					} else {
						p = rev.getAuthorIdent().getName().toLowerCase();
						if (StringUtils.isEmpty(p)) {
							p = rev.getAuthorIdent().getEmailAddress().toLowerCase();
						}
					}
					p = p.replace('\n',' ').replace('\r',  ' ').trim();
					if (!metricMap.containsKey(p)) {
						metricMap.put(p, new Metric(p));
					}
					Metric m = metricMap.get(p);
					m.count++;
				}
			} catch (Throwable t) {
				error(t, repository, "{0} failed to mine log history for author metrics of {1}",
						objectId);
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
