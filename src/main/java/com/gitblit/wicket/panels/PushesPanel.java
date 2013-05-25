/*
 * Copyright 2013 gitblit.com.
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
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.StringResourceModel;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.Constants;
import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.PushLogEntry;
import com.gitblit.models.RepositoryCommit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.PushLogUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.ComparePage;
import com.gitblit.wicket.pages.PushesPage;
import com.gitblit.wicket.pages.SummaryPage;
import com.gitblit.wicket.pages.TagPage;
import com.gitblit.wicket.pages.TreePage;
import com.gitblit.wicket.pages.UserPage;

public class PushesPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	private final boolean hasPushes;
	
	private boolean hasMore;

	public PushesPanel(String wicketId, final RepositoryModel model, Repository r, int limit, int pageOffset) {
		super(wicketId);
		boolean pageResults = limit <= 0;
		int pushesPerPage = GitBlit.getInteger(Keys.web.pushesPerPage, 10);
		if (pushesPerPage <= 1) {
			pushesPerPage = 10;
		}

		final int hashLen = GitBlit.getInteger(Keys.web.shortCommitIdLength, 6);
		List<PushLogEntry> pushes;
		if (pageResults) {
			pushes = PushLogUtils.getPushLogByRef(model.name, r, pageOffset * pushesPerPage, pushesPerPage);
		} else {
			pushes = PushLogUtils.getPushLogByRef(model.name, r, limit);
		}
		
		// inaccurate way to determine if there are more commits.
		// works unless commits.size() represents the exact end.
		hasMore = pushes.size() >= pushesPerPage;

		hasPushes = pushes.size() > 0;

		ListDataProvider<PushLogEntry> dp = new ListDataProvider<PushLogEntry>(pushes);
		DataView<PushLogEntry> pushView = new DataView<PushLogEntry>("push", dp) {
			private static final long serialVersionUID = 1L;

			public void populateItem(final Item<PushLogEntry> pushItem) {
				final PushLogEntry push = pushItem.getModelObject();
				String fullRefName = push.getChangedRefs().get(0);
				String shortRefName = fullRefName;
				boolean isTag = false;
				if (shortRefName.startsWith(org.eclipse.jgit.lib.Constants.R_HEADS)) {
					shortRefName = shortRefName.substring(org.eclipse.jgit.lib.Constants.R_HEADS.length());
				} else if (shortRefName.startsWith(org.eclipse.jgit.lib.Constants.R_TAGS)) {
					shortRefName = shortRefName.substring(org.eclipse.jgit.lib.Constants.R_TAGS.length());
					isTag = true;
				}
				
				pushItem.add(WicketUtils.createDateLabel("whenPushed", push.date, getTimeZone(), getTimeUtils()));
				Label pushIcon = new Label("pushIcon");
				if (isTag) {
					WicketUtils.setCssClass(pushIcon, "iconic-tag");
				} else {
					WicketUtils.setCssClass(pushIcon, "iconic-loop");
				}
				pushItem.add(pushIcon);
				if (push.user.username.equals(push.user.emailAddress) && push.user.emailAddress.indexOf('@') > -1) {
					// username is an email address - 1.2.1 push log bug
					pushItem.add(new Label("whoPushed", push.user.getDisplayName()));
				} else {
					// link to user acount page
					pushItem.add(new LinkPanel("whoPushed", null, push.user.getDisplayName(),
						UserPage.class, WicketUtils.newUsernameParameter(push.user.username)));
				}
				
				String preposition = "gb.at";
				boolean isDelete = false;
				boolean isRewind = false;
				String what;
				switch(push.getChangeType(fullRefName)) {
				case CREATE:
					if (isTag) {
						what = getString("gb.pushedNewTag");
					} else {
						what = getString("gb.pushedNewBranch");
					}
					preposition = "gb.to";
					break;
				case DELETE:
					isDelete = true;
					if (isTag) {
						what = getString("gb.deletedTag");
					} else {
						what = getString("gb.deletedBranch");
					}
					preposition = "gb.from";
					break;
				case UPDATE_NONFASTFORWARD:
					isRewind = true;
				default:
					what = MessageFormat.format(push.getCommitCount() > 1 ? getString("gb.pushedNCommitsTo") : getString("gb.pushedOneCommitTo") , push.getCommitCount());
					break;
				}
				pushItem.add(new Label("whatPushed", what));
				
				pushItem.add(new Label("refRewind", getString("gb.rewind")).setVisible(isRewind));
				
				if (isDelete) {
					// can't link to deleted ref
					pushItem.add(new Label("refPushed", shortRefName));
				} else if (isTag) {
					// link to tag
					pushItem.add(new LinkPanel("refPushed", null, shortRefName,
							TagPage.class, WicketUtils.newObjectParameter(model.name, fullRefName)));
				} else {
					// link to tree
					pushItem.add(new LinkPanel("refPushed", null, shortRefName,
						TreePage.class, WicketUtils.newObjectParameter(model.name, fullRefName)));
				}
				
				// to/from/etc
				pushItem.add(new Label("repoPreposition", getString(preposition)));
				
				String repoName = StringUtils.stripDotGit(model.name);
				pushItem.add(new LinkPanel("repoPushed", null, repoName,
						SummaryPage.class, WicketUtils.newRepositoryParameter(model.name)));

				int maxCommitCount = 5;
				List<RepositoryCommit> commits = push.getCommits();
				if (commits.size() > maxCommitCount) {
					commits = new ArrayList<RepositoryCommit>(commits.subList(0,  maxCommitCount));					
				}
				
				// compare link
				String compareLinkText = null;
				if ((push.getCommitCount() <= maxCommitCount) && (push.getCommitCount() > 1)) {
					compareLinkText = MessageFormat.format(getString("gb.viewComparison"), commits.size());
				} else if (push.getCommitCount() > maxCommitCount) {
					int diff = push.getCommitCount() - maxCommitCount;
					compareLinkText = MessageFormat.format(diff > 1 ? getString("gb.nMoreCommits") : getString("gb.oneMoreCommit"), diff);
				}
				if (StringUtils.isEmpty(compareLinkText)) {
					pushItem.add(new Label("compareLink").setVisible(false));
				} else {
					String endRangeId = push.getNewId(fullRefName);
					String startRangeId = push.getOldId(fullRefName);
					pushItem.add(new LinkPanel("compareLink", null, compareLinkText, ComparePage.class, WicketUtils.newRangeParameter(push.repository, startRangeId, endRangeId)));
				}
				
				ListDataProvider<RepositoryCommit> cdp = new ListDataProvider<RepositoryCommit>(commits);
				DataView<RepositoryCommit> commitsView = new DataView<RepositoryCommit>("commit", cdp) {
					private static final long serialVersionUID = 1L;

					public void populateItem(final Item<RepositoryCommit> commitItem) {
						final RepositoryCommit commit = commitItem.getModelObject();

						// author gravatar
						commitItem.add(new GravatarImage("commitAuthor", commit.getAuthorIdent().getName(),
								commit.getAuthorIdent().getEmailAddress(), null, 16, false, false));
						
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

						// commit hash link
						LinkPanel commitHash = new LinkPanel("hashLink", null, commit.getName().substring(0, hashLen),
								CommitPage.class, WicketUtils.newObjectParameter(
										model.name, commit.getName()));
						WicketUtils.setCssClass(commitHash, "shortsha1");
						WicketUtils.setHtmlTooltip(commitHash, commit.getName());
						commitItem.add(commitHash);
					}
				};
				
				pushItem.add(commitsView);
			}
		};
		add(pushView);

		// determine to show pager, more, or neither
		if (limit <= 0) {
			// no display limit
			add(new Label("morePushes").setVisible(false));
		} else {
			if (pageResults) {
				// paging
				add(new Label("morePushes").setVisible(false));
			} else {
				// more
				if (pushes.size() == limit) {
					// show more
					add(new LinkPanel("morePushes", "link", new StringResourceModel("gb.morePushes",
							this, null), PushesPage.class,
							WicketUtils.newRepositoryParameter(model.name)));
				} else {
					// no more
					add(new Label("morePushes").setVisible(false));
				}
			}
		}
	}

	public boolean hasMore() {
		return hasMore;
	}
	
	public boolean hideIfEmpty() {
		setVisible(hasPushes);
		return hasPushes;
	}
}
