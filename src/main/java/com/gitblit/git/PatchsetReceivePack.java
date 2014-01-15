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
package com.gitblit.git;

import static org.eclipse.jgit.transport.BasePackPushConnection.CAPABILITY_SIDE_BAND_64K;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.eclipse.jgit.transport.ReceivePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.Keys;
import com.gitblit.manager.IGitblit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.TicketModel.Field;
import com.gitblit.models.TicketModel.Patchset;
import com.gitblit.models.TicketModel.PatchsetType;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.models.UserModel;
import com.gitblit.tickets.ITicketService;
import com.gitblit.tickets.RepositoryTicketService;
import com.gitblit.tickets.TicketMilestone;
import com.gitblit.tickets.TicketNotifier;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.DiffUtils;
import com.gitblit.utils.DiffUtils.DiffStat;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.RefLogUtils;
import com.gitblit.utils.StringUtils;


/**
 * PatchsetReceivePack processes receive commands and allows for creating, updating,
 * and closing Gitblit tickets.  It also executes Groovy pre- and post- receive
 * hooks.
 *
 * The patchset mechanism defined in this class is based on the ReceiveCommits class
 * from the Gerrit code review server.
 *
 * The general execution flow is:
 * <ol>
 *    <li>onPreReceive()</li>
 *    <li>executeCommands()</li>
 *    <li>onPostReceive()</li>
 * </ol>
 *
 * @author Android Open Source Project
 * @author James Moger
 *
 */
public class PatchsetReceivePack extends GitblitReceivePack {

	protected static final List<String> MAGIC_REFS = Arrays.asList(Constants.R_FOR, Constants.R_TICKETS);

	protected static final Pattern NEW_PATCH =
		      Pattern.compile("^refs/changes/(?:[0-9a-zA-Z][0-9a-zA-Z]/)?([1-9][0-9]*)(?:/new)?$");

	protected static final FooterKey CHANGE_ID = new FooterKey("Change-Id");

	protected static final String TOPIC = "topic=";

	protected static final String ASSIGNEDTO = "r=";

	protected static final String WATCH = "cc=";

	protected static final String MILESTONE = "m=";

	private static final Logger LOGGER = LoggerFactory.getLogger(PatchsetReceivePack.class);

	protected final ITicketService ticketService;

	protected final TicketNotifier ticketNotifier;

	public PatchsetReceivePack(IGitblit gitblit, Repository db, RepositoryModel repository, UserModel user) {
		super(gitblit, db, repository, user);
		this.ticketService = gitblit.getTicketService();
		this.ticketNotifier = ticketService.createNotifier();
	}

	/** Returns the patchset ref root from the ref */
	private String getPatchsetRef(String refName) {
		for (String patchRef : MAGIC_REFS) {
			if (refName.startsWith(patchRef)) {
				return patchRef;
			}
		}
		return null;
	}

	/** Checks if the supplied ref name is a patchset ref */
	private boolean isPatchsetRef(String refName) {
		return !StringUtils.isEmpty(getPatchsetRef(refName));
	}

	/** Checks if the supplied ref name is a change ref */
	private boolean isChangeRef(String refName) {
		return refName.startsWith(Constants.R_CHANGES);
	}

	/** Extracts the integration branch from the ref name */
	private String getIntegrationBranch(String refName) {
		String patchsetRef = getPatchsetRef(refName);
		String branch = refName.substring(patchsetRef.length());
		if (branch.indexOf('%') > -1) {
			return branch.substring(0, branch.indexOf('%'));
		}
		return branch;
	}

	/** Extracts a ticket field from the ref name */
	private List<String> getOptions(ReceiveCommand cmd, String token) {
		String refName = cmd.getRefName();
		if (refName.indexOf('%') > -1) {
			List<String> list = new ArrayList<String>();
			String [] strings = refName.substring(refName.indexOf('%') + 1).split(",");
			for (String str : strings) {
				if (str.toLowerCase().startsWith(token)) {
					String val = str.substring(token.length());
					list.add(val);
				}
			}
			return list;
		}
		return null;
	}

