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
package com.gitblit.wicket.panels;

import java.util.Collections;
import java.util.List;

import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;

import com.gitblit.Constants;
import com.gitblit.models.Activity;
import com.gitblit.models.Activity.RepositoryCommit;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.CommitDiffPage;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.LogPage;
import com.gitblit.wicket.pages.SearchPage;
import com.gitblit.wicket.pages.SummaryPage;
import com.gitblit.wicket.pages.TreePage;

/**
 * Renders activity in day-blocks in reverse-chronological order.
 * 
 * @author James Moger
 * 
 */
public class ActivityPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public ActivityPanel(String wicketId, List<Activity> recentActivity) {
		super(wicketId);

		Collections.sort(recentActivity);

		DataView<Activity> activityView = new DataView<Activity>("activity",
				new ListDataProvider<Activity>(recentActivity)) {
			private static final long serialVersionUID = 1L;

			public void populateItem(final Item<Activity> item) {
				final Activity entry = item.getModelObject();
				item.add(WicketUtils.createDatestampLabel("title", entry.startDate, getTimeZone()));

				// display the commits in chronological order
				DataView<RepositoryCommit> commits = new DataView<RepositoryCommit>("commits",
						new ListDataProvider<RepositoryCommit>(entry.getCommits())) {
					private static final long serialVersionUID = 1L;

					public void populateItem(final Item<RepositoryCommit> item) {
						final RepositoryCommit commit = item.getModelObject();
						Fragment fragment = new Fragment("commit", "commitFragment", this);

						// time of day
						fragment.add(WicketUtils.createTimeLabel("time", commit.getAuthorIdent()
								.getWhen(), getTimeZone()));

						// avatar
						fragment.add(new GravatarImage("avatar", commit.getAuthorIdent(), 36));

						// merge icon
						if (commit.getParentCount() > 1) {
							fragment.add(WicketUtils.newImage("commitIcon",
									"commit_merge_16x16.png"));
						} else {
							fragment.add(WicketUtils.newBlankImage("commitIcon"));
						}

						// author search link
						String author = commit.getAuthorIdent().getName();
						LinkPanel authorLink = new LinkPanel("author", "list", author,
								SearchPage.class, WicketUtils.newSearchParameter(commit.repository,
										commit.getName(), author, Constants.SearchType.AUTHOR), true);
						setPersonSearchTooltip(authorLink, author, Constants.SearchType.AUTHOR);
						fragment.add(authorLink);

						// repository
						String repoName = StringUtils.stripDotGit(commit.repository);
						LinkPanel repositoryLink = new LinkPanel("repository", null,
								repoName, SummaryPage.class,
								WicketUtils.newRepositoryParameter(commit.repository), true);
						WicketUtils.setCssBackground(repositoryLink, repoName);
						fragment.add(repositoryLink);

						// repository branch
						LinkPanel branchLink = new LinkPanel("branch", "list", commit.branch,
								LogPage.class, WicketUtils.newObjectParameter(commit.repository,
										commit.branch), true);
						WicketUtils.setCssStyle(branchLink, "color: #008000;");
						fragment.add(branchLink);

						LinkPanel commitid = new LinkPanel("commitid", "list subject",
								commit.getShortName(), CommitPage.class,
								WicketUtils.newObjectParameter(commit.repository, commit.getName()), true);
						fragment.add(commitid);

						// message/commit link
						String shortMessage = commit.getShortMessage();
						String trimmedMessage = shortMessage;
						if (commit.getRefs() != null && commit.getRefs().size() > 0) {
							trimmedMessage = StringUtils.trimString(shortMessage, Constants.LEN_SHORTLOG_REFS);
						} else {
							trimmedMessage = StringUtils.trimString(shortMessage, Constants.LEN_SHORTLOG);
						}
						LinkPanel shortlog = new LinkPanel("message", "list subject",
								trimmedMessage, CommitPage.class, WicketUtils.newObjectParameter(
										commit.repository, commit.getName()), true);
						if (!shortMessage.equals(trimmedMessage)) {
							WicketUtils.setHtmlTooltip(shortlog, shortMessage);
						}
						fragment.add(shortlog);

						// refs
						fragment.add(new RefsPanel("commitRefs", commit.repository, commit
								.getRefs()));

						// view, diff, tree links
						fragment.add(new BookmarkablePageLink<Void>("view", CommitPage.class,
								WicketUtils.newObjectParameter(commit.repository, commit.getName())));
						fragment.add(new BookmarkablePageLink<Void>("diff", CommitDiffPage.class,
								WicketUtils.newObjectParameter(commit.repository, commit.getName()))
								.setEnabled(commit.getParentCount() > 0));
						fragment.add(new BookmarkablePageLink<Void>("tree", TreePage.class,
								WicketUtils.newObjectParameter(commit.repository, commit.getName())));

						item.add(fragment);
					}
				};
				item.add(commits);
			}
		};
		add(activityView);
	}
}
