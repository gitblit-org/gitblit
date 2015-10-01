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
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.Constants;
import com.gitblit.Keys;
import com.gitblit.models.Activity;
import com.gitblit.models.RepositoryCommit;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.CommitDiffPage;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.GitSearchPage;
import com.gitblit.wicket.pages.LogPage;
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

		final int shortHashLen = app().settings().getInteger(Keys.web.shortCommitIdLength, 6);
		DataView<Activity> activityView = new DataView<Activity>("activity",
				new ListDataProvider<Activity>(recentActivity)) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<Activity> activityItem) {
				final Activity entry = activityItem.getModelObject();
				activityItem.add(WicketUtils.createDatestampLabel("title", entry.startDate, getTimeZone(), getTimeUtils()));

				// display the commits in chronological order
				DataView<RepositoryCommit> commits = new DataView<RepositoryCommit>("commit",
						new ListDataProvider<RepositoryCommit>(entry.getCommits())) {
					private static final long serialVersionUID = 1L;

					@Override
					public void populateItem(final Item<RepositoryCommit> commitItem) {
						final RepositoryCommit commit = commitItem.getModelObject();

						// commit time of day
						commitItem.add(WicketUtils.createTimeLabel("time", commit.getCommitterIdent()
								.getWhen(), getTimeZone(), getTimeUtils()));

						// avatar
						commitItem.add(new AvatarImage("avatar", commit.getAuthorIdent(), 40));

						// merge icon
						if (commit.getParentCount() > 1) {
							commitItem.add(WicketUtils.newImage("commitIcon",
									"commit_merge_16x16.png"));
						} else {
							commitItem.add(WicketUtils.newBlankImage("commitIcon").setVisible(false));
						}

						// author search link
						String author = commit.getAuthorIdent().getName();
						LinkPanel authorLink = new LinkPanel("author", "list", author,
								GitSearchPage.class, WicketUtils.newSearchParameter(commit.repository,
										null, author, Constants.SearchType.AUTHOR), true);
						setPersonSearchTooltip(authorLink, author, Constants.SearchType.AUTHOR);
						commitItem.add(authorLink);

						// repository
						String repoName = StringUtils.stripDotGit(commit.repository);
						LinkPanel repositoryLink = new LinkPanel("repository", null,
								repoName, SummaryPage.class,
								WicketUtils.newRepositoryParameter(commit.repository), true);
						WicketUtils.setCssBackground(repositoryLink, repoName);
						commitItem.add(repositoryLink);

						// repository branch
						LinkPanel branchLink = new LinkPanel("branch", "list", Repository.shortenRefName(commit.branch),
								LogPage.class, WicketUtils.newObjectParameter(commit.repository,
										commit.branch), true);
						WicketUtils.setCssStyle(branchLink, "color: #008000;");
						commitItem.add(branchLink);

						LinkPanel commitid = new LinkPanel("commitid", "list subject",
								commit.getName().substring(0,  shortHashLen), CommitPage.class,
								WicketUtils.newObjectParameter(commit.repository, commit.getName()), true);
						commitItem.add(commitid);

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
						commitItem.add(shortlog);

						// refs
						commitItem.add(new RefsPanel("commitRefs", commit.repository, commit
								.getRefs()));

						// diff, tree links
						commitItem.add(new BookmarkablePageLink<Void>("diff", CommitDiffPage.class,
								WicketUtils.newObjectParameter(commit.repository, commit.getName()))
								.setEnabled(commit.getParentCount() > 0));
						commitItem.add(new BookmarkablePageLink<Void>("tree", TreePage.class,
								WicketUtils.newObjectParameter(commit.repository, commit.getName())));
					}
				};
				activityItem.add(commits);
			}
		};
		add(activityView);
	}
}
