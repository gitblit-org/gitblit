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
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.PageParameters;
import org.apache.wicket.RestartResponseException;
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
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.Constants;
import com.gitblit.Keys;
import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.RepositoryUrl;
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

public class TicketPage extends TicketBasePage {

	final int avatarWidth = 48;

	final TicketModel ticket;

	public TicketPage(PageParameters params) {
		super(params);

		final UserModel user = GitBlitWebSession.get().getUser() == null ? UserModel.ANONYMOUS : GitBlitWebSession.get().getUser();
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
		List<Change> stateChanges = new ArrayList<Change>();
		List<Change> discussion = new ArrayList<Change>();
		for (Change change : ticket.changes) {
			if (change.hasComment() || (change.isStatusChange() && !change.hasField(Field.number))) {
				discussion.add(change);
			}
			if (change.hasPatchset()) {
				revisions.add(change);
			}
			if (change.isStatusChange() && !change.hasPatchset()) {
				stateChanges.add(change);
			}
		}
		final Change currentRevision = revisions.isEmpty() ? null : revisions.get(revisions.size() - 1);

		HttpServletRequest req = ((WebRequest) getRequest()).getHttpServletRequest();

		List<RepositoryUrl> repositoryUrls = app().gitblit().getRepositoryUrls(req, user, repository);
		final RepositoryUrl primaryUrl = repositoryUrls.size() == 0 ? null : repositoryUrls.get(0);

		UserModel createdBy = app().users().getUserModel(ticket.createdBy);
		if (createdBy == null) {
			add(new Label("whoCreated", ticket.createdBy));
		} else {
			add(new LinkPanel("whoCreated", null, createdBy.getDisplayName(),
					UserPage.class, WicketUtils.newUsernameParameter(createdBy.username)));
		}

		if (ticket.isPullRequest()) {
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


		add(new Label("ticketTitle", ticket.title));
		String exportHref = urlFor(ExportTicketPage.class, params).toString();
		add(new ExternalLink("exportJson", exportHref, "json"));
		String href = urlFor(TicketsPage.class, params).toString();
		add(new ExternalLink("ticketNumber", href, "#" + ticket.number));

		if (StringUtils.isEmpty(ticket.assignedTo)) {
			// TODO assignment selector
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

		if (StringUtils.isEmpty(ticket.milestone)) {
			add(new Label("milestone", getString("gb.notSpecified")));
		} else {
			// link to milestone query
			PageParameters milestoneParameters = new PageParameters();
			milestoneParameters.put("r", repositoryName);
			milestoneParameters.put(Lucene.milestone.name(), ticket.milestone);
			add(new LinkPanel("milestone", null, ticket.milestone, TicketsPage.class, milestoneParameters));
		}
		// TODO milestone selector

		String desc;
		if (StringUtils.isEmpty(ticket.body)) {
			desc = getString("gb.noDescriptionGiven");
		} else {
			desc = MarkdownUtils.transformGFM(app().settings(), ticket.body, ticket.repository);
		}
		add(new Label("ticketDescription", desc).setEscapeModelStrings(false));

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

		// status label injects an icon and the ticket state
		Fragment ticketState = new Fragment("ticketState", "ticketStateFragment", this);
		Label ticketIcon = new Label("ticketIcon");
		if (ticket.isPullRequest()) {
			WicketUtils.setCssClass(ticketIcon, "fa fa-code");
		} else if (ticket.isBug()) {
			WicketUtils.setCssClass(ticketIcon, "fa fa-bug");
		} else {
			WicketUtils.setCssClass(ticketIcon, "fa fa-ticket");
		}
		ticketState.add(ticketIcon);
		ticketState.add(new Label("ticketStatus", ticket.status.toString()));
		WicketUtils.setCssClass(ticketState, getLozengeClass(ticket.status, false));
		add(ticketState);


		Label summaryState = new Label("summaryState", ticket.status.toString());
		WicketUtils.setCssClass(summaryState, getLozengeClass(ticket.status, false));
		add(summaryState);

		if (currentRevision == null || currentRevision.patch == null) {
			// no patchset
			add(new Label("insertionCount").setVisible(false));
			add(new Label("deletionCount").setVisible(false));
			add(new Label("diffstat").setVisible(false));
		} else {
			// has patchset
			int insertions = currentRevision.patch.insertions;
			int deletions = currentRevision.patch.deletions;

			// diffstat in header
			add(new DiffStatPanel("diffstat", insertions, deletions));

			// insertions
			String iPattern = null;
			switch (insertions) {
			case 0:
				break;
			case 1:
				iPattern = getString("gb.oneInsertion");
				break;
			default:
				iPattern = getString("gb.nInsertions");
				break;
			}
			if (iPattern == null) {
				add(new Label("insertionCount").setVisible(false));
			} else {
				add(new Label("insertionCount", MessageFormat.format(iPattern,
						"<b><span class=\"diffstat-insert\">+" + insertions + "</span></b>")).setEscapeModelStrings(false));
			}

			// deletions
			String dPattern = null;
			switch (deletions) {
			case 0:
				break;
			case 1:
				dPattern = getString("gb.oneDeletion");
				break;
			default:
				dPattern = getString("gb.nDeletions");
				break;
			}
			if (dPattern == null) {
				add(new Label("deletionCount").setVisible(false));
			} else {
				add(new Label("deletionCount", MessageFormat.format(dPattern,
						"<b><span class=\"diffstat-delete\">-" + deletions + "</span></b>")).setEscapeModelStrings(false));
			}
		}

		List<Change> comments = ticket.getComments();
		add(new Label("commentCount", "" + comments.size()).setVisible(!comments.isEmpty()));

		add(new Label("ticketTopic", ticket.topic == null ? "" : (getString("gb.topic") + " <b>" + ticket.topic + "</b>")).setEscapeModelStrings(false));

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
		 * DISCUSSION TAB
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

					// identify the merged patch, it should always be the last
					Patchset mergedPatch = null;
					for (Change c : revisions) {
						if (c.patch.tip.equals(resolvedBy)) {
							mergedPatch = c.patch;
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
					Label status = new Label("statusChange", entry.getString(Field.status));
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
					addUserAttributions(frag, entry, 0);
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

		if (UserModel.ANONYMOUS.equals(user)) {
			// anonymous users can not comment
			add(new Label("newComment").setVisible(false));
		} else {
			// permit user to comment
			Fragment newComment = new Fragment("newComment", "newCommentFragment", this);
			CommentPanel commentPanel = new CommentPanel("commentPanel", user, ticket, null, TicketsPage.class);
			commentPanel.setRepository(repositoryName);
			newComment.add(commentPanel);
			add(newComment);
		}

		/*
		 *  PATCHSET TAB
		 */
		if (revisions.size() == 0) {
			// no patchsets yet, show propose fragment
			Fragment changeIdFrag = new Fragment("patchset", "proposeFragment", this);
			changeIdFrag.add(new Label("proposeInstructions", MarkdownUtils.transformMarkdown(getString("gb.proposeInstructions"))).setEscapeModelStrings(false));
			changeIdFrag.add(new Label("barnumWorkflow", MessageFormat.format(getString("gb.proposeWith"), "Barnum")));
			changeIdFrag.add(new Label("barnumWorkflowSteps", getWorkflow("propose_barnum.md", primaryUrl.url, ticket.number)).setEscapeModelStrings(false));
			changeIdFrag.add(new Label("gitWorkflow", MessageFormat.format(getString("gb.proposeWith"), "Git")));
			changeIdFrag.add(new Label("gitWorkflowSteps", getWorkflow("propose_git.md", primaryUrl.url, ticket.number)).setEscapeModelStrings(false));
			add(changeIdFrag);
		} else {
			// ticket has patchsets
			Fragment reviewFrag = new Fragment("patchset", "patchsetFragment", this);

			// current revision
			MarkupContainer panel = newPatch("panel", repository, user, ticket, currentRevision, revisions, primaryUrl);
			reviewFrag.add(panel);
			addUserAttributions(reviewFrag, currentRevision, avatarWidth);
			addUserAttributions(panel, currentRevision, 0);
			addDateAttributions(panel, currentRevision);

			List<RevCommit> commits = JGitUtils.getRevLog(getRepository(), currentRevision.patch.base, currentRevision.patch.tip);
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
							CommitPage.class, WicketUtils.newObjectParameter(repositoryName, commit.getName())));
					item.add(new LinkPanel("diff", "link", getString("gb.diff"), CommitDiffPage.class,
							WicketUtils.newObjectParameter(repositoryName, commit.getName())));
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
		List<Change> events = new ArrayList<Change>();
		events.addAll(revisions);
		events.addAll(stateChanges);
		events.addAll(comments);
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
					Patchset patch = event.patch;
					String what = getString("gb.uploadedPatchset");
					switch (patch.addedCommits) {
					case 1:
						what += " (+" + patch.addedCommits + " " + getString("gb.commit") + ")";
						break;
					case 0:
						break;
					default:
						what += " (+" + patch.addedCommits + " " + getString("gb.commits") + ")";
						break;
					}
					item.add(new Label("what", what));
					item.add(new LinkPanel("patchsetRevision", "commit", getString("gb.revision") + " " + patch.rev,
							CommitPage.class, WicketUtils.newObjectParameter(repositoryName, patch.tip)));
					String typeCss;
					switch (patch.type) {
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
					Label typeLabel = new Label("patchsetType", patch.type.toString());
					if (typeCss == null) {
						typeLabel.setVisible(false);
					} else {
						WicketUtils.setCssClass(typeLabel, typeCss);
					}
					item.add(typeLabel);

					boolean showMergeBase = PatchsetType.Proposal == patch.type
										|| PatchsetType.Rebase == patch.type
										|| PatchsetType.Rebase_Squash == patch.type;

					item.add(new LinkPanel("mergeBase", "link", getString("gb.mergeBase"),
							CommitPage.class, WicketUtils.newObjectParameter(repositoryName, patch.base))
							.setVisible(showMergeBase));

					if (ticket.isMerged() && patch.tip.equals(ticket.mergeSha)) {
						// merged revision
						Label state = new Label("ticketState", Status.Merged.toString());
						String css = getLozengeClass(Status.Merged, true);
						WicketUtils.setCssClass(state, css);
						item.add(state);
					} else {
						item.add(new Label("ticketState").setVisible(false));
					}
					// show commit diffstat
					item.add(new DiffStatPanel("ticketDiffStat", patch.insertions, patch.deletions, true));
				} else if (event.hasComment()) {
					// comment
					item.add(new Label("what", getString("gb.commented")));
					item.add(new Label("patchsetRevision").setVisible(false));
					item.add(new Label("mergeBase").setVisible(false));
					item.add(new Label("patchsetType").setVisible(false));
					item.add(new Label("ticketState").setVisible(false));
					item.add(new Label("ticketDiffStat").setVisible(false));
				} else {
					// state change
					item.add(new Label("patchsetRevision").setVisible(false));
					item.add(new Label("mergeBase").setVisible(false));
					item.add(new Label("patchsetType").setVisible(false));
					Status res = event.getStatus();
					String what;
					switch (res) {
					case New:
						if (ticket.isPullRequest()) {
							what = getString("gb.proposedThisChange");
						} else {
							what = getString("gb.createdThisTicket");
						}
						break;
					default:
						what = getString("gb.changedState");
						break;
					}
					item.add(new Label("what", what));
					Label state = new Label("ticketState", res.toString());
					String css = getLozengeClass(res, true);
					WicketUtils.setCssClass(state, css);
					item.add(state);
					item.add(new Label("ticketDiffStat").setVisible(false));
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
					commenter.emailAddress, avatarSize > 24 ? "gravatar" : null, avatarSize, false, true).setVisible(avatarSize > 0));
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
		return MarkdownUtils.transformMarkdown(md);
	}

	protected FreemarkerPanel newPatch(String wicketId, RepositoryModel repository, UserModel user, TicketModel ticket,
			Change entry, List<Change> patches, RepositoryUrl primaryUrl) {
		List<Change> otherPatches = new ArrayList<Change>(patches);
		otherPatches.remove(entry);
		Collections.reverse(otherPatches);

		final Patchset patch = entry.patch;

		Map<String, Object> pmap = new HashMap<String, Object>();
		pmap.put("accordianId", "rev" + patch.rev);

		FreemarkerPanel panel = new FreemarkerPanel(wicketId, "CollapsiblePatch.fm", pmap);
		panel.setParseGeneratedMarkup(true);

		// patch header
		panel.add(new LinkPanel("patchId", null, getString("gb.revision") + " " + patch.rev,
				CommitPage.class, WicketUtils.newObjectParameter(repositoryName, patch.tip)));
		panel.add(new Label("forBranch", MessageFormat.format(getString("gb.forBranch"),
				"<b>" + ticket.mergeTo + "</b>")).setEscapeModelStrings(false));

		// compare menu
		panel.add(new LinkPanel("compareMergeBase", null, getString("gb.compareToMergeBase"),
				ComparePage.class, WicketUtils.newRangeParameter(repositoryName, patch.base, patch.tip), true));

		ListDataProvider<Change> compareMenuDp = new ListDataProvider<Change>(otherPatches);
		DataView<Change> compareMenu = new DataView<Change>("comparePatch", compareMenuDp) {
			private static final long serialVersionUID = 1L;
			@Override
			public void populateItem(final Item<Change> item) {
				Patchset otherPatch = item.getModelObject().patch;
				String startRef;
				String endRef;
				if (patch.rev < otherPatch.rev) {
					startRef = patch.ref;
					endRef = otherPatch.ref;
				} else {
					startRef = otherPatch.ref;
					endRef = patch.ref;
				}

				item.add(new LinkPanel("compareLink", null,
						MessageFormat.format(getString("gb.compareToPatchsetN"), otherPatch.rev),
						ComparePage.class, WicketUtils.newRangeParameter(getRepositoryModel().name,
								startRef, endRef), true));

			}
		};
		panel.add(compareMenu);

		// Barnum menu
		String checkout = MessageFormat.format("pt checkout {0,number,0}", ticket.number);
		panel.add(createCopyFragment("checkout", checkout));
		panel.add(new Label("checkoutLabel", checkout));

		String topic = StringUtils.isEmpty(ticket.topic) ? ("ticket/" + ticket.number) : ticket.topic;
		String branch = MessageFormat.format("pt checkout {0,number,0} -b {1}", ticket.number, topic);
		panel.add(createCopyFragment("branch", branch));
		panel.add(new Label("branchLabel", branch));

		// git menu
		String fetch = MessageFormat.format("git fetch {0} {1} && git checkout FETCH_HEAD", primaryUrl.url, patch.ref);
		panel.add(createCopyFragment("fetch", fetch));
		panel.add(new Label("fetchLabel", MessageFormat.format(getString("gb.fetchPatchset"), patch.rev)));

		String pull = MessageFormat.format("git pull {0} {1}", primaryUrl.url, patch.ref);
		panel.add(createCopyFragment("pull", pull));
		panel.add(new Label("pullLabel", MessageFormat.format(getString("gb.pullPatchset"), patch.rev)));

		// changed paths list
		RevCommit c = JGitUtils.getCommit(getRepository(), patch.ref);
		List<PathChangeModel> paths;
		if (patch.base == null) {
			paths = JGitUtils.getFilesInCommit(getRepository(), c);
		} else {
			paths = JGitUtils.getFilesInRange(getRepository(), patch.base, patch.tip);
		}

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
									.newPathParameter(repositoryName, entry.commitId, entry.path), true));
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
						path = JGitUtils.getStringContent(getRepository(), getCommit().getTree(), path);
						displayPath = entry.path + " -> " + path;
					}