	/** Extracts a ticket field from the ref name */
	private String getSingleOption(ReceiveCommand cmd, String token) {
		List<String> list = getOptions(cmd, token);
		if (list != null && list.size() > 0) {
			return list.get(0);
		}
		return null;
	}

	/** Returns true if the ref namespace exists */
	private boolean hasRefNamespace(String ref) {
		Map<String, Ref> blockingFors;
		try {
			blockingFors = getRepository().getRefDatabase().getRefs(ref);
		} catch (IOException err) {
			sendError("Cannot scan refs in {0}", repository.name);
			LOGGER.error("Error!", err);
			return true;
		}
		if (!blockingFors.isEmpty()) {
			sendError("{0} needs the following refs removed to receive patchsets: {1}",
					repository.name, blockingFors.keySet());
			return true;
		}
		return false;
	}

	/**
	 * Returns the current patchset revision for the specified ticketNumber.
	 * branch id index.
	 *
	 * @return the current patchset revision for the ticketNumber
	 */
	private int getCurrentRevision(long ticketNumber) {
		String refId = PatchsetCommand.getBaseRef(ticketNumber);
		int rev = 0;
		try {
			for (Ref r : getRepository().getRefDatabase().getRefs(Constants.R_CHANGES).values()) {
				if (r.getName().startsWith(refId)) {
					String id = r.getName().substring(refId.length());
					int ps = Integer.parseInt(id);
					if (ps > rev) {
						rev = ps;
					}
				}
			}
		} catch (IOException e) {
			LOGGER.error("failed to get change refs for " + repository.name, e);
		}
		return rev;
	}

	private String getTicketUrl(TicketModel ticket) {
		final String canonicalUrl = settings.getString(Keys.web.canonicalUrl, "https://localhost:8443");
		final String hrefPattern = "{0}/tickets?r={1}&h={2,number,0}";
		return MessageFormat.format(hrefPattern, canonicalUrl, ticket.repository, ticket.number);
	}

	/** Removes change ref receive commands */
	private List<ReceiveCommand> excludeChangeCommands(Collection<ReceiveCommand> commands) {
		List<ReceiveCommand> filtered = new ArrayList<ReceiveCommand>();
		for (ReceiveCommand cmd : commands) {
			if (!isChangeRef(cmd.getRefName())) {
				// this is not a change ref update
				filtered.add(cmd);
			}
		}
		return filtered;
	}

	/** Removes patchset receive commands for pre- and post- hook integrations */
	private List<ReceiveCommand> excludePatchsetCommands(Collection<ReceiveCommand> commands) {
		List<ReceiveCommand> filtered = new ArrayList<ReceiveCommand>();
		for (ReceiveCommand cmd : commands) {
			if (!isPatchsetRef(cmd.getRefName())) {
				// this is a non-patchset ref update
				filtered.add(cmd);
			}
		}
		return filtered;
	}

