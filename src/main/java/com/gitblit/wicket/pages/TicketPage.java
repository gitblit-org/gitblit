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
package com.gitblit.wicket.pages;

import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.PageParameters;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.IBehavior;
import org.apache.wicket.behavior.SimpleAttributeModifier;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.image.ContextImage;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.Model;
import org.apache.wicket.protocol.http.WebRequest;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.URIish;

import com.gitblit.Constants;
import com.gitblit.Keys;
import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.SubmoduleModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.TicketModel.CommentSource;
import com.gitblit.models.TicketModel.Field;
import com.gitblit.models.TicketModel.Patchset;
import com.gitblit.models.TicketModel.PatchsetType;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.models.UserModel;
import com.gitblit.tickets.TicketIndexer.Lucene;
import com.gitblit.tickets.TicketLabel;
import com.gitblit.tickets.TicketMilestone;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.MarkdownUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.freemarker.FreemarkerPanel;
import com.gitblit.wicket.panels.BasePanel.JavascriptTextPrompt;
import com.gitblit.wicket.panels.CommentPanel;
import com.gitblit.wicket.panels.DiffStatPanel;
import com.gitblit.wicket.panels.GravatarImage;
import com.gitblit.wicket.panels.LinkPanel;
import com.gitblit.wicket.panels.ShockWaveComponent;
import com.gitblit.wicket.panels.SimpleAjaxLink;

public class TicketPage extends TicketBasePage {

	final int avatarWidth = 40;

	final TicketModel ticket;

