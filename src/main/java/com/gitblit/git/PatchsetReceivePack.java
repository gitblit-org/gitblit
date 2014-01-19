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
import org.eclipse.jgit.lib.RefUpdate;
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
	private int getCurrentRevisionRef(long ticketNumber) {
		String refId = PatchsetCommand.getBaseChangeRef(ticketNumber);
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
				String assignedTo = PatchsetCommand.getSingleOption(cmd, PatchsetCommand.ASSIGNEDTO);
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
				String milestone = PatchsetCommand.getSingleOption(cmd, PatchsetCommand.MILESTONE);
				if (!StringUtils.isEmpty(milestone)) {
					TicketMilestone milestoneModel = ticketService.getMilestone(repository.name, milestone);
					if (milestoneModel == null) {
						// milestone does not exist
						sendRejection(cmd, "Sorry, \"{0}\" is not a valid milestone!", milestone);
						continue;
					}
				}

				// watcher verification
				List<String> watchers = PatchsetCommand.getOptions(cmd, PatchsetCommand.WATCH);
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
					// add the patchset revision change ref (refs/changes/xx/n)
					batch.addCommand(patchsetCmd);
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

				if (!patchsetRefCmd.getRefName().startsWith(Constants.R_TICKETS)) {
					// pushed using refs/for/n, reset the ticket head
					String ticketRef = Constants.R_TICKETS + patchsetCmd.getTicketNumber();
					updateRef(ticketRef, patchsetCmd.getNewId());
				}

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
					Collection<TicketModel> tickets = processMergedTickets(cmd);
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
						mergeChange.createdBy, mergeChange.patchset.rev, number, ticket.mergeTo);
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

		final int shortCommitIdLen = settings.getInteger(Keys.web.shortCommitIdLength, 6);
		final String shortTipId = cmd.getNewId().getName().substring(0, shortCommitIdLen);
		final RevCommit tipCommit = JGitUtils.getCommit(getRepository(), cmd.getNewId().getName());
		final String forBranch = (branch.equalsIgnoreCase("default") || branch.equalsIgnoreCase("new")) ? defaultBranch : branch;
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
				sendError("");
				sendError("There is no common ancestry between {0} and {1}.", forBranch, shortTipId);
				sendError("Please reconsider your proposed integration branch, {0}.", forBranch);
				sendError("");
				sendRejection(cmd, "no merge base for patchset and {0}", forBranch);
				return null;
			}
			mergeBase = JGitUtils.getCommit(getRepository(), base);
		}

		// ensure that the patchset can be cleanly merged right now
		if (!JGitUtils.canMerge(getRepository(), tipCommit.getName(), forBranch)) {
			sendError("");
			sendError("Your patchset can not be cleanly merged into {0}.", forBranch);
			sendError("Please rebase your patchset and push again.");
			sendError("NOTE:", number);
			sendError("You should push your rebase to refs/for/{0,number,0}", number);
			sendError("");
			sendError("  git push origin HEAD:refs/for/{0,number,0}", number);
			sendError("");
			sendRejection(cmd, "patchset not mergeable");
			return null;
		}

		// check to see if this commit is already linked to a ticket
		long id = identifyTicket(tipCommit, false);
		if (id > 0) {
			sendError("{0} has already been pushed to ticket {1,number,0}.", shortTipId, id);
			sendRejection(cmd, "everything up-to-date");
			return null;
		}

		PatchsetCommand psCmd;
		if (ticket == null) {
			/*
			 *  NEW TICKET
			 */
			Patchset patchset = newPatchset(null, mergeBase.getName(), tipCommit.getName());

			int minLength = 10;
			int maxLength = 100;
			String minTitle = MessageFormat.format("  minimum length of a title is {0} characters.", minLength);
			String maxTitle = MessageFormat.format("  maximum length of a title is {0} characters.", maxLength);

			if (patchset.totalCommits > 1) {
				sendError("");
				sendError("To create a proposal ticket, please squash your commits and");
				sendError("provide a meaningful commit message with a short title &");
				sendError("an optional description/body.");
				sendError("");
				sendError(minTitle);
				sendError(maxTitle);
				sendError("");
				sendRejection(cmd, "please squash to one commit");
				return null;
			}

			// require a reasonable title/subject
			String title = tipCommit.getFullMessage().trim().split("\n")[0];
			if (title.length() < minLength) {
				// reject, title too short
				sendError("");
				sendError("Please supply a longer title in your commit message!");
				sendError("");
				sendError(minTitle);
				sendError(maxTitle);
				sendError("");
				sendRejection(cmd, "ticket title is too short [{0}/{1}]", title.length(), maxLength);
				return null;
			}
			if (title.length() > maxLength) {
				// reject, title too long
				sendError("");
				sendError("Please supply a more concise title in your commit message!");
				sendError("");
				sendError(minTitle);
				sendError(maxTitle);
				sendError("");
				sendRejection(cmd, "ticket title is too long [{0}/{1}]", title.length(), maxLength);
				return null;
			}

			// assign new id
			String changeId = "I" + tipCommit.getName();
			long ticketId = ticketService.assignTicketId(repository.name, changeId);

			// create the patchset command
			psCmd = new PatchsetCommand(user.username, patchset);
			psCmd.newTicket(tipCommit, forBranch, ticketId, cmd.getRefName());
		} else {
			/*
			 *  EXISTING TICKET
			 */
			Patchset patchset = newPatchset(ticket, mergeBase.getName(), tipCommit.getName());
			psCmd = new PatchsetCommand(user.username, patchset);
			psCmd.updateTicket(tipCommit, forBranch, ticket, cmd.getRefName());
		}

		// confirm user can push the patchset
		boolean pushPermitted = ticket == null
				|| ticket.isAuthor(user.username)
				|| ticket.isAssignedTo(user.username)
				|| ticket.isReviewer(user.username)
				|| ticket.isPatchsetAuthor(user.username)
				|| user.canPush(repository);

		switch (psCmd.getPatchsetType()) {
		case Proposal:
			// proposals (first patchset) are always acceptable
			break;
		case FastForward:
			// patchset updates must be permitted
			if (!pushPermitted) {
				// reject
				sendError("");
				sendError("To push a patchset to this ticket one of the following must be true:");
				sendError("  1. you created the ticket");
				sendError("  2. you created the first patchset");
				sendError("  3. you are listed as a reviewer for the ticket");
				sendError("  4. you have push (RW) permission to {0}", repository.name);
				sendError("");
				sendRejection(cmd, "not permitted to push to ticket {0,number,0}", ticket.number);
				return null;
			}
			break;
		default:
			// non-fast-forward push
			if (!pushPermitted) {
				// reject
				sendRejection(cmd, "non-fast-forward ({0})", psCmd.getPatchsetType());
				return null;
			}
			break;
		}
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
		Change change = cmd.getChange();

		if (cmd.isNewTicket()) {
			// create the ticket object
			TicketModel ticket = ticketService.createTicket(repository.name, change);
			if (ticket != null) {
				sendInfo("");
				sendHeader("#{0,number,0}: {1}", ticket.number, StringUtils.trimString(ticket.title, Constants.LEN_SHORTLOG));
				sendInfo("created proposal ticket from patchset");
				sendInfo(ticketService.getTicketUrl(ticket));
				sendInfo("");

				// log the new patch ref
				RefLogUtils.updateRefLog(user, getRepository(),
						Arrays.asList(new ReceiveCommand(ObjectId.zeroId(), cmd.getNewId(), cmd.getRefName())));

				return ticket;
			} else {
				sendError("FAILED to create ticket");
			}
		} else {
			// update an existing ticket
			TicketModel ticket = ticketService.updateTicket(repository.name, cmd.getTicketNumber(), change);
			if (ticket != null) {
				sendInfo("");
				sendHeader("#{0,number,0}: {1}", ticket.number, StringUtils.trimString(ticket.title, Constants.LEN_SHORTLOG));
				sendInfo("uploaded patchset revision {0,number,0} ({1})", cmd.getPatchsetRevision(), cmd.getPatchsetType().toString());
				sendInfo(ticketService.getTicketUrl(ticket));
				sendInfo("");

				// log the new patchset ref
				RefLogUtils.updateRefLog(user, getRepository(),
						Arrays.asList(new ReceiveCommand(ObjectId.zeroId(), cmd.getNewId(), cmd.getRefName())));

				// return the updated ticket
				return ticket;
			} else {
				sendError("FAILED to upload patchset {0,number,0} for ticket {1,number,0}",
						cmd.getPatchsetRevision(), cmd.getTicketNumber());
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
	private Collection<TicketModel> processMergedTickets(ReceiveCommand cmd) {
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
				long ticketNumber = identifyTicket(c, true);
				if (ticketNumber == 0L || mergedTickets.containsKey(ticketNumber)) {
					continue;
				}

				TicketModel ticket = ticketService.getTicket(repository.name, ticketNumber);
				String integrationBranch;
				if (StringUtils.isEmpty(ticket.mergeTo)) {
					// unspecified integration branch
					integrationBranch = null;
				} else {
					// specified integration branch
					integrationBranch = Constants.R_HEADS + ticket.mergeTo;
				}

				// ticket must be open and, if specified, the ref must match the integration branch
				if (ticket.isClosed() || (integrationBranch != null && !integrationBranch.equals(cmd.getRefName()))) {
					continue;
				}

				String baseRef = PatchsetCommand.getBaseChangeRef(ticket.number);
				boolean knownPatchset = false;
				Set<Ref> refs = getRepository().getAllRefsByPeeledObjectId().get(c.getId());
				if (refs != null) {
					for (Ref ref : refs) {
						if (ref.getName().startsWith(baseRef)) {
							knownPatchset = true;
							break;
						}
					}
				}

				String mergeSha = c.getName();
				String mergeTo = Repository.shortenRefName(cmd.getRefName());
				Change change;
				Patchset patchset;
				if (knownPatchset) {
					// identify merged patchset by the patchset tip
					patchset = null;
					for (Patchset ps : ticket.getPatchsets()) {
						if (ps.tip.equals(mergeSha)) {
							patchset = ps;
							break;
						}
					}

					if (patchset == null) {
						// should not happen - unless ticket has been hacked
						sendError("Failed to find the patchset for {0} in ticket {1,number,0}?!",
								mergeSha, ticket.number);
						continue;
					}

					// create a new change
					change = new Change(user.username);
				} else {
					// new patchset pushed by user
					String base = cmd.getOldId().getName();
					patchset = newPatchset(ticket, base, mergeSha);
					PatchsetCommand psCmd = new PatchsetCommand(user.username, patchset);
					psCmd.updateTicket(c, mergeTo, ticket, null);

					// create a change ref and update the tickets/n ref
					updateRef(psCmd.getRefName(), c.getId());
					updateRef(Constants.R_TICKETS + ticket.number, c.getId());

					// create a change from the patchset command
					change = psCmd.getChange();
				}

				// set the common change data about the merge
				change.setField(Field.status, Status.Merged);
				change.setField(Field.mergeSha, mergeSha);
				change.setField(Field.mergeTo, mergeTo);

				if (StringUtils.isEmpty(ticket.assignedTo)) {
					// unassigned tickets are assigned to the closer
					change.setField(Field.assignedTo, user.username);
				}

				ticket = ticketService.updateTicket(repository.name, ticket.number, change);
				if (ticket != null) {
					sendInfo("");
					sendHeader("#{0,number,0}: {1}", ticket.number, StringUtils.trimString(ticket.title, Constants.LEN_SHORTLOG));
					sendInfo("closed by push of patchset revision {0,number,0} to {1}", patchset.rev, mergeTo);
					sendInfo(ticketService.getTicketUrl(ticket));
					sendInfo("");
					mergedTickets.put(ticket.number, ticket);
				} else {
					String shortid = mergeSha.substring(0, settings.getInteger(Keys.web.shortCommitIdLength, 6));
					sendError("FAILED to close ticket {0,number,0} by push of {1}", ticketNumber, shortid);
				}
			}
		} catch (IOException e) {
			LOGGER.error("Can't scan for changes to close", e);
		} finally {
			rw.reset();
		}

		return mergedTickets.values();
	}

	/**
	 * Try to identify a ticket id from the commit.
	 *
	 * @param commit
	 * @param parseMessage
	 * @return a ticket id or 0
	 */
	private long identifyTicket(RevCommit commit, boolean parseMessage) {
		// try lookup by change ref
		Map<AnyObjectId, Set<Ref>> map = getRepository().getAllRefsByPeeledObjectId();
		Set<Ref> refs = map.get(commit.getId());
		if (!ArrayUtils.isEmpty(refs)) {
			for (Ref ref : refs) {
				long number = PatchsetCommand.getTicketNumber(ref.getName());
				if (number > 0) {
					return number;
				}
			}
		}

		if (parseMessage) {
			// parse commit message looking for fixes/closes #n
			Pattern p = Pattern.compile("(?:fixes|closes)[\\s-]+#?(\\d+)", Pattern.CASE_INSENSITIVE);
			Matcher m = p.matcher(commit.getFullMessage());
			while (m.find()) {
				String val = m.group();
				return Long.parseLong(val);
			}
		}
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

	/**
	 * Creates a new patchset with metadata.
	 *
	 * @param ticket
	 * @param newMergeBase
	 * @param newTip
	 */
	private Patchset newPatchset(TicketModel ticket, String newMergeBase, String newTip) {
		int totalCommits = countCommits(newMergeBase, newTip);
		DiffStat diffStat = DiffUtils.getDiffStat(getRepository(), newMergeBase, newTip);

		Patchset newPatchset = new Patchset();
		newPatchset.tip = newTip;
		newPatchset.base = newMergeBase;
		newPatchset.insertions = diffStat.getInsertions();
		newPatchset.deletions = diffStat.getDeletions();
		newPatchset.totalCommits = totalCommits;

		Patchset currPatchset = ticket == null ? null : ticket.getCurrentPatchset();
		if (currPatchset == null) {
			// ticket had no patchsets
			newPatchset.rev = 1;
			newPatchset.type = PatchsetType.Proposal;
		} else {
			// ticket has patchsets
			//
			// ensure we use the highest rev for this ticket by
			// checking both the change refs (in case they were deleted)
			// and the ticket object (in case it's been manipulated)
			int revRef = getCurrentRevisionRef(ticket.number);
			int revTkt = currPatchset.rev;
			newPatchset.rev = Math.max(revRef, revTkt) + 1;

			int added = totalCommits - currPatchset.totalCommits;
			boolean ff = JGitUtils.isMergedInto(getRepository(), currPatchset.tip, newTip);
			boolean squash = added < 0;
			boolean rebase = !currPatchset.base.equals(newMergeBase);

			PatchsetType type;
			if (ff) {
				type = PatchsetType.FastForward;
			} else if (rebase && squash) {
				type = PatchsetType.Rebase_Squash;
			} else if (squash) {
				type = PatchsetType.Squash;
			} else if (rebase) {
				type = PatchsetType.Rebase;
			} else {
				type = PatchsetType.Amend;
			}
			newPatchset.type = type;

			if (added > 0) {
				// ignore squash (negative add)
				newPatchset.addedCommits = added;
			}
		}
		return newPatchset;
	}

	private boolean updateRef(String ref, ObjectId newId) {
		ObjectId ticketRefId = ObjectId.zeroId();
		try {
			ticketRefId = getRepository().resolve(ref);
		} catch (Exception e) {
			// ignore
		}

		try {
			RefUpdate ru = getRepository().updateRef(ref,  false);
			ru.setRefLogIdent(getRefLogIdent());
			ru.setForceUpdate(true);
			ru.setExpectedOldObjectId(ticketRefId);
			ru.setNewObjectId(newId);
			RefUpdate.Result result = ru.update(getRevWalk());
			if (result == RefUpdate.Result.LOCK_FAILURE) {
				sendError("Failed to obtain lock when updating {0}:{1}", repository.name, ref);
				sendError("Perhaps an administrator should remove {0}/{1}.lock?", getRepository().getDirectory(), ref);
				return false;
			}
			return true;
		} catch (IOException e) {
			LOGGER.error("failed to update ref " + ref, e);
			sendError("There was an error updating ref {0}:{1}", repository.name, ref);
		}
		return false;
	}
}
