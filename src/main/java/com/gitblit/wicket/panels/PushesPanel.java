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

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.Constants;
import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.PushLogEntry;
import com.gitblit.models.RepositoryCommit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.PushLogUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.GitSearchPage;
import com.gitblit.wicket.pages.SummaryPage;
import com.gitblit.wicket.pages.UserPage;

public class PushesPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	private final boolean hasPushes;
	
	private boolean hasMore;

	public PushesPanel(String wicketId, final RepositoryModel model, Repository r, int limit, int pageOffset) {
		super(wicketId);
		boolean pageResults = limit <= 0;
		int itemsPerPage = GitBlit.getInteger(Keys.web.itemsPerPage, 50);
		if (itemsPerPage <= 1) {
			itemsPerPage = 50;
		}

		final Map<String, String> usernameLookup = new HashMap<String, String>();
		final int hashLen = GitBlit.getInteger(Keys.web.shortCommitIdLength, 6);
		List<PushLogEntry> entries = PushLogUtils.getPushLog(model.name, r, limit);
		// establish pusher identities
		for (PushLogEntry push : entries) {
			// handle push logs with email address instead of account name
			String username = push.user.username;
			if (push.user.username.indexOf('@') > -1) {
				// push username is an email address, reverse lookup for account
				if (!usernameLookup.containsKey(push.user.username)) {
					for (UserModel user : GitBlit.self().getAllUsers()) {
						if (push.user.username.equals(user.emailAddress)) {
							username = user.username;
							usernameLookup.put(push.user.username, username);
							break;
						}
					}
				} else {
					username = usernameLookup.get(push.user.username);
				}
			} else {
				// push username is an account name, lookup for email address
				if (!usernameLookup.containsKey(push.user.username)) {
					UserModel user = GitBlit.self().getUserModel(push.user.username);
					if (user != null) {
						push.user.emailAddress = user.emailAddress;
						usernameLookup.put(push.user.username, user.emailAddress);
					}
				} else {
					push.user.emailAddress = usernameLookup.get(push.user.username);
				}
			}
		}
		
		hasPushes = entries.size() > 0;
		
		ListDataProvider<PushLogEntry> dp = new ListDataProvider<PushLogEntry>(entries);
		DataView<PushLogEntry> pushView = new DataView<PushLogEntry>("push", dp) {
			private static final long serialVersionUID = 1L;

			public void populateItem(final Item<PushLogEntry> pushItem) {
				final PushLogEntry push = pushItem.getModelObject();
				
				
				pushItem.add(new GravatarImage("whoAvatar", push.getCommitterIdent(), 40));
				pushItem.add(new LinkPanel("whoPushed", null, push.user.getDisplayName(),
						UserPage.class, WicketUtils.newUsernameParameter(push.user.username)));
				pushItem.add(new Label("whatPushed", 
						MessageFormat.format(push.getCommitCount() > 1 ? "pushed {0} commits to":"pushed 1 commit to", push.getCommitCount())));
				String repoName = StringUtils.stripDotGit(model.name);
				pushItem.add(new LinkPanel("wherePushed", null, repoName,
						SummaryPage.class, WicketUtils.newRepositoryParameter(model.name)));
				pushItem.add(WicketUtils.createDateLabel("whenPushed", push.date, getTimeZone(), getTimeUtils()));

				ListDataProvider<RepositoryCommit> cdp = new ListDataProvider<RepositoryCommit>(push.getCommits());
				DataView<RepositoryCommit> commitsView = new DataView<RepositoryCommit>("commit", cdp) {
					private static final long serialVersionUID = 1L;

					public void populateItem(final Item<RepositoryCommit> commitItem) {
						final RepositoryCommit commit = commitItem.getModelObject();

						// author search link
						String author = commit.getAuthorIdent().getName();
						LinkPanel authorLink = new LinkPanel("commitAuthor", "list", author,
								GitSearchPage.class, WicketUtils.newSearchParameter(model.name,
										null, author, Constants.SearchType.AUTHOR));
						setPersonSearchTooltip(authorLink, author, Constants.SearchType.AUTHOR);
						commitItem.add(authorLink);
						
						// merge icon
						if (commit.getParentCount() > 1) {
							commitItem.add(WicketUtils.newImage("commitIcon", "commit_merge_16x16.png"));
						} else {
							commitItem.add(WicketUtils.newBlankImage("commitIcon"));
						}

						// short message
						String shortMessage = commit.getShortMessage();
						String trimmedMessage = shortMessage;
						if (commit.getRefs() != null && commit.getRefs().size() > 0) {
							trimmedMessage = StringUtils.trimString(shortMessage, Constants.LEN_SHORTLOG_REFS);
						} else {
							trimmedMessage = StringUtils.trimString(shortMessage, Constants.LEN_SHORTLOG);
						}
						LinkPanel shortlog = new LinkPanel("commitShortMessage", "list",
								trimmedMessage, CommitPage.class, WicketUtils.newObjectParameter(
										model.name, commit.getName()));
						if (!shortMessage.equals(trimmedMessage)) {
							WicketUtils.setHtmlTooltip(shortlog, shortMessage);
						}
						commitItem.add(shortlog);

						commitItem.add(new RefsPanel("commitRefs", commit.repository, commit.getRefs()));

						// commit hash link
						LinkPanel commitHash = new LinkPanel("hashLink", null, commit.getName().substring(0, hashLen),
								CommitPage.class, WicketUtils.newObjectParameter(
										model.name, commit.getName()));
						WicketUtils.setCssClass(commitHash, "shortsha1");
						WicketUtils.setHtmlTooltip(commitHash, commit.getName());
						commitItem.add(commitHash);
						
//						item.add(new BookmarkablePageLink<Void>("diff", CommitDiffPage.class, WicketUtils
//								.newObjectParameter(repositoryName, entry.getName())).setEnabled(entry
//								.getParentCount() > 0));
//						item.add(new BookmarkablePageLink<Void>("tree", TreePage.class, WicketUtils
//								.newObjectParameter(repositoryName, entry.getName())));
					}
				};
				
				pushItem.add(commitsView);
			}
		};
		add(pushView);

		// determine to show pager, more, or neither
//		if (limit <= 0) {
//			// no display limit
//			add(new Label("moreLogs", "").setVisible(false));
//		} else {
//			if (pageResults) {
//				// paging
//				add(new Label("moreLogs", "").setVisible(false));
//			} else {
//				// more
//				if (commits.size() == limit) {
//					// show more
//					add(new LinkPanel("moreLogs", "link", new StringResourceModel("gb.moreLogs",
//							this, null), LogPage.class,
//							WicketUtils.newRepositoryParameter(repositoryName)));
//				} else {
//					// no more
//					add(new Label("moreLogs", "").setVisible(false));
//				}
//			}
//		}
	}

	public boolean hasMore() {
		return hasMore;
	}
	
	public boolean hideIfEmpty() {
		setVisible(hasPushes);
		return hasPushes;
	}
}
