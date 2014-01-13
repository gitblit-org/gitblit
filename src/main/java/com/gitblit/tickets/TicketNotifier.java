/*
 * Copyright 2014 gitblit.com.
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
package com.gitblit.tickets;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.Constants;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.models.Mailing;
import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.TicketModel.Field;
import com.gitblit.models.TicketModel.FieldChange;
import com.gitblit.models.TicketModel.Patchset;
import com.gitblit.models.TicketModel.PatchsetType;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.DiffUtils;
import com.gitblit.utils.DiffUtils.DiffStat;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.MarkdownUtils;
import com.gitblit.utils.StringUtils;

/**
 * Formats and queues ticket/patch notifications for dispatch to the
 * mail executor upon completion of a push or a ticket update.  Messages are
 * created as Markdown and then transformed to html.
 *
 * @author James Moger
 *
 */
public class TicketNotifier {

	protected final Map<Long, Mailing> queue = new TreeMap<Long, Mailing>();

	private final String SOFT_BRK = "\n";

	private final String HARD_BRK = "\n\n";

	private final String HR = "----\n\n";

	private final IStoredSettings settings;

	private final INotificationManager notificationManager;

	private final IUserManager userManager;

	private final IRepositoryManager repositoryManager;

	private final ITicketService ticketService;

	public TicketNotifier(
			IRuntimeManager runtimeManager,
			INotificationManager notificationManager,
			IUserManager userManager,
			IRepositoryManager repositoryManager,
			ITicketService ticketService) {

		this.settings = runtimeManager.getSettings();
		this.notificationManager = notificationManager;
		this.userManager = userManager;
		this.repositoryManager = repositoryManager;
		this.ticketService = ticketService;
	}

	public void sendAll() {
		for (Mailing mail : queue.values()) {
			notificationManager.send(mail);
		}
	}

	public void sendMailing(TicketModel ticket) {
		queueMailing(ticket);
		sendAll();
	}

	/**
	 * Queues an update notification.
	 *
	 * @param ticket
	 * @return a notification object used for testing
	 */
	public Mailing queueMailing(TicketModel ticket) {

		// format notification message
		String markdown = formatLastChange(ticket);

		StringBuilder html = new StringBuilder();
		html.append("<head>");
		html.append(readStyle());
		html.append("</head>");
		html.append("<body>");
		html.append(MarkdownUtils.transformGFM(settings, markdown, ticket.repository));
		html.append("</body>");

		Mailing mailing = Mailing.newHtml();
		mailing.from = getUserModel(ticket.updatedBy == null ? ticket.createdBy : ticket.updatedBy).getDisplayName();
		mailing.subject = getSubject(ticket);
		mailing.content = html.toString();
		mailing.id = "ticket." + ticket.number + "." + ticket.changeId;

		setRecipients(ticket, mailing);
		queue.put(ticket.number, mailing);

		return mailing;
	}

	protected String getSubject(TicketModel ticket) {
		Change lastChange = ticket.changes.get(ticket.changes.size() - 1);
		boolean newTicket = lastChange.isStatusChange() && ticket.changes.size() == 1;
		String re = newTicket ? "" : "Re: ";
		String subject = MessageFormat.format("{0}[{1}] {2} (#{3,number,0})",
				re, StringUtils.stripDotGit(ticket.repository), ticket.title, ticket.number);
		return subject;
	}

