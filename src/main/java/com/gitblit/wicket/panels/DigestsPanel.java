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

import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.PersonIdent;

import com.gitblit.Constants;
import com.gitblit.Keys;
import com.gitblit.models.DailyLogEntry;
import com.gitblit.models.RepositoryCommit;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.ComparePage;
import com.gitblit.wicket.pages.SummaryPage;
import com.gitblit.wicket.pages.TagPage;
import com.gitblit.wicket.pages.TicketsPage;
import com.gitblit.wicket.pages.TreePage;

public class DigestsPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	private final boolean hasChanges;

	private boolean hasMore;

	public DigestsPanel(String wicketId, List<DailyLogEntry> digests) {
		super(wicketId);
		hasChanges = digests.size() > 0;

		ListDataProvider<DailyLogEntry> dp = new ListDataProvider<DailyLogEntry>(digests);
		DataView<DailyLogEntry> pushView = new DataView<DailyLogEntry>("change", dp) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<DailyLogEntry> logItem) {
				final DailyLogEntry change = logItem.getModelObject();

				String dateFormat = app().settings().getString(Keys.web.datestampLongFormat, "EEEE, MMMM d, yyyy");
				TimeZone timezone = getTimeZone();
				DateFormat df = new SimpleDateFormat(dateFormat);
				df.setTimeZone(timezone);

				String fullRefName = change.getChangedRefs().get(0);
				String shortRefName = fullRefName;
				String ticketId = "";
				boolean isTag = false;
				boolean isTicket = false;
				if (shortRefName.startsWith(Constants.R_TICKET)) {
					ticketId = shortRefName = shortRefName.substring(Constants.R_TICKET.length());
					shortRefName = MessageFormat.format(getString("gb.ticketN"), ticketId);
					isTicket = true;
				} else if (shortRefName.startsWith(Constants.R_HEADS)) {
					shortRefName = shortRefName.substring(Constants.R_HEADS.length());
				} else if (shortRefName.startsWith(Constants.R_TAGS)) {
					shortRefName = shortRefName.substring(Constants.R_TAGS.length());
					isTag = true;
				}

				String fuzzydate;
				TimeUtils tu = getTimeUtils();
				Date pushDate = change.date;
				if (TimeUtils.isToday(pushDate, timezone)) {
					fuzzydate = tu.today();
				} else if (TimeUtils.isYesterday(pushDate, timezone)) {
					fuzzydate = tu.yesterday();
				} else {
					fuzzydate = getTimeUtils().timeAgo(pushDate);
				}
				logItem.add(new Label("whenChanged", fuzzydate + ", " + df.format(pushDate)));

				Label changeIcon = new Label("changeIcon");
				// use the repository hash color to differentiate the icon.
                String color = StringUtils.getColor(StringUtils.stripDotGit(change.repository));
                WicketUtils.setCssStyle(changeIcon, "color: " + color);

				if (isTag) {
					WicketUtils.setCssClass(changeIcon, "iconic-tag");
				} else if (isTicket) {
					WicketUtils.setCssClass(changeIcon, "fa fa-ticket");
				} else {
					WicketUtils.setCssClass(changeIcon, "iconic-loop");
				}
				logItem.add(changeIcon);

                if (isTag) {
                	// tags are special
                	PersonIdent ident = change.getCommits().get(0).getAuthorIdent();
                	if (!StringUtils.isEmpty(ident.getName())) {
                		logItem.add(new Label("whoChanged", ident.getName()));
                	} else {
                		logItem.add(new Label("whoChanged", ident.getEmailAddress()));
                	}
                } else {
                	logItem.add(new Label("whoChanged").setVisible(false));
                }

				String preposition = "gb.of";
				boolean isDelete = false;
				String what;
				String by = null;
				switch(change.getChangeType(fullRefName)) {
				case CREATE:
					if (isTag) {
						// new tag
						what = getString("gb.createdNewTag");
						preposition = "gb.in";
					} else {
						// new branch
						what = getString("gb.createdNewBranch");
						preposition = "gb.in";
					}
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
				default:
					what = MessageFormat.format(change.getCommitCount() > 1 ? getString("gb.commitsTo") : getString("gb.oneCommitTo"), change.getCommitCount());

					if (change.getAuthorCount() == 1) {
						by = MessageFormat.format(getString("gb.byOneAuthor"), change.getAuthorIdent().getName());
					} else {
						by = MessageFormat.format(getString("gb.byNAuthors"), change.getAuthorCount());
					}
					break;
				}
				logItem.add(new Label("whatChanged", what));
				logItem.add(new Label("byAuthors", by).setVisible(!StringUtils.isEmpty(by)));

				if (isDelete) {
					// can't link to deleted ref
					logItem.add(new Label("refChanged", shortRefName));
				} else if (isTag) {
					// link to tag
					logItem.add(new LinkPanel("refChanged", null, shortRefName,
							TagPage.class, WicketUtils.newObjectParameter(change.repository, shortRefName)));
				} else if (isTicket) {
					// link to ticket
					logItem.add(new LinkPanel("refChanged", null, shortRefName,
							TicketsPage.class, WicketUtils.newObjectParameter(change.repository, ticketId)));
				} else {
					// link to tree
					logItem.add(new LinkPanel("refChanged", null, shortRefName,
						TreePage.class, WicketUtils.newObjectParameter(change.repository, shortRefName)));
				}

				// to/from/etc
				logItem.add(new Label("repoPreposition", getString(preposition)));
				String repoName = StringUtils.stripDotGit(change.repository);
				logItem.add(new LinkPanel("repoChanged", null, repoName,
						SummaryPage.class, WicketUtils.newRepositoryParameter(change.repository)));

				int maxCommitCount = 5;
				List<RepositoryCommit> commits = change.getCommits();
				if (commits.size() > maxCommitCount) {
					commits = new ArrayList<RepositoryCommit>(commits.subList(0,  maxCommitCount));
				}

				// compare link
				String compareLinkText = null;
				if ((change.getCommitCount() <= maxCommitCount) && (change.getCommitCount() > 1)) {
					compareLinkText = MessageFormat.format(getString("gb.viewComparison"), commits.size());
				} else if (change.getCommitCount() > maxCommitCount) {
					int diff = change.getCommitCount() - maxCommitCount;
					compareLinkText = MessageFormat.format(diff > 1 ? getString("gb.nMoreCommits") : getString("gb.oneMoreCommit"), diff);
				}
				if (StringUtils.isEmpty(compareLinkText)) {
					logItem.add(new Label("compareLink").setVisible(false));
				} else {
					String endRangeId = change.getNewId(fullRefName);
					String startRangeId = change.getOldId(fullRefName);
					logItem.add(new LinkPanel("compareLink", null, compareLinkText, ComparePage.class, WicketUtils.newRangeParameter(change.repository, startRangeId, endRangeId)));
				}

				final boolean showSwatch = app().settings().getBoolean(Keys.web.repositoryListSwatches, true);

				ListDataProvider<RepositoryCommit> cdp = new ListDataProvider<RepositoryCommit>(commits);
				DataView<RepositoryCommit> commitsView = new DataView<RepositoryCommit>("commit", cdp) {
					private static final long serialVersionUID = 1L;

					@Override
					public void populateItem(final Item<RepositoryCommit> commitItem) {
						final RepositoryCommit commit = commitItem.getModelObject();

						// author gravatar
						commitItem.add(new AvatarImage("commitAuthor", commit.getAuthorIdent(), null, 16, false));

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
										change.repository, commit.getName()));
						if (!shortMessage.equals(trimmedMessage)) {
							WicketUtils.setHtmlTooltip(shortlog, shortMessage);
						}
						commitItem.add(shortlog);

						// commit hash link
						int hashLen = app().settings().getInteger(Keys.web.shortCommitIdLength, 6);
						LinkPanel commitHash = new LinkPanel("hashLink", null, commit.getName().substring(0, hashLen),
								CommitPage.class, WicketUtils.newObjectParameter(
										change.repository, commit.getName()));
						WicketUtils.setCssClass(commitHash, "shortsha1");
						WicketUtils.setHtmlTooltip(commitHash, commit.getName());
						commitItem.add(commitHash);

						if (showSwatch) {
							// set repository color
							String color = StringUtils.getColor(StringUtils.stripDotGit(change.repository));
							WicketUtils.setCssStyle(commitItem, MessageFormat.format("border-left: 2px solid {0};", color));
						}
					}
				};

				logItem.add(commitsView);
			}
		};

		add(pushView);
	}

	public boolean hasMore() {
		return hasMore;
	}

	public boolean hideIfEmpty() {
		setVisible(hasChanges);
		return hasChanges;
	}
}