	/**	Process receive commands EXCEPT for Patchset commands. */
	@Override
	public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
		Collection<ReceiveCommand> filtered = excludePatchsetCommands(commands);
		super.onPreReceive(rp, filtered);
	}

	/**	Process receive commands EXCEPT for Patchset commands. */
	@Override
	public void onPostReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
		Collection<ReceiveCommand> filtered = excludePatchsetCommands(commands);
		super.onPostReceive(rp, filtered);

		// send all queued ticket notifications after processing all patchsets
		ticketNotifier.sendAll();
	}

	@Override
	protected void validateCommands() {
		// workaround for JGit's awful scoping choices
		//
		// set the patchset refs to OK to bypass checks in the super implementation
		for (final ReceiveCommand cmd : filterCommands(Result.NOT_ATTEMPTED)) {
			if (isPatchsetRef(cmd.getRefName())) {
				if (cmd.getType() == ReceiveCommand.Type.CREATE) {
					cmd.setResult(Result.OK);
				}
			}
		}

		super.validateCommands();
	}

	/** Execute commands to update references. */
	@Override
	protected void executeCommands() {
		// workaround for JGit's awful scoping choices
		//
		// reset the patchset refs to NOT_ATTEMPTED (see validateCommands)
		for (ReceiveCommand cmd : filterCommands(Result.OK)) {
			if (isPatchsetRef(cmd.getRefName())) {
				cmd.setResult(Result.NOT_ATTEMPTED);
			}
		}

		List<ReceiveCommand> toApply = filterCommands(Result.NOT_ATTEMPTED);
		if (toApply.isEmpty()) {
			return;
		}

		ProgressMonitor updating = NullProgressMonitor.INSTANCE;
		boolean sideBand = isCapabilityEnabled(CAPABILITY_SIDE_BAND_64K);
		if (sideBand) {
			SideBandProgressMonitor pm = new SideBandProgressMonitor(msgOut);
			pm.setDelayStart(250, TimeUnit.MILLISECONDS);
			updating = pm;
		}

		BatchRefUpdate batch = getRepository().getRefDatabase().newBatchUpdate();
		batch.setAllowNonFastForwards(isAllowNonFastForwards());
		batch.setRefLogIdent(getRefLogIdent());
		batch.setRefLogMessage("push", true);

		ReceiveCommand patchsetRefCmd = null;
		PatchsetCommand patchsetCmd = null;
		for (ReceiveCommand cmd : toApply) {
			if (Result.NOT_ATTEMPTED != cmd.getResult()) {
				// Already rejected by the core receive process.
				continue;
			}

			if (isPatchsetRef(cmd.getRefName())) {
				if (ticketService == null) {
					sendRejection(cmd, "Sorry, the ticket service is unavailable and can not accept patchsets at this time.");
					continue;
				}

				if (!ticketService.isReady()) {
					sendRejection(cmd, "Sorry, the ticket service can not accept patchsets at this time.");
					continue;
				}

				if (UserModel.ANONYMOUS.equals(user)) {
					// server allows anonymous pushes, but anonymous patchset
					// contributions are prohibited by design
					sendRejection(cmd, "Sorry, anonymous patchset contributions are prohibited.");
					continue;
				}

				final Matcher m = NEW_PATCH.matcher(cmd.getRefName());
				if (m.matches()) {
					// prohibit pushing directly to a change ref
					sendRejection(cmd, "You may not directly push patchsets to a change ref!");
					continue;
				}

				if (hasRefNamespace(Constants.R_FOR)) {
					// the refs/for/ namespace exists and it must not
					LOGGER.error("{} already has refs in the {} namespace",
							repository.name, Constants.R_FOR);
					sendRejection(cmd, "Sorry, a repository administrator will have to remove the {} namespace", Constants.R_FOR);
					continue;
				}

				if (patchsetRefCmd != null) {
					sendRejection(cmd, "You may only push one patchset at a time.");
					continue;
				}

				// assign to verification
				String assignedTo = getSingleOption(cmd, ASSIGNEDTO);
				if (!StringUtils.isEmpty(assignedTo)) {
					UserModel assignee = gitblit.getUserModel(assignedTo);
					if (assignee == null) {
						// no account by this name
						sendRejection(cmd, "{0} can not be assigned any tickets because there is no user account by that name", assignedTo);
						continue;
					} else if (!assignee.canPush(repository)) {
						// account does not have RW permissions
						sendRejection(cmd, "{0} ({1}) can not be assigned any tickets because the user does not have RW permissions for {2}",
								assignee.getDisplayName(), assignee.username, repository.name);
						continue;
					}
				}

				// milestone verification
				String milestone = getSingleOption(cmd, MILESTONE);
				if (!StringUtils.isEmpty(milestone)) {
					TicketMilestone milestoneModel = ticketService.getMilestone(repository.name, milestone);
					if (milestoneModel == null) {
						// milestone does not exist
						sendRejection(cmd, "Sorry, \"{0}\" is not a valid milestone!", milestone);
						continue;
					}
				}

				// watcher verification
				List<String> watchers = getOptions(cmd, WATCH);
				if (!ArrayUtils.isEmpty(watchers)) {
					for (String watcher : watchers) {
						UserModel user = gitblit.getUserModel(watcher);
						if (user == null) {
							// watcher does not exist
							sendRejection(cmd, "Sorry, \"{0}\" is not a valid username for the watch list!", watcher);
							continue;
						}
					}
				}

				patchsetRefCmd = cmd;
				patchsetCmd = preparePatchset(cmd);
				if (patchsetCmd != null) {
					// add the patchset revision ref (refs/changes/xx/n)
					batch.addCommand(patchsetCmd);

					// update ticket ref (refs/tickets/n)
					ReceiveCommand ticketCmd = new ReceiveCommand(cmd.getOldId(), cmd.getNewId(), Constants.R_TICKETS + patchsetCmd.ticketNumber);
					batch.addCommand(ticketCmd);
				}
				continue;
			}

			// reset ticket service caches
			if (ticketService != null && ticketService instanceof RepositoryTicketService) {
				ticketService.resetCaches();
			}

			batch.addCommand(cmd);
		}

		if (!batch.getCommands().isEmpty()) {
			try {
				batch.execute(getRevWalk(), updating);
			} catch (IOException err) {
				for (ReceiveCommand cmd : toApply) {
					if (cmd.getResult() == Result.NOT_ATTEMPTED) {
						sendRejection(cmd, "lock error: {0}", err.getMessage());
					}
				}
			}
		}

		//
		// set the results into the patchset ref receive command
		//
		if (patchsetRefCmd != null && patchsetCmd != null) {
			if (!patchsetCmd.getResult().equals(Result.OK)) {
				// patchset command failed!
				patchsetRefCmd.setResult(patchsetCmd.getResult(), patchsetCmd.getMessage());
			} else {
				// all patchset commands were applied
				patchsetRefCmd.setResult(Result.OK);
				TicketModel ticket = processPatchset(patchsetCmd);
				if (ticket != null) {
					ticketNotifier.queueMailing(ticket);
				}
			}
		}

		//
		// if there are standard ref update receive commands that were
		// successfully processed, process referenced tickets, if any
		//
		List<ReceiveCommand> allUpdates = ReceiveCommand.filter(batch.getCommands(), Result.OK);
		List<ReceiveCommand> refUpdates = excludePatchsetCommands(allUpdates);
		List<ReceiveCommand> stdUpdates = excludeChangeCommands(refUpdates);
		if (!stdUpdates.isEmpty()) {
			int ticketsProcessed = 0;
			for (ReceiveCommand cmd : stdUpdates) {
				switch (cmd.getType()) {
				case CREATE:
				case UPDATE:
				case UPDATE_NONFASTFORWARD:
					Collection<TicketModel> tickets = closeMergedTickets(cmd);
					ticketsProcessed += tickets.size();
					for (TicketModel ticket : tickets) {
						ticketNotifier.queueMailing(ticket);
					}
					break;
				default:
					break;
				}
			}

			if (ticketsProcessed == 1) {
				sendInfo("1 ticket updated");
			} else {
				sendInfo("{0} tickets updated", ticketsProcessed);
			}
		}
	}

	/**
	 * Prepares a patchset command.
	 *
	 * @param cmd
	 * @return the patchset command
	 */
	private PatchsetCommand preparePatchset(ReceiveCommand cmd) {
		String branch = getIntegrationBranch(cmd.getRefName());
		String defaultBranch = "master";
		try {
			defaultBranch = getRepository().getBranch();
		} catch (Exception e) {
			LOGGER.error("failed to determine default branch for " + repository.name, e);
		}

		// try to parse the branch spec as a ticket number
		long number = 0;
		try {
			number = Long.parseLong(branch);
		} catch (Exception e) {
		}

		TicketModel ticket = null;
		if (number > 0 && ticketService.hasTicket(repository.name, number)) {
			ticket = ticketService.getTicket(repository.name, number);
		}

		if (ticket == null) {
			if (number > 0) {
				// requested ticket does not exist
				sendError("Sorry, {0} does not have ticket {1,number,0}!", repository.name, number);
				sendRejection(cmd, "Invalid ticket number");
				return null;
			}
		} else {
			if (ticket.isMerged()) {
				// ticket already merged & resolved
				Change mergeChange = null;
				for (Change change : ticket.changes) {
					if (change.isMerge()) {
						mergeChange = change;
						break;
					}
				}
				sendError("Sorry, {0} already merged patchset revision {1} from ticket {2,number,0} to {3}!",
						mergeChange.createdBy, mergeChange.patch.rev, number, ticket.mergeTo);
				sendRejection(cmd, "Ticket {0,number,0} already resolved", number);
				return null;
			} else if (!StringUtils.isEmpty(ticket.mergeTo)) {
				// ticket specifies integration branch
				branch = ticket.mergeTo;
			} else {
				// use default branch
				branch = defaultBranch;
			}
		}


		final RevCommit tipCommit = JGitUtils.getCommit(getRepository(), cmd.getNewId().getName());
		final String forBranch = branch.equalsIgnoreCase("default") ? defaultBranch : branch;
		RevCommit mergeBase = null;
		Ref forBranchRef = getAdvertisedRefs().get(Constants.R_HEADS + forBranch);
		if (forBranchRef == null || forBranchRef.getObjectId() == null) {
			// unknown integration branch
			sendError("Sorry, there is no integration branch named ''{0}''.", forBranch);
			sendRejection(cmd, "Invalid integration branch specified");
			return null;
		} else {
			// determine the merge base for the patchset on the integration branch
			String base = JGitUtils.getMergeBase(getRepository(), forBranchRef.getObjectId(), tipCommit.getId());
			if (StringUtils.isEmpty(base)) {
				sendError("There is no common ancestry between {0} and {1}.",
						forBranchRef.getName(),
						StringUtils.trimString(cmd.getNewId().getName(), settings.getInteger(Keys.web.shortCommitIdLength, 6)));
				sendError("Please reconsider your proposed integration branch, {0}.", forBranch);
				sendRejection(cmd, "No merge base for patchset tip and {0}", forBranch);
				return null;
			}
			mergeBase = JGitUtils.getCommit(getRepository(), base);
		}

		// ensure that the patchset can be cleanly merged right now
		if (!JGitUtils.canMerge(getRepository(), tipCommit.getName(), forBranch)) {
			sendError("Your patchset can not be cleanly merged into {0}. Please merge or rebase.", forBranch);
			sendRejection(cmd, "Patchset not mergeable");
			return null;
		}

		// check to see if this commit is aleady linked to a ticket
		long id = getTicketNumber(tipCommit);
		if (id > 0) {
			if (ticket != null && id == ticket.number) {
				sendError("{0} is already linked to this ticket.", tipCommit.getName());
				sendRejection(cmd, "Commit already linked");
			} else {
				sendError("{0} is already linked to ticket {1,number,0}.", tipCommit.getName(), id);
				sendRejection(cmd, "Commit linked to another ticket");
			}
			return null;
		}

		int commitCount = countCommits(mergeBase.getName(), tipCommit.getName());

		PatchsetCommand psCmd;
		if (ticket == null) {
			/*
			 *  NEW TICKET
			 */
			int minLength = 10;
			int maxLength = 100;
			if (commitCount > 1) {
				sendError("To create a proposal ticket, please squash your commits and");
				sendError("provide a meaningful commit message with a short subject &");
				sendError("a description/body.");
				sendError("  minimum length of a title is {0} characters.", minLength);
				sendError("  maximum length of a title is {0} characters.", maxLength);
				sendRejection(cmd, "Must squash to one commit");
				return null;
			}

			// require a reasonable title/subject
			String title = tipCommit.getFullMessage().trim().split("\n")[0];
			if (title.length() < minLength) {
				// reject, title too short
				sendError("Please supply a longer title in your commit message!");
				sendError("  minimum length of a title is {0} characters.", minLength);
				sendError("  maximum length of a title is {0} characters.", maxLength);
				sendRejection(cmd, "Ticket title is too short [{0}/{1}]", title.length(), maxLength);
				return null;
			}
			if (title.length() > maxLength) {
				// reject, title too long
				sendError("Please supply a more concise title in your commit message!");
				sendError("  minimum length of a title is {0} characters.", minLength);
				sendError("  maximum length of a title is {0} characters.", maxLength);
				sendRejection(cmd, "Ticket title is too long [{0}/{1}]", title.length(), maxLength);
				return null;
			}

			psCmd = new PatchsetCommand(forBranch, tipCommit, "I" + tipCommit.getName());
			long ticketId = ticketService.assignTicketId(repository.name, psCmd.changeId);
			psCmd.ticketNumber = ticketId;
		} else {
			/*
			 *  EXISTING TICKET
			 */
			int rev = getCurrentRevision(ticket.number);
			psCmd = new PatchsetCommand(forBranch, tipCommit, ticket.changeId, ticket.number, rev + 1);

			Patchset curr = ticket.getCurrentPatchset();
			if (curr != null) {
				// patchset revision
				// qualify the revision
				int added = commitCount - curr.totalCommits;
				boolean squash = added < 0;
				boolean rebase = !curr.base.equals(mergeBase.getName());
				boolean amend = added == 0 && !rebase;

				PatchsetType type;
				if (rebase && squash) {
					type = PatchsetType.Rebase_Squash;
				} else if (squash) {
					type = PatchsetType.Squash;
				} else if (rebase) {
					type = PatchsetType.Rebase;
				} else if (amend) {
					type = PatchsetType.Amend;
				} else {
					type = PatchsetType.FastForward;
				}
				psCmd.patchset.type = type;
				if (added > 0) {
					psCmd.patchset.addedCommits = added;
				}
			}
		}

		// confirm user can push the patchset
		boolean canPush = ticket == null
				|| ticket.isAuthor(user.username)
				|| ticket.isAssignedTo(user.username)
				|| ticket.isReviewer(user.username)
				|| ticket.isPatchsetAuthor(user.username)
				|| user.canPush(repository);

		switch (psCmd.patchset.type) {
		case Proposal:
			// initial contributions are always acceptable
			break;
		case FastForward:
			// patchset updates must be permitted
			if (!canPush) {
				// reject
				sendRejection(cmd, "You do not have patchset update permissions for ticket {0}", ticket.number);
				return null;
			}
			break;
		default:
			// non-fast-forward push
			if (canPush) {
				// permit
				sendInfo("Accepting non-fast-forward patchset {0,number,0}/{1} ({2})",
						ticket.number, psCmd.patchset.rev, psCmd.patchset.type);
			} else {
				// reject
				sendRejection(cmd, "non-fast-forward ({0})", psCmd.patchset.type);
				return null;
			}
			break;
		}

		// update the patchset command metadata
		psCmd.patchset.base = mergeBase.getName();
		psCmd.patchset.totalCommits = commitCount;

		DiffStat diffstat = DiffUtils.getDiffStat(getRepository(), mergeBase, tipCommit, null);
		psCmd.patchset.insertions = diffstat.getInsertions();
		psCmd.patchset.deletions = diffstat.getDeletions();
		psCmd.assignChangeRef();

		psCmd.milestone = getSingleOption(cmd, MILESTONE);
		psCmd.assignedTo = getSingleOption(cmd, ASSIGNEDTO);
		psCmd.topic = getSingleOption(cmd, TOPIC);
		psCmd.watchers = getOptions(cmd, WATCH);

		return psCmd;
	}

	/**
	 * Creates or updates an ticket with the specified patchset.
	 *
	 * @param cmd
	 * @param milestone
	 * @param assignedTo
	 * @param topic
	 * @return a ticket if the creation or update was successful
	 */
	private TicketModel processPatchset(PatchsetCommand cmd) {
		if (cmd.isNewTicket()) {
			// create the ticket object
			Change change = cmd.asNewChange(user.username);
			change.setField(Field.repository, repository.name);

			TicketModel ticket = ticketService.createTicket(change);
			if (ticket != null) {

				sendHeader("#{0,number,0}: {1}", ticket.number, StringUtils.trimString(ticket.title, Constants.LEN_SHORTLOG));
				sendInfo("created proposal ticket from patchset", cmd.ticketNumber);
				sendInfo(getTicketUrl(ticket));
				sendInfo("");

				// log the new patch ref
				RefLogUtils.updateRefLog(user, getRepository(),
						Arrays.asList(new ReceiveCommand(ObjectId.zeroId(), cmd.getNewId(), cmd.getRefName())));

				return ticket;
			} else {
				sendError("FAILED to create ticket {0,number,0}", cmd.ticketNumber);
			}
		} else {
			// update an existing ticket
			TicketModel ticket = ticketService.getTicket(repository.name, cmd.ticketNumber);
			Change change = cmd.asUpdateChange(user.username, ticket);

			// update ticket with the patchset reference
			ticket = ticketService.updateTicket(repository.name, cmd.ticketNumber, change);
			if (ticket != null) {
				sendHeader("#{0,number,0}: {1}", ticket.number, StringUtils.trimString(ticket.title, Constants.LEN_SHORTLOG));
				sendInfo("uploaded patchset revision {0,number,0}", cmd.patchset.rev);
				sendInfo(getTicketUrl(ticket));
				sendInfo("");

				// log the new patchset ref
				RefLogUtils.updateRefLog(user, getRepository(),
						Arrays.asList(new ReceiveCommand(ObjectId.zeroId(), cmd.getNewId(), cmd.getRefName())));

				// return the updated ticket
				return ticket;
			} else {
				sendError("FAILED to upload patchset {0,number,0} for ticket {1,number,0}",
						cmd.patchset.rev, cmd.ticketNumber);
			}
		}

		return null;
	}

	/**
	 * Automatically closes open tickets that have been merged to their integration
	 * branch by a client.
	 *
	 * @param cmd
	 */
	private Collection<TicketModel> closeMergedTickets(ReceiveCommand cmd) {
		Map<Long, TicketModel> mergedTickets = new LinkedHashMap<Long, TicketModel>();
		final RevWalk rw = getRevWalk();
		try {
			rw.reset();
			rw.markStart(rw.parseCommit(cmd.getNewId()));
			if (!ObjectId.zeroId().equals(cmd.getOldId())) {
				rw.markUninteresting(rw.parseCommit(cmd.getOldId()));
			}

			RevCommit c;
			while ((c = rw.next()) != null) {
				rw.parseBody(c);
				long ticketNumber = getTicketNumber(c);
				if (ticketNumber == 0L || mergedTickets.containsKey(ticketNumber)) {
					continue;
				}

				TicketModel ticket = ticketService.getTicket(repository.name, ticketNumber);
				String integrationBranch = Constants.R_HEADS + ticket.mergeTo;

				// ticket must be open and the received ref must match the integration branch
				if (ticket.isClosed() || !integrationBranch.equals(cmd.getRefName())) {
					continue;
				}

				String baseRef = PatchsetCommand.getBaseRef(ticket.number);
				String commitRef = null;
				Set<Ref> refs = getRepository().getAllRefsByPeeledObjectId().get(c.getId());
				if (refs != null) {
					for (Ref ref : refs) {
						if (ref.getName().startsWith(baseRef)) {
							commitRef = ref.getName();
							break;
						}
					}
				}

				Change change = new Change(user.username);
				change.setField(Field.status, Status.Merged);
				change.setField(Field.mergeSha, c.getName());
				if (StringUtils.isEmpty(ticket.assignedTo)) {
					// unassigned tickets are assigned to the closer
					change.setField(Field.assignedTo, user.username);
				}

				if (commitRef == null) {
					// new patchset, create a patch
					int rev = getCurrentRevision(ticket.number);
					String newRef = PatchsetCommand.getChangeRef(ticket.number, rev + 1);
					commitRef = newRef;

					JGitUtils.setBranchRef(getRepository(), newRef, c.getName());

					String tip = c.getName();
					String base = cmd.getOldId().getName();
					DiffStat diffStat = DiffUtils.getDiffStat(getRepository(), c);
					int tc = countCommits(base, tip);

					Patchset patchset = new Patchset();
					patchset.rev = rev + 1;
					patchset.tip = tip;
					patchset.base = base;
					patchset.insertions = diffStat.getInsertions();
					patchset.deletions = diffStat.getDeletions();
					patchset.totalCommits = tc;
					patchset.type = PatchsetType.FastForward;
					patchset.ref = newRef;

					change.patch = patchset;
				}

				String shortid = StringUtils.trimString(c.getName(), settings.getInteger(Keys.web.shortCommitIdLength, 6));
				ticket = ticketService.updateTicket(repository.name, ticket.changeId, change);
				if (ticket != null) {
					sendHeader("#{0,number,0}: {1}", ticket.number, StringUtils.trimString(ticket.title, Constants.LEN_SHORTLOG));
					sendInfo("closed by push of {0} to {1} ({2})", shortid, ticket.mergeTo, commitRef);
					sendInfo(getTicketUrl(ticket));
					sendInfo("");
					mergedTickets.put(ticket.number, ticket);
				} else {
					sendError("FAILED to close ticket {0,number,0} by push of {1} ({2})", ticketNumber, shortid, commitRef);
				}
			}
		} catch (IOException e) {
			LOGGER.error("Can't scan for changes to close", e);
		} finally {
			rw.reset();
		}

		return mergedTickets.values();
	}

	private long getTicketNumber(RevCommit c) {
		// try lookup by change ref
		Map<AnyObjectId, Set<Ref>> map = getRepository().getAllRefsByPeeledObjectId();
		Set<Ref> refs = map.get(c.getId());
		if (!ArrayUtils.isEmpty(refs)) {
			for (Ref ref : refs) {
				long number = PatchsetCommand.getTicketNumber(ref.getName());
				if (number > 0) {
					return number;
				}
			}
		}

		// try lookup by change-id
		List<String> changeIds = c.getFooterLines(CHANGE_ID);
		if (!ArrayUtils.isEmpty(changeIds)) {
			for (String changeId : changeIds) {
				if (ticketService.hasTicket(repository.name, changeId)) {
					return ticketService.getTicketId(repository.name, changeId);
				}
			}
		}

		// TODO parse commit message looking for fixes/closes #n
		return 0L;
	}

	private int countCommits(String baseId, String tipId) {
		int count = 0;
		RevWalk walk = getRevWalk();
		walk.reset();
		walk.sort(RevSort.TOPO);
		walk.sort(RevSort.REVERSE, true);
		try {
			RevCommit tip = walk.parseCommit(getRepository().resolve(tipId));
			RevCommit base = walk.parseCommit(getRepository().resolve(baseId));
			walk.markStart(tip);
			walk.markUninteresting(base);
			for (;;) {
				RevCommit c = walk.next();
				if (c == null) {
					break;
				}
				count++;
			}
		} catch (IOException e) {
			// Should never happen, the core receive process would have
			// identified the missing object earlier before we got control.
			LOGGER.error("failed to get commit count", e);
			return 0;
		} finally {
			walk.release();
		}
		return count;
	}
}