	protected String formatLastChange(TicketModel ticket) {
		Change lastChange = ticket.changes.get(ticket.changes.size() - 1);
		UserModel user = getUserModel(lastChange.createdBy);

		// define the fields we do NOT want to see in an email notification
		Set<TicketModel.Field> fieldExclusions = new HashSet<TicketModel.Field>();
		fieldExclusions.addAll(Arrays.asList(Field.number, Field.createdBy, Field.changeId, Field.type));

		StringBuilder sb = new StringBuilder();
		boolean newTicket = false;
		boolean isFastForward = true;
		List<RevCommit> commits = null;
		DiffStat diffstat = null;

		String pattern;
		if (lastChange.isStatusChange()) {
			Status state = lastChange.getStatus();
			switch (state) {
			case New:
				// new ticket
				newTicket = true;
				fieldExclusions.add(Field.status);
				fieldExclusions.add(Field.title);
				fieldExclusions.add(Field.body);
				if (lastChange.hasPatchset()) {
					pattern = "**{0}** is proposing a change.";
				} else {
					pattern = "**{0}** created this ticket.";
				}
				sb.append(MessageFormat.format(pattern, user.getDisplayName()));
				break;
			case Open:
				// reopened ticket
				pattern = "**{0}** re-opened this ticket.";
				sb.append(MessageFormat.format(pattern, user.getDisplayName()));
				break;
			default:
				if (StringUtils.isEmpty(ticket.mergeSha)) {
					// closed by user
					pattern = "**{0}** closed this ticket.";
					sb.append(MessageFormat.format(pattern, user.getDisplayName()));
				} else {
					// closed by push
					pattern = "**{0}** closed this ticket by pushing {1} to {2}.";

					// identify patch that closed the ticket
					String merged = ticket.mergeSha;
					for (Patchset patch : ticket.getPatchsets()) {
						if (patch.tip.equals(ticket.mergeSha)) {
							merged = "patch revision " + patch.rev;
							break;
						}
					}
					sb.append(MessageFormat.format(pattern, user.getDisplayName(), merged, ticket.mergeTo));
				}
				break;
			}
			sb.append(HARD_BRK);
		} else if (lastChange.hasPatchset()) {
			// patchset uploaded
			Patchset patch = lastChange.patch;
			String base;
			// determine the changed paths
			Repository repo = null;
			try {
				repo = repositoryManager.getRepository(ticket.repository);
				if (patch.rev > 1 && PatchsetType.FastForward == patch.type) {
					// fast-forward update, just show the new data
					isFastForward = true;
					Patchset prev = ticket.getPatchset(patch.rev - 1);
					base = prev.tip;
				} else {
					// proposal OR non-fast-forward update
					isFastForward = false;
					base = patch.base;
				}

				diffstat = DiffUtils.getDiffStat(repo, base, patch.tip);
				commits = JGitUtils.getRevLog(repo, base, patch.tip);
			} catch (Exception e) {
				Logger.getLogger(getClass()).error("failed to get changed paths", e);
			} finally {
				repo.close();
			}

			// describe the patchset
			pattern = "**{0}** uploaded patchset revision {1}.";
			sb.append(MessageFormat.format(pattern, user.getDisplayName(), patch.rev));
			sb.append(SOFT_BRK);
			sb.append(MessageFormat.format("{0} {1}, {2} {3}, <span class=\"insertions\">+{4} insertions</span>, <span class=\"deletions\">-{5} deletions</span> from merge base {6}.",
					commits.size(), commits.size() == 1 ? "commit" : "commits",
					diffstat.paths.size(),
					diffstat.paths.size() == 1 ? "file" : "files",
					patch.insertions, patch.deletions, patch.base));

			// note commit additions
			switch (lastChange.patch.type) {
			case FastForward:
			case Rebase:
				if (lastChange.patch.addedCommits > 0) {
					sb.append(SOFT_BRK);
					sb.append(MessageFormat.format("{0} {1} added.", lastChange.patch.addedCommits, lastChange.patch.addedCommits == 1 ? "commit" : "commits"));
				}
				break;
			default:
				break;
			}

			// describe patchset type
			switch (lastChange.patch.type) {
			case Proposal:
			case FastForward:
				sb.append("This revision is a FAST-FORWARD update.");
				break;
			case Rebase:
				sb.append(SOFT_BRK);
				sb.append("This revision has been REBASED.");
				break;
			case Rebase_Squash:
				sb.append(SOFT_BRK);
				sb.append("This revision has been REBASED and SQUASHED.");
				break;
			case Squash:
				sb.append(SOFT_BRK);
				sb.append("This revision has been SQUASHED.");
				break;
			case Amend:
				sb.append(SOFT_BRK);
				sb.append("This revision has been AMENDED.");
				break;
			}
			sb.append(HARD_BRK);
		}

		// ticket link
		String canonicalUrl = settings.getString(Keys.web.canonicalUrl, "https://localhost:8443");
		String href = MessageFormat.format("{0}/tickets?r={1}&h={2,number,0}", canonicalUrl, ticket.repository, ticket.number);
		String ticketLink = MessageFormat.format("[view ticket {0,number,0}]({1})", ticket.number, href);
		sb.append(ticketLink);
		sb.append(HARD_BRK);

		if (newTicket) {
			// ticket title
			sb.append(MessageFormat.format("### {0}", ticket.title));
			sb.append(HARD_BRK);

			// ticket description, on state change
			if (StringUtils.isEmpty(ticket.body)) {
				sb.append("<span class='note'>no description entered</span>");
			} else {
				sb.append(ticket.body);
			}
			sb.append(HARD_BRK);
			sb.append(HR);
		}

		// field changes
		if (lastChange.hasFieldChanges()) {
			List<FieldChange> filtered = new ArrayList<FieldChange>();
			for (FieldChange fc : lastChange.fields) {
				if (!fieldExclusions.contains(fc.field)) {
					// field is excluded from this formatting
					filtered.add(fc);
				}
			}

			// sort by field ordinal
			Collections.sort(filtered, new Comparator<FieldChange>() {
				@Override
				public int compare(FieldChange o1, FieldChange o2) {
					return o1.field.compareTo(o2.field);
				}
			});

			if (filtered.size() > 0) {
				sb.append(HARD_BRK);
				sb.append("| Field Changes               ||\n");
				sb.append("| ------------: | :----------- |\n");
				for (FieldChange fc : filtered) {
					String value = fc.value == null ? "" : fc.value.toString().replace("\n", "<br/>");
					sb.append(String.format("| **%1$s:** | %2$s |\n", fc.field.name(), value));
				}
				sb.append(HARD_BRK);
			}
		}

		// new comment
		if (lastChange.hasComment()) {
			sb.append(MessageFormat.format("**{0}** commented on this ticket.", user.getDisplayName()));
			sb.append(HARD_BRK);
			sb.append(HR);
			sb.append(lastChange.comment.text);
			sb.append(HARD_BRK);
		}

		// insert the patchset details and review instructions
		if (lastChange.hasPatchset() && ticket.isOpen()) {
			if (commits != null && commits.size() > 0) {
				// append the commit list
				String title = isFastForward ? "Commits added since last patchset revision" : "All commits in patchset";
				sb.append(MessageFormat.format("| {0} ||\n", title));
				sb.append("| SHA | Author | Title |\n");
				sb.append("| :-- | :----- | :---- |\n");
				for (RevCommit commit : commits) {
					sb.append(MessageFormat.format("| {0} | {1} | {2} |\n",
							commit.getName(), commit.getAuthorIdent().getName(),
							StringUtils.trimString(commit.getShortMessage(), Constants.LEN_SHORTLOG)));
				}
				sb.append(HARD_BRK);
			}

			if (diffstat != null) {
				// append the changed path list
				String title = isFastForward ? "Files changed since last patchset revision" : "All files changed in patchset";
				sb.append(MessageFormat.format("| {0} |||\n", title));
				sb.append("| T   | File |     |\n");
				sb.append("| :-- | :----------- | :-- |\n");
				for (PathChangeModel path : diffstat.paths) {
					sb.append(MessageFormat.format("| {0} | {1} | <span class=\"insertions\">+{2}</span>/<span class=\"deletions\">-{3}</span> |\n",
							path.changeType.name().charAt(0), path.name, path.insertions, path.deletions));
				}
				sb.append(HARD_BRK);
			}

			sb.append(formatPatchsetInstructions(ticket, lastChange.patch));
		}

		return sb.toString();
	}

