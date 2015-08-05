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
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.StringResourceModel;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand.Type;

import com.gitblit.Constants;
import com.gitblit.Keys;
import com.gitblit.models.RefLogEntry;
import com.gitblit.models.RepositoryCommit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.RefLogUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.ComparePage;
import com.gitblit.wicket.pages.ReflogPage;
import com.gitblit.wicket.pages.TagPage;
import com.gitblit.wicket.pages.TicketsPage;
import com.gitblit.wicket.pages.TreePage;
import com.gitblit.wicket.pages.UserPage;

public class ReflogPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	private final boolean hasChanges;

	private boolean hasMore;

	public ReflogPanel(String wicketId, final RepositoryModel model, Repository r, int limit, int pageOffset) {
		super(wicketId);
		boolean pageResults = limit <= 0;
		int changesPerPage = app().settings().getInteger(Keys.web.reflogChangesPerPage, 10);
		if (changesPerPage <= 1) {
			changesPerPage = 10;
		}

		List<RefLogEntry> changes;
		if (pageResults) {
			changes = RefLogUtils.getLogByRef(model.name, r, pageOffset * changesPerPage, changesPerPage);
		} else {
			changes = RefLogUtils.getLogByRef(model.name, r, limit);
		}

		// inaccurate way to determine if there are more commits.
		// works unless commits.size() represents the exact end.
		hasMore = changes.size() >= changesPerPage;
		hasChanges = changes.size() > 0;

		setup(changes);

		// determine to show pager, more, or neither
		if (limit <= 0) {
			// no display limit
			add(new Label("moreChanges").setVisible(false));
		} else {
			if (pageResults) {
				// paging
				add(new Label("moreChanges").setVisible(false));
			} else {
				// more
				if (changes.size() == limit) {
					// show more
					add(new LinkPanel("moreChanges", "link", new StringResourceModel("gb.moreChanges",
							this, null), ReflogPage.class,
							WicketUtils.newRepositoryParameter(model.name)));
				} else {
					// no more
					add(new Label("moreChanges").setVisible(false));
				}
			}
		}
	}

	public ReflogPanel(String wicketId, List<RefLogEntry> changes) {
		super(wicketId);
		hasChanges = changes.size() > 0;
		setup(changes);
		add(new Label("moreChanges").setVisible(false));
	}

	protected void setup(List<RefLogEntry> changes) {

		ListDataProvider<RefLogEntry> dp = new ListDataProvider<RefLogEntry>(changes);
		DataView<RefLogEntry> changeView = new DataView<RefLogEntry>("change", dp) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<RefLogEntry> changeItem) {
				final RefLogEntry change = changeItem.getModelObject();

				String dateFormat = app().settings().getString(Keys.web.datetimestampLongFormat, "EEEE, MMMM d, yyyy HH:mm Z");
				TimeZone timezone = getTimeZone();
				DateFormat df = new SimpleDateFormat(dateFormat);
				df.setTimeZone(timezone);
				Calendar cal = Calendar.getInstance(timezone);

				String fullRefName = change.getChangedRefs().get(0);
				String shortRefName = fullRefName;
				String ticketId = null;
				boolean isTag = false;
				boolean isTicket = false;
				if (shortRefName.startsWith(Constants.R_TICKET)) {
					ticketId = fullRefName.substring(Constants.R_TICKET.length());
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
				Date changeDate = change.date;
				if (TimeUtils.isToday(changeDate, timezone)) {
					fuzzydate = tu.today();
				} else if (TimeUtils.isYesterday(changeDate, timezone)) {
					fuzzydate = tu.yesterday();
				} else {
					// calculate a fuzzy time ago date
                	cal.setTime(changeDate);
                	cal.set(Calendar.HOUR_OF_DAY, 0);
                	cal.set(Calendar.MINUTE, 0);
                	cal.set(Calendar.SECOND, 0);
                	cal.set(Calendar.MILLISECOND, 0);
                	Date date = cal.getTime();
					fuzzydate = getTimeUtils().timeAgo(date);
				}
				changeItem.add(new Label("whenChanged", fuzzydate + ", " + df.format(changeDate)));

				Label changeIcon = new Label("changeIcon");
				if (Type.DELETE.equals(change.getChangeType(fullRefName))) {
					WicketUtils.setCssClass(changeIcon, "iconic-trash-stroke");
				} else if (isTag) {
					WicketUtils.setCssClass(changeIcon, "iconic-tag");
				} else if (isTicket) {
					WicketUtils.setCssClass(changeIcon, "fa fa-ticket");
				} else {
					WicketUtils.setCssClass(changeIcon, "iconic-upload");
				}
				changeItem.add(changeIcon);

				if (change.user.username.equals(change.user.emailAddress) && change.user.emailAddress.indexOf('@') > -1) {
					// username is an email address - 1.2.1 push log bug
					changeItem.add(new Label("whoChanged", change.user.getDisplayName()));
				} else if (change.user.username.equals(UserModel.ANONYMOUS.username)) {
					// anonymous change
					changeItem.add(new Label("whoChanged", getString("gb.anonymousUser")));
				} else {
					// link to user account page
					changeItem.add(new LinkPanel("whoChanged", null, change.user.getDisplayName(),
							UserPage.class, WicketUtils.newUsernameParameter(change.user.username)));
				}

				boolean isDelete = false;
				boolean isRewind = false;
				String what;
				String by = null;
				switch(change.getChangeType(fullRefName)) {
				case CREATE:
					if (isTag) {
						// new tag
						what = getString("gb.pushedNewTag");
					} else {
						// new branch
						what = getString("gb.pushedNewBranch");
					}
					break;
				case DELETE:
					isDelete = true;
					if (isTag) {
						what = getString("gb.deletedTag");
					} else {
						what = getString("gb.deletedBranch");
					}
					break;
				case UPDATE_NONFASTFORWARD:
					isRewind = true;
				default:
					what = MessageFormat.format(change.getCommitCount() > 1 ? getString("gb.pushedNCommitsTo") : getString("gb.pushedOneCommitTo"), change.getCommitCount());

					if (change.getAuthorCount() == 1) {
						by = MessageFormat.format(getString("gb.byOneAuthor"), change.getAuthorIdent().getName());
					} else {
						by = MessageFormat.format(getString("gb.byNAuthors"), change.getAuthorCount());
					}
					break;
				}
				changeItem.add(new Label("whatChanged", what));
				changeItem.add(new Label("byAuthors", by).setVisible(!StringUtils.isEmpty(by)));
				changeItem.add(new Label("refRewind", getString("gb.rewind")).setVisible(isRewind));

				if (isDelete) {
					// can't link to deleted ref
					changeItem.add(new Label("refChanged", shortRefName));
				} else if (isTag) {
					// link to tag
					changeItem.add(new LinkPanel("refChanged", null, shortRefName,
							TagPage.class, WicketUtils.newObjectParameter(change.repository, fullRefName)));
				} else if (isTicket) {
					// link to ticket
					changeItem.add(new LinkPanel("refChanged", null, shortRefName,
							TicketsPage.class, WicketUtils.newObjectParameter(change.repository, ticketId)));
				} else {
					// link to tree
					changeItem.add(new LinkPanel("refChanged", null, shortRefName,
						TreePage.class, WicketUtils.newObjectParameter(change.repository, fullRefName)));
				}

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
					changeItem.add(new Label("compareLink").setVisible(false));
				} else {
					String endRangeId = change.getNewId(fullRefName);
					String startRangeId = change.getOldId(fullRefName);
					changeItem.add(new LinkPanel("compareLink", null, compareLinkText, ComparePage.class, WicketUtils.newRangeParameter(change.repository, startRangeId, endRangeId)));
				}

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
					}
				};

				changeItem.add(commitsView);
			}
		};

		add(changeView);
	}

	public boolean hasMore() {
		return hasMore;
	}

	public boolean hideIfEmpty() {
		setVisible(hasChanges);
		return hasChanges;
	}
}
