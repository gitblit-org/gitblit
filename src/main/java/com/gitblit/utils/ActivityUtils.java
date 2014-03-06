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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.models.Activity;
import com.gitblit.models.GravatarProfile;
import com.gitblit.models.RefModel;
import com.gitblit.models.RepositoryCommit;
import com.gitblit.models.RepositoryModel;
import com.google.gson.reflect.TypeToken;

/**
 * Utility class for building activity information from repositories.
 *
 * @author James Moger
 *
 */
public class ActivityUtils {

	/**
	 * Gets the recent activity from the repositories for the last daysBack days
	 * on the specified branch.
	 *
	 * @param settings
	 *            the runtime settings
	 * @param repositoryManager
	 *            the repository manager
	 * @param models
	 *            the list of repositories to query
	 * @param daysBack
	 *            the number of days back from Now to collect
	 * @param objectId
	 *            the branch to retrieve. If this value is null or empty all
	 *            branches are queried.
	 * @param timezone
	 *            the timezone for aggregating commits
	 * @return
	 */
	public static List<Activity> getRecentActivity(
					IStoredSettings settings,
					IRepositoryManager repositoryManager,
					List<RepositoryModel> models,
					int daysBack,
					String objectId,
					TimeZone timezone) {

		// Activity panel shows last daysBack of activity across all
		// repositories.
		Date thresholdDate = new Date(System.currentTimeMillis() - daysBack * TimeUtils.ONEDAY);

		// Build a map of DailyActivity from the available repositories for the
		// specified threshold date.
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		df.setTimeZone(timezone);
		Calendar cal = Calendar.getInstance();
		cal.setTimeZone(timezone);

		// aggregate author exclusions
		Set<String> authorExclusions = new TreeSet<String>();
		authorExclusions.addAll(settings.getStrings(Keys.web.metricAuthorExclusions));
		for (RepositoryModel model : models) {
			if (!ArrayUtils.isEmpty(model.metricAuthorExclusions)) {
				authorExclusions.addAll(model.metricAuthorExclusions);
			}
		}

		Map<String, Activity> activity = new HashMap<String, Activity>();
		for (RepositoryModel model : models) {
			if (!model.isShowActivity()) {
				// skip this repository
				continue;
			}
			if (model.hasCommits && model.lastChange.after(thresholdDate)) {
				if (model.isCollectingGarbage) {
					continue;
				}
				Repository repository = repositoryManager.getRepository(model.name);
				List<String> branches = new ArrayList<String>();
				if (StringUtils.isEmpty(objectId)) {
					for (RefModel local : JGitUtils.getLocalBranches(
							repository, true, -1)) {
			        	if (!local.getDate().after(thresholdDate)) {
							// branch not recently updated
			        		continue;
			        	}
						branches.add(local.getName());
					}
				} else {
					branches.add(objectId);
				}

				for (String branch : branches) {
					String shortName = branch;
					if (shortName.startsWith(Constants.R_HEADS)) {
						shortName = shortName.substring(Constants.R_HEADS.length());
					}
					List<RepositoryCommit> commits = CommitCache.instance().getCommits(model.name, repository, branch, thresholdDate);
					if (model.maxActivityCommits > 0 && commits.size() > model.maxActivityCommits) {
						// trim commits to maximum count
						commits = commits.subList(0,  model.maxActivityCommits);
					}
					for (RepositoryCommit commit : commits) {
						Date date = commit.getCommitDate();
						String dateStr = df.format(date);
						if (!activity.containsKey(dateStr)) {
							// Normalize the date to midnight
							cal.setTime(date);
							cal.set(Calendar.HOUR_OF_DAY, 0);
							cal.set(Calendar.MINUTE, 0);
							cal.set(Calendar.SECOND, 0);
							cal.set(Calendar.MILLISECOND, 0);
							Activity a = new Activity(cal.getTime());
							a.excludeAuthors(authorExclusions);
							activity.put(dateStr, a);
						}
						activity.get(dateStr).addCommit(commit);
					}
				}

				// close the repository
				repository.close();
			}
		}

		List<Activity> recentActivity = new ArrayList<Activity>(activity.values());
		return recentActivity;
	}

	/**
	 * Returns the Gravatar profile, if available, for the specified email
	 * address.
	 *
	 * @param emailaddress
	 * @return a Gravatar Profile
	 * @throws IOException
	 */
	public static GravatarProfile getGravatarProfileFromAddress(String emailaddress)
			throws IOException {
		return getGravatarProfile(StringUtils.getMD5(emailaddress.toLowerCase()));
	}

	/**
	 * Creates a Gravatar thumbnail url from the specified email address.
	 *
	 * @param email
	 *            address to query Gravatar
	 * @param width
	 *            size of thumbnail. if width <= 0, the default of 50 is used.
	 * @return
	 */
	public static String getGravatarIdenticonUrl(String email, int width) {
		if (width <= 0) {
			width = 50;
		}
		String emailHash = StringUtils.getMD5(email);
		String url = MessageFormat.format(
				"https://www.gravatar.com/avatar/{0}?s={1,number,0}&d=identicon", emailHash, width);
		return url;
	}

	/**
	 * Creates a Gravatar thumbnail url from the specified email address.
	 *
	 * @param email
	 *            address to query Gravatar
	 * @param width
	 *            size of thumbnail. if width <= 0, the default of 50 is used.
	 * @return
	 */
	public static String getGravatarThumbnailUrl(String email, int width) {
		if (width <= 0) {
			width = 50;
		}
		String emailHash = StringUtils.getMD5(email);
		String url = MessageFormat.format(
				"https://www.gravatar.com/avatar/{0}?s={1,number,0}&d=mm", emailHash, width);
		return url;
	}

	/**
	 * Returns the Gravatar profile, if available, for the specified hashcode.
	 * address.
	 *
	 * @param hash
	 *            the hash of the email address
	 * @return a Gravatar Profile
	 * @throws IOException
	 */
	public static GravatarProfile getGravatarProfile(String hash) throws IOException {
		String url = MessageFormat.format("https://www.gravatar.com/{0}.json", hash);
		// Gravatar has a complex json structure
		Type profileType = new TypeToken<Map<String, List<GravatarProfile>>>() {
		}.getType();
		Map<String, List<GravatarProfile>> profiles = null;
		try {
			profiles = JsonUtils.retrieveJson(url, profileType);
		} catch (FileNotFoundException e) {
		}
		if (profiles == null || profiles.size() == 0) {
			return null;
		}
		// due to the complex json structure we need to pull out the profile
		// from a list 2 levels deep
		GravatarProfile profile = profiles.values().iterator().next().get(0);
		return profile;
	}
}