					if (entry.changeType.equals(ChangeType.ADD)) {
						// add show view
						item.add(new LinkPanel("pathName", "list", displayPath, BlobPage.class,
								WicketUtils.newPathParameter(repositoryName, entry.commitId, path), true));
					} else if (entry.changeType.equals(ChangeType.DELETE)) {
						// delete, show label
						item.add(new Label("pathName", displayPath));
					} else {
						// mod, show diff
						item.add(new LinkPanel("pathName", "list", displayPath, BlobDiffPage.class,
								WicketUtils.newPathParameter(repositoryName, entry.commitId, path), true));
					}
				}

				// quick links
				if (entry.isSubmodule()) {
					// submodule
					item.add(new BookmarkablePageLink<Void>("diff", BlobDiffPage.class, WicketUtils
							.newPathParameter(repositoryName, entry.commitId, entry.path))
							.setEnabled(!entry.changeType.equals(ChangeType.ADD)));
					item.add(new BookmarkablePageLink<Void>("view", CommitPage.class, WicketUtils
							.newObjectParameter(submodulePath, entry.objectId)).setEnabled(hasSubmodule));
				} else {
					// tree or blob
					item.add(new BookmarkablePageLink<Void>("diff", BlobDiffPage.class, WicketUtils
							.newPathParameter(repositoryName, entry.commitId, entry.path))
							.setEnabled(!entry.changeType.equals(ChangeType.ADD)
									&& !entry.changeType.equals(ChangeType.DELETE)));
					item.add(new BookmarkablePageLink<Void>("view", BlobPage.class, WicketUtils
							.newPathParameter(repositoryName, entry.commitId, entry.path))
							.setEnabled(!entry.changeType.equals(ChangeType.DELETE)));
				}

				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		panel.add(pathsView);

		panel.add(createMergePanel(user, repository, ticket, patch));

		return panel;
	}

	/**
	 * Adds a merge panel for the patchset to the markup container.  The panel
	 * may just a message if the patchset can not be merged.
	 *
	 * @param c
	 * @param user
	 * @param repository
	 * @param ticket
	 * @param patchset
	 */
	protected Component createMergePanel(UserModel user, RepositoryModel repository, TicketModel ticket, Patchset patchset) {
		boolean reviewRequired = false; // TODO allow reviews
		boolean patchsetSubmittable = ticket.isOpen() && (!reviewRequired || (reviewRequired && ticket.isApproved(patchset)));
		if (patchset == null) {
			// no patch to merge
			return new Label("mergePanel");
		} else if (user.canPush(repository) && patchsetSubmittable) {
			// parent is merged
			ArrayList<String> submitLinks = new ArrayList<String>();
			boolean canMerge = JGitUtils.canMerge(getRepository(), patchset.tip, ticket.mergeTo);
			if (!canMerge) {
				// commit can not be cleanly merged
				return new Fragment("mergePanel", "notMergeableFragment", this);
			} else {
				// user can merge
				Fragment mergePanel = new Fragment("mergePanel", "mergeableFragment", this);
				return mergePanel;
			}
		} else {
			if (ticket.isVetoed(patchset)) {
				// patchset has been vetoed
				return new Fragment("mergePanel", "vetoedFragment", this);
			} else if (reviewRequired) {
				// patchset has been not been approved for merge
				return new Fragment("mergePanel", "notApprovedFragment", this);
			} else {
				// user does not have merge permissions
				return new Label("mergePanel");
			}
		}
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