	public TicketPage(PageParameters params) {
		super(params);

		final UserModel user = GitBlitWebSession.get().getUser() == null ? UserModel.ANONYMOUS : GitBlitWebSession.get().getUser();
		final boolean isAuthenticated = !UserModel.ANONYMOUS.equals(user) && user.isAuthenticated;
		final RepositoryModel repository = getRepositoryModel();
		final String id = WicketUtils.getObject(params);
		if (app().tickets().isValidChangeId(id)) {
			ticket = app().tickets().getTicket(repository.name, id);
		} else {
			int issueId = Integer.parseInt(id);
			ticket = app().tickets().getTicket(repository.name, issueId);
		}

		if (ticket == null) {
			// ticket not found
			throw new RestartResponseException(TicketsPage.class, WicketUtils.newRepositoryParameter(repositoryName));
		}

		final List<Change> revisions = new ArrayList<Change>();
		List<Change> comments = new ArrayList<Change>();
		List<Change> statusChanges = new ArrayList<Change>();
		List<Change> discussion = new ArrayList<Change>();
		for (Change change : ticket.changes) {
			if (change.hasComment() || (change.isStatusChange() && (change.getStatus() != Status.New))) {
				discussion.add(change);
			}
			if (change.hasComment()) {
				comments.add(change);
			}
			if (change.hasPatchset()) {
				revisions.add(change);
			}
			if (change.isStatusChange() && !change.hasPatchset()) {
				statusChanges.add(change);
			}
		}

		final Change currentRevision = revisions.isEmpty() ? null : revisions.get(revisions.size() - 1);
		final Patchset currentPatchset = ticket.getCurrentPatchset();

		/*
		 * TICKET HEADER
		 */
		String href = urlFor(TicketsPage.class, params).toString();
		add(new ExternalLink("ticketNumber", href, "#" + ticket.number));
		Label headerStatus = new Label("headerStatus", ticket.status.toString());
		WicketUtils.setCssClass(headerStatus, getLozengeClass(ticket.status, false));
		add(headerStatus);
		add(new Label("ticketTitle", ticket.title));
		if (currentPatchset == null) {
			add(new Label("diffstat").setVisible(false));
		} else {
			add(new DiffStatPanel("diffstat", currentPatchset.insertions, currentPatchset.deletions));
		}


		/*
		 * TAB TITLES
		 */
		add(new Label("commentCount", "" + comments.size()).setVisible(!comments.isEmpty()));
		add(new Label("commitCount", "" + (currentPatchset == null ? 0 : currentPatchset.totalCommits)).setVisible(currentPatchset != null));


		/*
		 * TICKET AUTHOR and DATE (DISCUSSION TAB)
		 */
		UserModel createdBy = app().users().getUserModel(ticket.createdBy);
		if (createdBy == null) {
			add(new Label("whoCreated", ticket.createdBy));
		} else {
			add(new LinkPanel("whoCreated", null, createdBy.getDisplayName(),
					UserPage.class, WicketUtils.newUsernameParameter(createdBy.username)));
		}

		if (ticket.isProposal()) {
			// clearly indicate this is a change ticket
			add(new Label("creationMessage", getString("gb.proposedThisChange")));
		} else {
			// standard ticket
			add(new Label("creationMessage", getString("gb.createdThisTicket")));
		}

		String dateFormat = app().settings().getString(Keys.web.datestampLongFormat, "EEEE, MMMM d, yyyy");
		String timestampFormat = app().settings().getString(Keys.web.datetimestampLongFormat, "EEEE, MMMM d, yyyy");
		final TimeZone timezone = getTimeZone();
		final DateFormat df = new SimpleDateFormat(dateFormat);
		df.setTimeZone(timezone);
		final DateFormat tsf = new SimpleDateFormat(timestampFormat);
		tsf.setTimeZone(timezone);
		final Calendar cal = Calendar.getInstance(timezone);

		String fuzzydate;
		TimeUtils tu = getTimeUtils();
		Date createdDate = ticket.createdAt;
		if (TimeUtils.isToday(createdDate, timezone)) {
			fuzzydate = tu.today();
		} else if (TimeUtils.isYesterday(createdDate, timezone)) {
			fuzzydate = tu.yesterday();
		} else {
			// calculate a fuzzy time ago date
        	cal.setTime(createdDate);
        	cal.set(Calendar.HOUR_OF_DAY, 0);
        	cal.set(Calendar.MINUTE, 0);
        	cal.set(Calendar.SECOND, 0);
        	cal.set(Calendar.MILLISECOND, 0);
        	createdDate = cal.getTime();
			fuzzydate = getTimeUtils().timeAgo(createdDate);
		}
		Label when = new Label("whenCreated", fuzzydate + ", " + df.format(createdDate));
		WicketUtils.setHtmlTooltip(when, tsf.format(ticket.createdAt));
		add(when);

		String exportHref = urlFor(ExportTicketPage.class, params).toString();
		add(new ExternalLink("exportJson", exportHref, "json"));


		/*
		 * ASSIGNED TO (DISCUSSION TAB)
		 */
		if (StringUtils.isEmpty(ticket.assignedTo)) {
			add(new Label("assignedTo"));
		} else {
			UserModel assignee = app().users().getUserModel(ticket.assignedTo);
			if (assignee == null) {
				add(new Label("assignedTo", ticket.assignedTo));
			} else {
				add(new LinkPanel("assignedTo", null, assignee.getDisplayName(),
						UserPage.class, WicketUtils.newUsernameParameter(assignee.username)));
			}
		}

		/*
		 * MILESTONE PROGRESS (DISCUSSION TAB)
		 */
		if (StringUtils.isEmpty(ticket.milestone)) {
			add(new Label("milestone"));
		} else {
			// link to milestone query
			TicketMilestone milestone = app().tickets().getMilestone(repositoryName, ticket.milestone);
			PageParameters milestoneParameters = new PageParameters();
			milestoneParameters.put("r", repositoryName);
			milestoneParameters.put(Lucene.milestone.name(), ticket.milestone);
			int progress = 0;
			int open = 0;
			int closed = 0;
			if (milestone != null) {
				progress = milestone.getProgress();
				open = milestone.getOpenTickets();
				closed = milestone.getClosedTickets();
			}

			Fragment milestoneProgress = new Fragment("milestone", "milestoneProgressFragment", this);
			milestoneProgress.add(new LinkPanel("link", null, ticket.milestone, TicketsPage.class, milestoneParameters));
			Label label = new Label("progress");
			WicketUtils.setCssStyle(label, "width:" + progress + "%;");
			milestoneProgress.add(label);
			WicketUtils.setHtmlTooltip(milestoneProgress, MessageFormat.format("{0} open, {1} closed", open, closed));
			add(milestoneProgress);
		}


		/*
		 * TICKET DESCRIPTION (DISCUSSION TAB)
		 */
		String desc;
		if (StringUtils.isEmpty(ticket.body)) {
			desc = getString("gb.noDescriptionGiven");
		} else {
			desc = MarkdownUtils.transformGFM(app().settings(), ticket.body, ticket.repository);
		}
		add(new Label("ticketDescription", desc).setEscapeModelStrings(false));


		/*
		 * PARTICIPANTS (DISCUSSION TAB)
		 */
		if (app().settings().getBoolean(Keys.web.allowGravatar, true)) {
			// gravatar allowed
			List<String> participants = ticket.getParticipants();
			add(new Label("participantsLabel", MessageFormat.format(getString(participants.size() > 1 ? "gb.nParticipants" : "gb.oneParticipant"),
					"<b>" + participants.size() + "</b>")).setEscapeModelStrings(false));
			ListDataProvider<String> participantsDp = new ListDataProvider<String>(participants);
			DataView<String> participantsView = new DataView<String>("participants", participantsDp) {
				private static final long serialVersionUID = 1L;

				@Override
				public void populateItem(final Item<String> item) {
					String username = item.getModelObject();
					UserModel user = app().users().getUserModel(username);
					if (user == null) {
						user = new UserModel(username);
					}
					item.add(new GravatarImage("participant", user.getDisplayName(),
							user.emailAddress, null, 25, false, true));
				}
			};
			add(participantsView);
		} else {
			// gravatar prohibited
			add(new Label("participantsLabel").setVisible(false));
			add(new Label("participants").setVisible(false));
		}


		/*
		 * LARGE STATUS INDICATOR WITH ICON (DISCUSSION TAB->SIDE BAR)
		 */
		Fragment ticketStatus = new Fragment("ticketStatus", "ticketStatusFragment", this);
		Label ticketIcon = new Label("ticketIcon");
		if (ticket.isProposal()) {
			WicketUtils.setCssClass(ticketIcon, "fa fa-code");
		} else if (ticket.isBug()) {
			WicketUtils.setCssClass(ticketIcon, "fa fa-bug");
		} else {
			WicketUtils.setCssClass(ticketIcon, "fa fa-ticket");
		}
		ticketStatus.add(ticketIcon);
		ticketStatus.add(new Label("ticketStatus", ticket.status.toString()));
		WicketUtils.setCssClass(ticketStatus, getLozengeClass(ticket.status, false));
		add(ticketStatus);


		/*
		 * UPDATE FORM (DISCUSSION TAB)
		 */
		if (isAuthenticated) {
			Fragment controls = new Fragment("controls", "controlsFragment", this);

			List<Status> choices = new ArrayList<Status>();
			if (ticket.isProposal()) {
				choices.addAll(Arrays.asList(TicketModel.Status.proposalWorkflow));
			} else if (ticket.isBug()) {
				choices.addAll(Arrays.asList(TicketModel.Status.bugWorkflow));
			} else {
				choices.addAll(Arrays.asList(TicketModel.Status.requestWorkflow));
			}
			choices.remove(ticket.status);

			ListDataProvider<Status> workflowDp = new ListDataProvider<Status>(choices);
			DataView<Status> workflowView = new DataView<Status>("newStatus", workflowDp) {
				private static final long serialVersionUID = 1L;

				@Override
				public void populateItem(final Item<Status> item) {
					SimpleAjaxLink<Status> link = new SimpleAjaxLink<Status>("link", item.getModel()) {

						private static final long serialVersionUID = 1L;

						@Override
						public void onClick(AjaxRequestTarget target) {
							Status status = getModel().getObject();
							Change change = new Change(user.username);
							change.setField(Field.status, status);
							if (!ticket.isWatching(user.username)) {
								change.watch(user.username);
							}
							TicketModel update = app().tickets().updateTicket(repositoryName, ticket.number, change);
							app().tickets().createNotifier().sendMailing(update);
							setResponsePage(TicketsPage.class, getPageParameters());
						}
					};
					String css = getStatusClass(item.getModel().getObject());
					WicketUtils.setCssClass(link, css);
					item.add(link);
				}
			};
			controls.add(workflowView);
			add(controls);
		} else {
			add(new Label("controls").setVisible(false));
		}


		/*
		 * TICKET METADATA
		 */
		add(new Label("ticketType", ticket.type.toString()));
		add(new Label("ticketTopic", ticket.topic == null ? "" : ticket.topic));


		/*
		 * VOTERS
		 */
		List<String> voters = ticket.getVoters();
		Label votersCount = new Label("votes", "" + voters.size());
		if (voters.size() == 0) {
			WicketUtils.setCssClass(votersCount, "badge");
		} else {
			WicketUtils.setCssClass(votersCount, "badge badge-info");
		}
		add(votersCount);
		if (user.isAuthenticated) {
			Model<String> model;
			if (ticket.isVoter(user.username)) {
				model = Model.of(getString("gb.removeVote"));
			} else {
				model = Model.of(MessageFormat.format(getString("gb.vote"), ticket.type.toString()));
			}
			SimpleAjaxLink<String> link = new SimpleAjaxLink<String>("voteLink", model) {

				private static final long serialVersionUID = 1L;

				@Override
				public void onClick(AjaxRequestTarget target) {
					Change change = new Change(user.username);
					if (ticket.isVoter(user.username)) {
						change.unvote(user.username);
					} else {
						change.vote(user.username);
					}
					app().tickets().updateTicket(repositoryName, ticket.number, change);
					setResponsePage(TicketsPage.class, getPageParameters());
				}
			};
			add(link);
		} else {
			add(new Label("voteLink").setVisible(false));
		}


		/*
		 * WATCHERS
		 */
		List<String> watchers = ticket.getWatchers();
		Label watchersCount = new Label("watchers", "" + watchers.size());
		if (watchers.size() == 0) {
			WicketUtils.setCssClass(watchersCount, "badge");
		} else {
			WicketUtils.setCssClass(watchersCount, "badge badge-info");
		}
		add(watchersCount);
		if (user.isAuthenticated) {
			Model<String> model;
			if (ticket.isWatching(user.username)) {
				model = Model.of(getString("gb.stopWatching"));
			} else {
				model = Model.of(MessageFormat.format(getString("gb.watch"), ticket.type.toString()));
			}
			SimpleAjaxLink<String> link = new SimpleAjaxLink<String>("watchLink", model) {

				private static final long serialVersionUID = 1L;

				@Override
				public void onClick(AjaxRequestTarget target) {
					Change change = new Change(user.username);
					if (ticket.isWatching(user.username)) {
						change.unwatch(user.username);
					} else {
						change.watch(user.username);
					}
					app().tickets().updateTicket(repositoryName, ticket.number, change);
					setResponsePage(TicketsPage.class, getPageParameters());
				}
			};
			add(link);
		} else {
			add(new Label("watchLink").setVisible(false));
		}


		/*
		 * TOPIC & LABELS (DISCUSSION TAB->SIDE BAR)
		 */
		ListDataProvider<String> labelsDp = new ListDataProvider<String>(ticket.getLabels());
		DataView<String> labelsView = new DataView<String>("labels", labelsDp) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<String> item) {
				final String value = item.getModelObject();
				Label label = new Label("label", value);
				TicketLabel tLabel = app().tickets().getLabel(repositoryName, value);
				String background = MessageFormat.format("background-color:{0};", tLabel.color);
				label.add(new SimpleAttributeModifier("style", background));
				item.add(label);
			}
		};

		add(labelsView);


		/*
		 * COMMENTS & STATUS CHANGES (DISCUSSION TAB)
		 */
		ListDataProvider<Change> discussionDp = new ListDataProvider<Change>(discussion);
		DataView<Change> discussionView = new DataView<Change>("discussion", discussionDp) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<Change> item) {
				final Change entry = item.getModelObject();
				if (entry.isMerge()) {
					/*
					 * MERGE
					 */
					String resolvedBy = entry.getString(Field.mergeSha);

					// identify the merged patch, it is likely the last
					Patchset mergedPatch = null;
					for (Change c : revisions) {
						if (c.patchset.tip.equals(resolvedBy)) {
							mergedPatch = c.patchset;
							break;
						}
					}

					String commitLink;
					if (mergedPatch == null) {
						// shouldn't happen, but just-in-case
						int len = app().settings().getInteger(Keys.web.shortCommitIdLength, 6);
						commitLink = resolvedBy.substring(0, len);
					} else {
						// expected result
						commitLink = getString("gb.revision") + " " + mergedPatch.rev;
					}

					Fragment mergeCloseFragment = new Fragment("entry", "mergeCloseFragment", this);
					Fragment mergeFragment = new Fragment("merge", "mergeFragment", this);
					mergeFragment.add(new LinkPanel("commitLink", null, commitLink,
							CommitPage.class, WicketUtils.newObjectParameter(repositoryName, resolvedBy)));
					mergeFragment.add(new Label("toBranch", MessageFormat.format(getString("gb.toBranch"),
							"<b>" + ticket.mergeTo + "</b>")).setEscapeModelStrings(false));
					addUserAttributions(mergeFragment, entry, 0);
					addDateAttributions(mergeFragment, entry);

					Fragment closeFragment = new Fragment("close", "closeFragment", this);
					addUserAttributions(closeFragment, entry, 0);
					addDateAttributions(closeFragment, entry);

					mergeCloseFragment.add(mergeFragment);
					mergeCloseFragment.add(closeFragment);
					mergeCloseFragment.add(new Fragment("boundary", "boundaryFragment", this));

					item.add(mergeCloseFragment);
				} else if (entry.isStatusChange()) {
					/*
					 *  STATUS CHANGE
					 */
					Fragment frag = new Fragment("entry", "statusFragment", this);
					Label status = new Label("statusChange", entry.getStatus().toString());
					String css = getLozengeClass(entry.getStatus(), false);
					WicketUtils.setCssClass(status, css);
					for (IBehavior b : status.getBehaviors()) {
						if (b instanceof SimpleAttributeModifier) {
							SimpleAttributeModifier sam = (SimpleAttributeModifier) b;
							if ("class".equals(sam.getAttribute())) {
								status.add(new SimpleAttributeModifier("class", "status-change " + sam.getValue()));
								break;
							}
						}
					}
					frag.add(status);
					addUserAttributions(frag, entry, avatarWidth);
					addDateAttributions(frag, entry);
					item.add(frag);
				} else {
					/*
					 * COMMENT
					 */
					String comment = MarkdownUtils.transformGFM(app().settings(), entry.comment.text, repositoryName);
					Fragment frag = new Fragment("entry", "commentFragment", this);
					Label commentIcon = new Label("commentIcon");
					if (entry.comment.src == CommentSource.Email) {
						WicketUtils.setCssClass(commentIcon, "iconic-mail");
					} else {
						WicketUtils.setCssClass(commentIcon, "iconic-comment-alt2-stroke");
					}
					frag.add(commentIcon);
					frag.add(new Label("comment", comment).setEscapeModelStrings(false));
					addUserAttributions(frag, entry, avatarWidth);
					addDateAttributions(frag, entry);
					item.add(frag);
				}
			}
		};
		add(discussionView);


		/*
		 * ADD COMMENT PANEL
		 */
		if (UserModel.ANONYMOUS.equals(user)
				|| !repository.isBare
				|| repository.isFrozen
				|| repository.isMirror) {

			// prohibit comments for anonymous users, local working copy repos,
			// frozen repos, and mirrors
			add(new Label("newComment").setVisible(false));
		} else {
			// permit user to comment
			Fragment newComment = new Fragment("newComment", "newCommentFragment", this);
			GravatarImage img = new GravatarImage("newCommentAvatar", user.username, user.emailAddress,
					"gravatar-round", avatarWidth, false, true);
			newComment.add(img);
			CommentPanel commentPanel = new CommentPanel("commentPanel", user, ticket, null, TicketsPage.class);
			commentPanel.setRepository(repositoryName);
			newComment.add(commentPanel);
			add(newComment);
		}


		/*
		 *  PATCHSET TAB
		 */
		if (currentPatchset == null) {
			// no patchset yet, show propose fragment
			String repoUrl = getRepositoryUrl(user, repository);
			Fragment changeIdFrag = new Fragment("patchset", "proposeFragment", this);
			changeIdFrag.add(new Label("proposeInstructions", MarkdownUtils.transformMarkdown(getString("gb.proposeInstructions"))).setEscapeModelStrings(false));
			changeIdFrag.add(new Label("barnumWorkflow", MessageFormat.format(getString("gb.proposeWith"), "Barnum")));
			changeIdFrag.add(new Label("barnumWorkflowSteps", getWorkflow("propose_barnum.md", repoUrl, ticket.number)).setEscapeModelStrings(false));
			changeIdFrag.add(new Label("gitWorkflow", MessageFormat.format(getString("gb.proposeWith"), "Git")));
			changeIdFrag.add(new Label("gitWorkflowSteps", getWorkflow("propose_git.md", repoUrl, ticket.number)).setEscapeModelStrings(false));
			add(changeIdFrag);
		} else {
			// show current patchset
			Fragment reviewFrag = new Fragment("patchset", "patchsetFragment", this);

			// current revision
			MarkupContainer panel = createPatchsetPanel("panel", repository, user);
			reviewFrag.add(panel);
			addUserAttributions(reviewFrag, currentRevision, avatarWidth);
			addUserAttributions(panel, currentRevision, 0);
			addDateAttributions(panel, currentRevision);

			List<RevCommit> commits = JGitUtils.getRevLog(getRepository(), currentPatchset.base, currentPatchset.tip);
			ListDataProvider<RevCommit> commitsDp = new ListDataProvider<RevCommit>(commits);
			DataView<RevCommit> commitsView = new DataView<RevCommit>("commit", commitsDp) {
				private static final long serialVersionUID = 1L;

				@Override
				public void populateItem(final Item<RevCommit> item) {
					RevCommit commit = item.getModelObject();
					PersonIdent author = commit.getAuthorIdent();
					item.add(new GravatarImage("authorAvatar", author.getName(), author.getEmailAddress(), null, 16, false, false));
					item.add(new Label("author", commit.getAuthorIdent().getName()));
					item.add(new LinkPanel("commitId", null, getShortObjectId(commit.getName()),
							CommitPage.class, WicketUtils.newObjectParameter(repositoryName, commit.getName()), true));
					item.add(new LinkPanel("diff", "link", getString("gb.diff"), CommitDiffPage.class,
							WicketUtils.newObjectParameter(repositoryName, commit.getName()), true));
					item.add(new Label("title", StringUtils.trimString(commit.getShortMessage(), Constants.LEN_SHORTLOG_REFS)));
					item.add(WicketUtils.createDateLabel("commitDate", JGitUtils.getCommitDate(commit), GitBlitWebSession
							.get().getTimezone(), getTimeUtils(), false));
					item.add(new DiffStatPanel("commitDiffStat", 0, 0, true));
				}
			};
			reviewFrag.add(commitsView);
			add(reviewFrag);
		}


		/*
		 * HISTORY TAB
		 */
		Fragment revisionHistory = new Fragment("history", "historyFragment", this);
		Set<Change> set = new HashSet<Change>();
		set.addAll(revisions);
		set.addAll(statusChanges);
		set.addAll(comments);

		List<Change> events = new ArrayList<Change>(set);
		Collections.sort(events);
		Collections.reverse(events);
		ListDataProvider<Change> eventsDp = new ListDataProvider<Change>(events);
		DataView<Change> eventsView = new DataView<Change>("event", eventsDp) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<Change> item) {
				Change event = item.getModelObject();
				if (event.hasPatchset()) {
					// patchset
					Patchset patchset = event.patchset;
					String what = getString("gb.uploadedPatchset");
					switch (patchset.addedCommits) {
					case 1:
						what += " (+" + patchset.addedCommits + " " + getString("gb.commit") + ")";
						break;
					case 0:
						break;
					default:
						what += " (+" + patchset.addedCommits + " " + getString("gb.commits") + ")";
						break;
					}
					item.add(new Label("what", what));
					item.add(new LinkPanel("patchsetRevision", "commit", getString("gb.revision") + " " + patchset.rev,
							CommitPage.class, WicketUtils.newObjectParameter(repositoryName, patchset.tip), true));
					String typeCss = getPatchsetTypeCss(patchset.type);
					Label typeLabel = new Label("patchsetType", patchset.type.toString());
					if (typeCss == null) {
						typeLabel.setVisible(false);
					} else {
						WicketUtils.setCssClass(typeLabel, typeCss);
					}
					item.add(typeLabel);

					boolean showMergeBase = PatchsetType.Proposal == patchset.type
										|| PatchsetType.Rebase == patchset.type
										|| PatchsetType.Rebase_Squash == patchset.type;

					item.add(new LinkPanel("mergeBase", "link", getString("gb.mergeBase"),
							CommitPage.class, WicketUtils.newObjectParameter(repositoryName, patchset.base), true)
							.setVisible(showMergeBase));

					if (ticket.isMerged() && patchset.tip.equals(ticket.mergeSha)) {
						// merged revision
						Label status = new Label("revisedStatus", Status.Merged.toString());
						String css = getLozengeClass(Status.Merged, true);
						WicketUtils.setCssClass(status, css);
						item.add(status);
					} else {
						item.add(new Label("revisedStatus").setVisible(false));
					}
					// show commit diffstat
					item.add(new DiffStatPanel("patchsetDiffStat", patchset.insertions, patchset.deletions, true));
				} else if (event.hasComment()) {
					// comment
					item.add(new Label("what", getString("gb.commented")));
					item.add(new Label("patchsetRevision").setVisible(false));
					item.add(new Label("mergeBase").setVisible(false));
					item.add(new Label("patchsetType").setVisible(false));
					item.add(new Label("revisedStatus").setVisible(false));
					item.add(new Label("patchsetDiffStat").setVisible(false));
				} else {
					// status change
					item.add(new Label("patchsetRevision").setVisible(false));
					item.add(new Label("mergeBase").setVisible(false));
					item.add(new Label("patchsetType").setVisible(false));
					Status res = event.getStatus();
					String what;
					switch (res) {
					case New:
						if (ticket.isProposal()) {
							what = getString("gb.proposedThisChange");
						} else {
							what = getString("gb.createdThisTicket");
						}
						break;
					default:
						what = getString("gb.changedStatus");
						break;
					}
					item.add(new Label("what", what));
					Label status = new Label("revisedStatus", res.toString());
					String css = getLozengeClass(res, true);
					WicketUtils.setCssClass(status, css);
					item.add(status);
					item.add(new Label("patchsetDiffStat").setVisible(false));
				}
				addUserAttributions(item, event, 16);
				addDateAttributions(item, event);
			}
		};
		revisionHistory.add(eventsView);
		add(revisionHistory);
	}

	protected void addUserAttributions(MarkupContainer container, Change entry, int avatarSize) {
		UserModel commenter = app().users().getUserModel(entry.createdBy);
		if (commenter == null) {
			// unknown user
			container.add(new GravatarImage("changeAvatar", entry.createdBy,
					entry.createdBy, null, avatarSize, false, false).setVisible(avatarSize > 0));
			container.add(new Label("changeAuthor", entry.createdBy.toLowerCase()));
		} else {
			// known user
			container.add(new GravatarImage("changeAvatar", commenter.getDisplayName(),
					commenter.emailAddress, avatarSize > 24 ? "gravatar-round" : null, avatarSize, false, true).setVisible(avatarSize > 0));
			container.add(new LinkPanel("changeAuthor", null, commenter.getDisplayName(),
					UserPage.class, WicketUtils.newUsernameParameter(commenter.username)));
		}
	}

	protected void addDateAttributions(MarkupContainer container, Change entry) {
		container.add(WicketUtils.createDateLabel("changeDate", entry.createdAt, GitBlitWebSession
				.get().getTimezone(), getTimeUtils(), false));

		// set the id attribute
		container.setOutputMarkupId(true);
		container.add(new AttributeModifier("id", Model.of(entry.id)));

		ExternalLink link = new ExternalLink("changeLink", "#" + entry.id);
		container.add(link);
	}

	protected String getWorkflow(String resource, String url, long number) {
		String md = readResource(resource);
		md = md.replace("${url}", url);
		md = md.replace("${repo}", StringUtils.getLastPathElement(StringUtils.stripDotGit(repositoryName)));
		md = md.replace("${number}", "" + number);
		md = md.replace("${integrationBranch}", Repository.shortenRefName(getRepositoryModel().HEAD));
		return MarkdownUtils.transformMarkdown(md);
	}

	protected FreemarkerPanel createPatchsetPanel(String wicketId, RepositoryModel repository, UserModel user) {
		final Patchset currentPatchset = ticket.getCurrentPatchset();
		List<Patchset> patchsets = new ArrayList<Patchset>(ticket.getPatchsets());
		patchsets.remove(currentPatchset);
		Collections.reverse(patchsets);

		Map<String, Object> pmap = new HashMap<String, Object>();
		pmap.put("accordianId", "rev" + currentPatchset.rev);

		FreemarkerPanel panel = new FreemarkerPanel(wicketId, "CollapsiblePatch.fm", pmap);
		panel.setParseGeneratedMarkup(true);

		// patchset header
		panel.add(new LinkPanel("patchId", null, getString("gb.revision") + " " + currentPatchset.rev,
				CommitPage.class, WicketUtils.newObjectParameter(repositoryName, currentPatchset.tip), true));

		// patchset type
		String patchsetTypeCss = getPatchsetTypeCss(currentPatchset.type);
		String typeSpan = MessageFormat.format("<span class=\"{0}\">{1}</span>",
				patchsetTypeCss, currentPatchset.type.toString().toUpperCase());
		String patchsetType = MessageFormat.format(getString("gb.thisPatchsetRevisionTypeIs"), typeSpan);
		panel.add(new Label("patchsetType", patchsetType).setEscapeModelStrings(false));
		switch (currentPatchset.addedCommits) {
			case 1:
				panel.add(new Label("plusCommits", getString("gb.plusOneCommit")));
				break;
			default:
				panel.add(new Label("plusCommits",
						MessageFormat.format(getString("gb.plusNCommits"),
								currentPatchset.addedCommits)).setVisible(currentPatchset.addedCommits > 0));
				break;
		}

		// compare menu
		panel.add(new LinkPanel("compareMergeBase", null, getString("gb.compareToMergeBase"),
				ComparePage.class, WicketUtils.newRangeParameter(repositoryName, currentPatchset.base, currentPatchset.tip), true));

		ListDataProvider<Patchset> compareMenuDp = new ListDataProvider<Patchset>(patchsets);
		DataView<Patchset> compareMenu = new DataView<Patchset>("comparePatch", compareMenuDp) {
			private static final long serialVersionUID = 1L;
			@Override
			public void populateItem(final Item<Patchset> item) {
				Patchset patchset = item.getModelObject();
				LinkPanel link = new LinkPanel("compareLink", null,
						MessageFormat.format(getString("gb.compareToPatchsetN"), patchset.rev),
						ComparePage.class, WicketUtils.newRangeParameter(getRepositoryModel().name,
								patchset.tip, currentPatchset.tip), true);
				item.add(link);

			}
		};
		panel.add(compareMenu);

		// Barnum menu
		String ptcheckout = MessageFormat.format("pt checkout {0,number,0}", ticket.number);
		panel.add(createCopyFragment("ptFetch", "pt refresh && " + ptcheckout));
		panel.add(new Label("ptFetchLabel", ptcheckout));

		String ptreview = MessageFormat.format("pt checkout {0,number,0} -b ticket/{0,number,0}", ticket.number);
		panel.add(createCopyFragment("ptReview", "pt refresh && " + ptreview));
		panel.add(new Label("ptReviewLabel", ptreview));

		// git menu
		String repoUrl = getRepositoryUrl(user, repository);
		String fetch = MessageFormat.format("git fetch {0} {1} && git checkout FETCH_HEAD", repoUrl, currentPatchset.ref);
		panel.add(createCopyFragment("gitFetch", fetch));
		panel.add(new Label("gitFetchLabel", MessageFormat.format(getString("gb.fetchPatchset"), currentPatchset.rev)));

		String review = MessageFormat.format("git fetch {0} {1} && git checkout FETCH_HEAD -b ticket/{2,number,0}", repoUrl, currentPatchset.ref, ticket.number);
		panel.add(createCopyFragment("gitReview", review));
		panel.add(new Label("gitReviewLabel", MessageFormat.format(getString("gb.reviewPatchset"), currentPatchset.rev)));

		// changed paths list
		List<PathChangeModel> paths = JGitUtils.getFilesInRange(getRepository(), currentPatchset.base, currentPatchset.tip);
		ListDataProvider<PathChangeModel> pathsDp = new ListDataProvider<PathChangeModel>(paths);
		DataView<PathChangeModel> pathsView = new DataView<PathChangeModel>("changedPath", pathsDp) {
			private static final long serialVersionUID = 1L;
			int counter;

			@Override
			public void populateItem(final Item<PathChangeModel> item) {
				final PathChangeModel entry = item.getModelObject();
				Label changeType = new Label("changeType", "");
				WicketUtils.setChangeTypeCssClass(changeType, entry.changeType);
				setChangeTypeTooltip(changeType, entry.changeType);
				item.add(changeType);
				item.add(new DiffStatPanel("diffStat", entry.insertions, entry.deletions, true));

				boolean hasSubmodule = false;
				String submodulePath = null;
				if (entry.isTree()) {
					// tree
					item.add(new LinkPanel("pathName", null, entry.path, TreePage.class,
							WicketUtils
									.newPathParameter(repositoryName, currentPatchset.tip, entry.path), true));
					item.add(new Label("diffStat").setVisible(false));
				} else if (entry.isSubmodule()) {
					// submodule
					String submoduleId = entry.objectId;
					SubmoduleModel submodule = getSubmodule(entry.path);
					submodulePath = submodule.gitblitPath;
					hasSubmodule = submodule.hasSubmodule;

					item.add(new LinkPanel("pathName", "list", entry.path + " @ " +
							getShortObjectId(submoduleId), TreePage.class,
							WicketUtils.newPathParameter(submodulePath, submoduleId, ""), true).setEnabled(hasSubmodule));
					item.add(new Label("diffStat").setVisible(false));
				} else {
					// blob
					String displayPath = entry.path;
					String path = entry.path;
					if (entry.isSymlink()) {
						RevCommit commit = JGitUtils.getCommit(getRepository(), Constants.R_TICKETS + ticket.number);
						path = JGitUtils.getStringContent(getRepository(), commit.getTree(), path);
						displayPath = entry.path + " -> " + path;
					}

					if (entry.changeType.equals(ChangeType.ADD)) {
						// add show view
						item.add(new LinkPanel("pathName", "list", displayPath, BlobPage.class,
								WicketUtils.newPathParameter(repositoryName, currentPatchset.tip, path), true));
					} else if (entry.changeType.equals(ChangeType.DELETE)) {
						// delete, show label
						item.add(new Label("pathName", displayPath));
					} else {
						// mod, show diff
						item.add(new LinkPanel("pathName", "list", displayPath, BlobDiffPage.class,
								WicketUtils.newPathParameter(repositoryName, currentPatchset.tip, path), true));
					}
				}

				// quick links
				if (entry.isSubmodule()) {
					// submodule
					item.add(setNewTarget(new BookmarkablePageLink<Void>("diff", BlobDiffPage.class, WicketUtils
							.newPathParameter(repositoryName, entry.commitId, entry.path)))
							.setEnabled(!entry.changeType.equals(ChangeType.ADD)));
					item.add(new BookmarkablePageLink<Void>("view", CommitPage.class, WicketUtils
							.newObjectParameter(submodulePath, entry.objectId)).setEnabled(hasSubmodule));
				} else {
					// tree or blob
					item.add(setNewTarget(new BookmarkablePageLink<Void>("diff", BlobDiffPage.class, WicketUtils
							.newBlobDiffParameter(repositoryName, currentPatchset.base, currentPatchset.tip, entry.path)))
							.setEnabled(!entry.changeType.equals(ChangeType.ADD)
									&& !entry.changeType.equals(ChangeType.DELETE)));
					item.add(setNewTarget(new BookmarkablePageLink<Void>("view", BlobPage.class, WicketUtils
							.newPathParameter(repositoryName, currentPatchset.tip, entry.path)))
							.setEnabled(!entry.changeType.equals(ChangeType.DELETE)));
				}

				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		panel.add(pathsView);

		panel.add(createMergePanel(user, repository));

		return panel;
	}

	protected <X extends MarkupContainer> X setNewTarget(X x) {
		x.add(new SimpleAttributeModifier("target", "_blank"));
		return x;
	}

	/**
	 * Adds a merge panel for the patchset to the markup container.  The panel
	 * may just a message if the patchset can not be merged.
	 *
	 * @param c
	 * @param user
	 * @param repository
	 */
	protected Component createMergePanel(UserModel user, RepositoryModel repository) {
		Patchset patchset = ticket.getCurrentPatchset();
		boolean reviewRequired = false; // TODO allow reviews
		boolean patchsetMergeable = ticket.isOpen() && (!reviewRequired || (reviewRequired && ticket.isApproved(patchset)));
		if (patchset == null) {
			// no patchset to merge
			return new Label("mergePanel");
		} else if (patchsetMergeable) {
			boolean canMerge = JGitUtils.canMerge(getRepository(), patchset.tip, ticket.mergeTo);
			if (canMerge) {
				// patchset can be cleanly merged to integration branch
				Fragment mergePanel = new Fragment("mergePanel", "mergeableFragment", this);
				mergePanel.add(new Label("mergeTitle", MessageFormat.format(getString("gb.patchsetMergeable"), ticket.mergeTo)));
				if (user.canPush(repository)) {
					// user can merge locally
					mergePanel.add(new ExternalLink("mergeButton", "#").setVisible(user.canPush(repository)));
					Component instructions = getMergeInstructions(user, repository, "mergeMore", "gb.patchsetMergeableMore");
					mergePanel.add(instructions);
				} else {
					mergePanel.add(new Label("mergeButton").setVisible(false));
					mergePanel.add(new Label("mergeMore").setVisible(false));
				}
				return mergePanel;
			} else {
				// patchset can not be cleanly merged
				Fragment mergePanel = new Fragment("mergePanel", "notMergeableFragment", this);
				mergePanel.add(new Label("mergeTitle", MessageFormat.format(getString("gb.patchsetNotMergeable"), ticket.mergeTo)));
				if (user.canPush(repository)) {
					// user can merge locally
					Component instructions = getMergeInstructions(user, repository, "mergeMore", "gb.patchsetNotMergeableMore");
					mergePanel.add(instructions);
				} else {
					mergePanel.add(new Label("mergeMore").setVisible(false));
				}
				return mergePanel;
			}
		} else {
			if (ticket.isVetoed(patchset)) {
				// patchset has been vetoed
				Fragment mergePanel =  new Fragment("mergePanel", "vetoedFragment", this);
				mergePanel.add(new Label("mergeTitle", MessageFormat.format(getString("gb.patchsetNotMergeable"), ticket.mergeTo)));
				return mergePanel;
			} else if (reviewRequired) {
				// patchset has been not been approved for merge
				Fragment mergePanel = new Fragment("mergePanel", "notApprovedFragment", this);
				mergePanel.add(new Label("mergeTitle", MessageFormat.format(getString("gb.patchsetNotApproved"), ticket.mergeTo)));
				mergePanel.add(new Label("mergeMore", MessageFormat.format(getString("gb.patchsetNotApprovedMore"), ticket.mergeTo)));
				return mergePanel;
			} else {
				// other case
				return new Label("mergePanel");
			}
		}
	}

	protected Component getMergeInstructions(UserModel user, RepositoryModel repository, String markupId, String infoKey) {
		Fragment cmd = new Fragment(markupId, "commandlineMergeFragment", this);
		cmd.add(new Label("instructions", MessageFormat.format(getString(infoKey), ticket.mergeTo)));
		String repoUrl = getRepositoryUrl(user, repository);

		String step1 = MessageFormat.format("git checkout -b ticket/{0,number,0} {1}", ticket.number, ticket.mergeTo);
		String step2 = MessageFormat.format("git pull {0} refs/tickets/{1,number,0}", repoUrl, ticket.number);
		String step3 = MessageFormat.format("git checkout {0}\ngit merge ticket/{1,number,0}\ngit push origin {0}", ticket.mergeTo, ticket.number);

		cmd.add(new Label("step1", step1));
		cmd.add(new Label("step2", step2));
		cmd.add(new Label("step3", step3));

		cmd.add(createCopyFragment("copyStep1", step1.replace("\n", " && ")));
		cmd.add(createCopyFragment("copyStep2", step2.replace("\n", " && ")));
		cmd.add(createCopyFragment("copyStep3", step3.replace("\n", " && ")));
		return cmd;
	}

	/**
	 * Returns the primary repository url
	 *
	 * @param user
	 * @param repository
	 * @return the primary repository url
	 */
	protected String getRepositoryUrl(UserModel user, RepositoryModel repository) {
		HttpServletRequest req = ((WebRequest) getRequest()).getHttpServletRequest();
		String primaryurl = app().gitblit().getRepositoryUrls(req, user, repository).get(0).url;
		String url = primaryurl;
		try {
			url = new URIish(primaryurl).setUser(null).toString();
		} catch (Exception e) {
		}
		return url;
	}

	/**
	 * Returns the ticket (if any) that this commit references.
	 *
	 * @param commit
	 * @return null or a ticket
	 */
	protected TicketModel getTicket(RevCommit commit) {
		try {
			Map<String, Ref> refs = getRepository().getRefDatabase().getRefs(Constants.R_CHANGES);
			for (Map.Entry<String, Ref> entry : refs.entrySet()) {
				if (entry.getValue().getObjectId().equals(commit.getId())) {
					String n = entry.getKey();
					n = n.substring(n.indexOf('/') + 1);
					n = n.substring(0, n.indexOf('/'));
					long id = Long.parseLong(n);
					TicketModel ticket = app().tickets().getTicket(repositoryName, id);
					return ticket;
				}
			}
		} catch (Exception e) {
			logger().error("failed to determine ticket from ref", e);
		}
		return null;
	}

	protected String getPatchsetTypeCss(PatchsetType type) {
		String typeCss;
		switch (type) {
			case Rebase:
				typeCss = getLozengeClass(Status.Merged, false);
				break;
			case Squash:
			case Rebase_Squash:
				typeCss = getLozengeClass(Status.Declined, false);
				break;
			case Amend:
				typeCss = getLozengeClass(Status.On_Hold, false);
				break;
			case Proposal:
				typeCss = getLozengeClass(Status.New, false);
				break;
			case FastForward:
			default:
				typeCss = null;
			break;
		}
		return typeCss;
	}

	@Override
	protected String getPageName() {
		return getString("gb.ticket");
	}

	@Override
	protected Class<? extends BasePage> getRepoNavPageClass() {
		return TicketsPage.class;
	}

	@Override
	protected String getPageTitle(String repositoryName) {
		return "#" + ticket.number + " - " + ticket.title;
	}

	protected Fragment createCopyFragment(String wicketId, String text) {
		if (app().settings().getBoolean(Keys.web.allowFlashCopyToClipboard, true)) {
			// clippy: flash-based copy & paste
			Fragment copyFragment = new Fragment(wicketId, "clippyPanel", this);
			String baseUrl = WicketUtils.getGitblitURL(getRequest());
			ShockWaveComponent clippy = new ShockWaveComponent("clippy", baseUrl + "/clippy.swf");
			clippy.setValue("flashVars", "text=" + StringUtils.encodeURL(text));
			copyFragment.add(clippy);
			return copyFragment;
		} else {
			// javascript: manual copy & paste with modal browser prompt dialog
			Fragment copyFragment = new Fragment(wicketId, "jsPanel", this);
			ContextImage img = WicketUtils.newImage("copyIcon", "clippy.png");
			img.add(new JavascriptTextPrompt("onclick", "Copy to Clipboard (Ctrl+C, Enter)", text));
			copyFragment.add(img);
			return copyFragment;
		}
	}
}
