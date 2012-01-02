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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.GitBlit;
import com.gitblit.models.Activity;
import com.gitblit.models.Activity.RepositoryCommit;
import com.gitblit.models.GravatarProfile;
import com.gitblit.models.RefModel;
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
	 * @param models
	 *            the list of repositories to query
	 * @param daysBack
	 *            the number of days back from Now to collect
	 * @param objectId
	 *            the branch to retrieve. If this value is null the default
	 *            branch of the repository is used.
	 * @return
	 */
	public static List<Activity> getRecentActivity(List<RepositoryModel> models, int daysBack,
			String objectId) {

		// Activity panel shows last daysBack of activity across all
		// repositories.
		Date thresholdDate = new Date(System.currentTimeMillis() - daysBack * TimeUtils.ONEDAY);

		// Build a map of DailyActivity from the available repositories for the
		// specified threshold date.
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		Calendar cal = Calendar.getInstance();

		Map<String, Activity> activity = new HashMap<String, Activity>();
		for (RepositoryModel model : models) {
			if (model.hasCommits && model.lastChange.after(thresholdDate)) {
				Repository repository = GitBlit.self().getRepository(model.name);
				List<RevCommit> commits = JGitUtils.getRevLog(repository, objectId, thresholdDate);
				Map<ObjectId, List<RefModel>> allRefs = JGitUtils.getAllRefs(repository);
				repository.close();

				// determine commit branch
				String branch = objectId;
				if (StringUtils.isEmpty(branch) && !commits.isEmpty()) {
					List<RefModel> headRefs = allRefs.get(commits.get(0).getId());
					List<String> localBranches = new ArrayList<String>();
					for (RefModel ref : headRefs) {
						if (ref.getName().startsWith(Constants.R_HEADS)) {
							localBranches.add(ref.getName().substring(Constants.R_HEADS.length()));
						}
					}
					// determine branch
					if (localBranches.size() == 1) {
						// only one branch, choose it
						branch = localBranches.get(0);
					} else if (localBranches.size() > 1) {
						if (localBranches.contains("master")) {
							// choose master
							branch = "master";
						} else {
							// choose first branch
							branch = localBranches.get(0);
						}
					}
				}

				for (RevCommit commit : commits) {
					Date date = JGitUtils.getCommitDate(commit);
					String dateStr = df.format(date);
					if (!activity.containsKey(dateStr)) {
						// Normalize the date to midnight
						cal.setTime(date);
						cal.set(Calendar.HOUR_OF_DAY, 0);
						cal.set(Calendar.MINUTE, 0);
						cal.set(Calendar.SECOND, 0);
						cal.set(Calendar.MILLISECOND, 0);
						activity.put(dateStr, new Activity(cal.getTime()));
					}
					RepositoryCommit commitModel = activity.get(dateStr).addCommit(model.name,
							branch, commit);
					commitModel.setRefs(allRefs.get(commit.getId()));
				}
			}
		}

		List<Activity> recentActivity = new ArrayList<Activity>(activity.values());
		for (Activity daily : recentActivity) {
			Collections.sort(daily.commits);
		}
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
	 *            size of thumbnail. if width <= 0, the defalt of 60 is used.
	 * @return
	 */
	public static String getGravatarThumbnailUrl(String email, int width) {
		if (width <= 0) {
			width = 60;
		}
		String emailHash = StringUtils.getMD5(email);
		String url = MessageFormat.format(
				"http://www.gravatar.com/avatar/{0}?s={1,number,0}&d=identicon", emailHash, width);
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
		String url = MessageFormat.format("http://www.gravatar.com/{0}.json", hash);
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
