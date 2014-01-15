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

import java.nio.charset.Charset;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.RawParseUtils;

import com.gitblit.Constants;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.TicketModel.Field;
import com.gitblit.models.TicketModel.Patchset;
import com.gitblit.models.TicketModel.PatchsetType;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;

/**
 *
 * A subclass of ReceiveCommand with information about the patchset.
 *
 * @author James Moger
 *
 */
public class PatchsetCommand extends ReceiveCommand {

	final Patchset patchset;
	final RevCommit tip;
	final String mergeTo;
	final String changeId;
	final boolean isNew;
	long ticketNumber;
	String milestone;
	String assignedTo;
	String topic;
	List<String> watchers;

	public static String getBaseRef(long ticketNumber) {
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.R_CHANGES);
		long m = ticketNumber % 100L;
		if (m < 10) {
			sb.append('0');
		}
		sb.append(m);
		sb.append('/');
		sb.append(ticketNumber);
		sb.append('/');
		return sb.toString();
	}

	public static String getChangeRef(long ticketNumber, int revision) {
		return getBaseRef(ticketNumber) + revision;
	}

	public static long getTicketNumber(String ref) {
		if (!ref.startsWith(Constants.R_CHANGES)) {
			return 0L;
		}
		// strip changes ref
		String p = ref.substring(Constants.R_CHANGES.length());
		// strip shard id
		p = p.substring(p.indexOf('/') + 1);
		// strip revision
		p = p.substring(0, p.indexOf('/'));
		// parse ticket number
		return Long.parseLong(p);
	}

	public PatchsetCommand(String integrationBranch, RevCommit commit, String changeId) {
		this(integrationBranch, commit, changeId, 0, 1);
		this.patchset.type = PatchsetType.Proposal;
	}

	public PatchsetCommand(String integrationBranch, RevCommit commit, String changeId, long ticketNumber, int revision) {
		super(ObjectId.zeroId(), commit.getId(), null);
		this.mergeTo = integrationBranch;
		this.tip = commit;
		this.changeId = changeId;
		this.isNew = ticketNumber == 0L;
		this.ticketNumber = ticketNumber;

		this.patchset = new Patchset();
		this.patchset.rev = revision;
		this.patchset.tip = commit.getName();
	}

	public boolean isNewTicket() {
		return isNew;
	}

	public void assignChangeRef() {
		this.patchset.ref = getChangeRef(ticketNumber, patchset.rev);
	}

	@Override
	public String getRefName() {
		if (patchset.ref == null) {
			assignChangeRef();
		}
		return patchset.ref;
	}

	public String getTitle() {
		String title = tip.getShortMessage();
		return title;
	}

	/**
	 * Returns the body of the commit message
	 *
	 * @return
	 */
	public String getBody() {
		final byte[] raw = tip.getRawBuffer();
		int bodyEnd = raw.length - 1;
		while (raw[bodyEnd] == '\n') {
			// trim any trailing LFs, not interesting
			bodyEnd--;
		}

		final int messageBegin = RawParseUtils.commitMessage(raw, 0);
		if (messageBegin < 0) {
			return "";
		}
		for (;;) {
			bodyEnd = RawParseUtils.prevLF(raw, bodyEnd);
			if (bodyEnd <= messageBegin) {
				// Don't parse commit headers as footer lines.
				break;
			}
			final int keyStart = bodyEnd + 2;
			if (raw[keyStart] == '\n') {
				// Stop at first paragraph break, no footers above it.
				bodyEnd += 2;
				break;
			}
		}

		final Charset enc = RawParseUtils.parseEncoding(raw);
		final int titleEnd = RawParseUtils.endOfParagraph(raw, messageBegin);
		if (titleEnd < bodyEnd) {
			String body = RawParseUtils.decode(enc, raw, titleEnd, bodyEnd);
			return body.trim();
		}
		return "";
	}

	public Change asNewChange(String username) {
		Change change = new Change(username);
		change.patch = patchset;
		change.setField(Field.number, ticketNumber);
		change.setField(Field.changeId, changeId);
		change.setField(Field.title, getTitle());
		change.setField(Field.body, getBody());
		change.setField(Field.status, Status.New);
		change.setField(Field.type, TicketModel.Type.Proposal);
		change.setField(Field.mergeTo, mergeTo);

		Set<String> watchSet = new TreeSet<String>();
		watchSet.add(username);
		if (!ArrayUtils.isEmpty(watchers)) {
			for (String cc : watchers) {
				watchSet.add(cc.toLowerCase());
			}
		}

		if (!StringUtils.isEmpty(milestone)) {
			// user provided milestone
			change.setField(Field.milestone, milestone);
		}

		if (!StringUtils.isEmpty(assignedTo)) {
			// user provided assigned to
			change.setField(Field.assignedTo, assignedTo);
			watchSet.add(assignedTo);
		}

		if (!StringUtils.isEmpty(topic)) {
			// user provided topic
			change.setField(Field.topic, topic);
		}

		// set the watchers
		change.watch(watchSet.toArray(new String[watchSet.size()]));

		return change;
	}

	public Change asUpdateChange(String username, TicketModel ticket) {
		Change change = new Change(username);
		change.patch = patchset;

		Set<String> watchSet = new TreeSet<String>();
		watchSet.add(username);
		if (!ArrayUtils.isEmpty(watchers)) {
			for (String cc : watchers) {
				watchSet.add(cc.toLowerCase());
			}
		}

		if (ticket.isClosed()) {
			// re-opening a closed ticket
			change.setField(Field.status, Status.Open);
		}

		// ticket may or may not already have an integration branch
		if (StringUtils.isEmpty(ticket.mergeTo) || !ticket.mergeTo.equals(mergeTo)) {
			change.setField(Field.mergeTo, mergeTo);
		}

		if (!StringUtils.isEmpty(milestone) && !milestone.equals(ticket.milestone)) {
			// user specified a (different) milestone
			change.setField(Field.milestone, milestone);
		}

		if (!StringUtils.isEmpty(assignedTo) && !assignedTo.equals(ticket.assignedTo)) {
			// user specified a (different) assigned to
			change.setField(Field.assignedTo, assignedTo);
			watchSet.add(assignedTo);
		}

		if (!StringUtils.isEmpty(topic) && !topic.equals(ticket.topic)) {
			// user specified a (different) topic
			change.setField(Field.topic, topic);
		}

		if (TicketModel.Type.Proposal == ticket.type
				&& PatchsetType.Amend == patchset.type
				&& patchset.totalCommits == 1) {

			// Gerrit-style title and description updates from the commit message
			 String title = getTitle();
             String body = getBody();

             if (!ticket.title.equals(title)) {
                 // title changed
                 change.setField(Field.title, title);
             }

             if (!ticket.body.equals(body)) {
                 // description changed
                 change.setField(Field.body, body);
             }
		}

		// update the watchers
		watchSet.removeAll(ticket.getWatchers());
		if (!watchSet.isEmpty()) {
			change.watch(watchSet.toArray(new String[watchSet.size()]));
		}
		return change;
	}
}