	/**
	 * Generates patchset review instructions for command-line git
	 *
	 * @param patch
	 * @return instructions
	 */
	protected String formatPatchsetInstructions(TicketModel ticket, Patchset patch) {
		String canonicalUrl = settings.getString(Keys.web.canonicalUrl, "https://localhost:8443");
		String repositoryUrl = canonicalUrl + Constants.R_PATH + ticket.repository;
		String barnumPatchId = "" + ticket.number;

		String instructions = readResource("commands.md");
		instructions = instructions.replace("${patchId}", barnumPatchId);
		instructions = instructions.replace("${repositoryUrl}", repositoryUrl);
		instructions = instructions.replace("${patchRef}", patch.ref);
		instructions = instructions.replace("${reviewBranch}", "ticket/" + ticket.number);

		return instructions;
	}

	/**
	 * Gets the usermodel for the username.  Creates a temp model, if required.
	 *
	 * @param username
	 * @return a usermodel
	 */
	protected UserModel getUserModel(String username) {
		UserModel user = userManager.getUserModel(username);
		if (user == null) {
			// create a temporary user model (for unit tests)
			user = new UserModel(username);
		}
		return user;
	}

	/**
	 * Set the proper recipients for a ticket.
	 *
	 * @param ticket
	 * @param mailing
	 */
	protected void setRecipients(TicketModel ticket, Mailing mailing) {
		//
		// Direct TO recipients
		//
		Set<String> toAddresses = new TreeSet<String>();
		for (String name : ticket.getParticipants()) {
			UserModel user = userManager.getUserModel(name);
			if (user != null) {
				if (!StringUtils.isEmpty(user.emailAddress)) {
					toAddresses.add(user.emailAddress);
				}
			}
		}
		mailing.setRecipients(toAddresses);

		//
		// CC recipients
		//
		Set<String> ccs = new TreeSet<String>();

		// cc users mentioned in last comment
		Change lastChange = ticket.changes.get(ticket.changes.size() - 1);
		if (lastChange.hasComment()) {
			Pattern p = Pattern.compile("(?:^|\\s+)(@[A-Za-z0-9-_]+)");
			Matcher m = p.matcher(lastChange.comment.text);
			while (m.find()) {
				String username = m.group();
				ccs.add(username);
			}
		}

		// cc users who are watching the ticket
		ccs.addAll(ticket.getWatchers());

		// TODO cc users who are watching the repository

		Set<String> ccAddresses = new TreeSet<String>();
		for (String name : ccs) {
			UserModel user = userManager.getUserModel(name);
			if (user != null) {
				if (!StringUtils.isEmpty(user.emailAddress)) {
					ccAddresses.add(user.emailAddress);
				}
			}
		}

		// cc repository mailing list addresses
		RepositoryModel model = repositoryManager.getRepositoryModel(ticket.repository);
		if (!ArrayUtils.isEmpty(model.mailingLists)) {
			ccAddresses.addAll(model.mailingLists);
		}
		ccAddresses.addAll(settings.getStrings(Keys.mail.mailingLists));

		mailing.setCCs(ccAddresses);
	}

	protected String readStyle() {
		StringBuilder sb = new StringBuilder();
		sb.append("<style>\n");
		sb.append(readResource("email.css"));
		sb.append("</style>\n");
		return sb.toString();
	}

	protected String readResource(String resource) {
		StringBuilder sb = new StringBuilder();
		InputStream is = null;
		try {
			is = getClass().getResourceAsStream(resource);
			List<String> lines = IOUtils.readLines(is);
			for (String line : lines) {
				sb.append(line).append('\n');
			}
		} catch (IOException e) {

		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
				}
			}
		}
		return sb.toString();
	}
}
