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
import java.util.ArrayList;
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
 * A subclass of ReceiveCommand which constructs a ticket change based on a
 * patchset and data derived from the push ref.
 *
 * @author James Moger
 *
 */
public class PatchsetCommand extends ReceiveCommand {

	public static final String TOPIC = "topic=";

	public static final String RESPONSIBLE = "r=";

	public static final String WATCH = "cc=";

	public static final String MILESTONE = "m=";

	protected final Change change;

	protected boolean isNew;

	protected long ticketId;

	public static String getBaseChangeRef(long ticketNumber) {
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

	public static long getTicketNumber(String ref) {
		if (ref.startsWith(Constants.R_TICKETS)) {
			// current ticket head
			String p = ref.substring(Constants.R_TICKETS.length());
			return Long.parseLong(p);
		}

		if (ref.startsWith(Constants.R_CHANGES)) {
			// patchset revision

			// strip changes ref
			String p = ref.substring(Constants.R_CHANGES.length());
			// strip shard id
			p = p.substring(p.indexOf('/') + 1);
			// strip revision
			p = p.substring(0, p.indexOf('/'));
			// parse ticket number
			return Long.parseLong(p);
		}
		return 0L;
	}

	public PatchsetCommand(String username, Patchset patchset) {
		super(patchset.isFF() ? ObjectId.fromString(patchset.parent) : ObjectId.zeroId(),
				ObjectId.fromString(patchset.tip), null);
		this.change = new Change(username);
		this.change.patchset = patchset;
	}

	public PatchsetType getPatchsetType() {
		return change.patchset.type;
	}

	public boolean isNewTicket() {
		return isNew;
	}

	public long getTicketId() {
		return ticketId;
	}

	public Change getChange() {
		return change;
	}

	/**
	 * Creates a "new ticket" change for the proposal.
	 *
	 * @param commit
	 * @param mergeTo
	 * @param ticketId
	 * @parem pushRef
	 */
	public void newTicket(RevCommit commit, String mergeTo, long ticketId, String pushRef) {
		this.ticketId = ticketId;
		isNew = true;
		change.setField(Field.title, getTitle(commit));
		change.setField(Field.body, getBody(commit));
		change.setField(Field.status, Status.New);
		change.setField(Field.mergeTo, mergeTo);
		change.setField(Field.type, TicketModel.Type.Proposal);

		Set<String> watchSet = new TreeSet<String>();
		watchSet.add(change.author);

		// identify parameters passed in the push ref
		if (!StringUtils.isEmpty(pushRef)) {
			List<String> watchers = getOptions(pushRef, WATCH);
			if (!ArrayUtils.isEmpty(watchers)) {
				for (String cc : watchers) {
					watchSet.add(cc.toLowerCase());
				}
			}

			String milestone = getSingleOption(pushRef, MILESTONE);
			if (!StringUtils.isEmpty(milestone)) {
				// user provided milestone
				change.setField(Field.milestone, milestone);
			}

			String responsible = getSingleOption(pushRef, RESPONSIBLE);
			if (!StringUtils.isEmpty(responsible)) {
				// user provided responsible
				change.setField(Field.responsible, responsible);
				watchSet.add(responsible);
			}

			String topic = getSingleOption(pushRef, TOPIC);
			if (!StringUtils.isEmpty(topic)) {
				// user provided topic
				change.setField(Field.topic, topic);
			}
		}

		// set the watchers
		change.watch(watchSet.toArray(new String[watchSet.size()]));
	}

	/**
	 *
	 * @param commit
	 * @param mergeTo
	 * @param ticket
	 * @param pushRef
	 */
	public void updateTicket(RevCommit commit, String mergeTo, TicketModel ticket, String pushRef) {

		this.ticketId = ticket.number;

		if (ticket.isClosed()) {
			// re-opening a closed ticket
			change.setField(Field.status, Status.Open);
		}

		// ticket may or may not already have an integration branch
		if (StringUtils.isEmpty(ticket.mergeTo) || !ticket.mergeTo.equals(mergeTo)) {
			change.setField(Field.mergeTo, mergeTo);
		}

		if (ticket.isProposal() && PatchsetType.Amend == change.patchset.type
				&& change.patchset.commits == 1) {

			// Gerrit-style title and description updates from the commit
			// message
			String title = getTitle(commit);
			String body = getBody(commit);

			if (!ticket.title.equals(title)) {
				// title changed
				change.setField(Field.title, title);
			}

			if (!ticket.body.equals(body)) {
				// description changed
				change.setField(Field.body, body);
			}
		}

		Set<String> watchSet = new TreeSet<String>();
		watchSet.add(change.author);

		// update the patchset command metadata
		if (!StringUtils.isEmpty(pushRef)) {
			List<String> watchers = getOptions(pushRef, WATCH);
			if (!ArrayUtils.isEmpty(watchers)) {
				for (String cc : watchers) {
					watchSet.add(cc.toLowerCase());
				}
			}

			String milestone = getSingleOption(pushRef, MILESTONE);
			if (!StringUtils.isEmpty(milestone) && !milestone.equals(ticket.milestone)) {
				// user specified a (different) milestone
				change.setField(Field.milestone, milestone);
			}

			String responsible = getSingleOption(pushRef, RESPONSIBLE);
			if (!StringUtils.isEmpty(responsible) && !responsible.equals(ticket.responsible)) {
				// user specified a (different) responsible
				change.setField(Field.responsible, responsible);
				watchSet.add(responsible);
			}

			String topic = getSingleOption(pushRef, TOPIC);
			if (!StringUtils.isEmpty(topic) && !topic.equals(ticket.topic)) {
				// user specified a (different) topic
				change.setField(Field.topic, topic);
			}
		}

		// update the watchers
		watchSet.removeAll(ticket.getWatchers());
		if (!watchSet.isEmpty()) {
			change.watch(watchSet.toArray(new String[watchSet.size()]));
		}
	}

	@Override
	public String getRefName() {
		return getBaseChangeRef(ticketId) + change.patchset.number;
	}

	private String getTitle(RevCommit commit) {
		String title = commit.getShortMessage();
		return title;
	}

	/**
	 * Returns the body of the commit message
	 *
	 * @return
	 */
	private String getBody(RevCommit commit) {
		final byte[] raw = commit.getRawBuffer();
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

	/** Extracts a ticket field from the ref name */
	private static List<String> getOptions(String refName, String token) {
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
	private static String getSingleOption(String refName, String token) {
		List<String> list = getOptions(refName, token);
		if (list != null && list.size() > 0) {
			return list.get(0);
		}
		return null;
	}

	/** Extracts a ticket field from the ref name */
	public static String getSingleOption(ReceiveCommand cmd, String token) {
		return getSingleOption(cmd.getRefName(), token);
	}

	/** Extracts a ticket field from the ref name */
	public static List<String> getOptions(ReceiveCommand cmd, String token) {
		return getOptions(cmd.getRefName(), token);
	}

}