/*
 * Copyright 2011 gitblit.com.
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
package com.gitblit.utils;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.PathModel;
import com.gitblit.models.RefModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Comment;

public class TicgitUtils {

	static final Logger LOGGER = LoggerFactory.getLogger(TicgitUtils.class);

	public static RefModel getTicketsBranch(Repository r) {
		RefModel ticgitBranch = null;
		try {
			// search for ticgit branch in local heads
			for (RefModel ref : JGitUtils.getLocalBranches(r, false, -1)) {
				if (ref.displayName.endsWith("ticgit")) {
					ticgitBranch = ref;
					break;
				}
			}

			// search for ticgit branch in remote heads
			if (ticgitBranch == null) {
				for (RefModel ref : JGitUtils.getRemoteBranches(r, false, -1)) {
					if (ref.displayName.endsWith("ticgit")) {
						ticgitBranch = ref;
						break;
					}
				}
			}
		} catch (Throwable t) {
			LOGGER.error("Failed to find ticgit branch!", t);
		}
		return ticgitBranch;
	}

	public static List<TicketModel> getTickets(Repository r) {
		RefModel ticgitBranch = getTicketsBranch(r);
		if (ticgitBranch == null) {
			return null;
		}
		RevCommit commit = (RevCommit) ticgitBranch.referencedObject;
		List<PathModel> paths = JGitUtils.getFilesInPath(r, null, commit);
		List<TicketModel> tickets = new ArrayList<TicketModel>();
		for (PathModel ticketFolder : paths) {
			if (ticketFolder.isTree()) {
				try {
					TicketModel t = new TicketModel(ticketFolder.name);
					readTicketContents(r, ticgitBranch, t);
					tickets.add(t);
				} catch (Throwable t) {
					LOGGER.error("Failed to get a ticket!", t);
				}
			}
		}
		Collections.sort(tickets);
		Collections.reverse(tickets);
		return tickets;
	}

	public static TicketModel getTicket(Repository r, String ticketFolder) {
		RefModel ticketsBranch = getTicketsBranch(r);
		if (ticketsBranch != null) {
			try {
				TicketModel ticket = new TicketModel(ticketFolder);
				readTicketContents(r, ticketsBranch, ticket);
				return ticket;
			} catch (Throwable t) {
				LOGGER.error("Failed to get ticket " + ticketFolder, t);
			}
		}
		return null;
	}

	private static void readTicketContents(Repository r, RefModel ticketsBranch, TicketModel ticket) {
		RevCommit commit = (RevCommit) ticketsBranch.referencedObject;
		List<PathModel> ticketFiles = JGitUtils.getFilesInPath(r, ticket.name, commit);
		for (PathModel file : ticketFiles) {
			String content = JGitUtils.getStringContent(r, commit.getTree(), file.path).trim();
			if (file.name.equals("TICKET_ID")) {
				ticket.id = content;
			} else if (file.name.equals("TITLE")) {
				ticket.title = content;
			} else {
				String[] chunks = file.name.split("_");
				if (chunks[0].equals("ASSIGNED")) {
					ticket.handler = content;
				} else if (chunks[0].equals("COMMENT")) {
					try {
						Comment c = new Comment(file.name, content);
						ticket.comments.add(c);
					} catch (ParseException e) {
						LOGGER.error("Failed to parse ticket comment", e);
					}
				} else if (chunks[0].equals("TAG")) {
					if (content.startsWith("TAG_")) {
						ticket.tags.add(content.substring(4));
					} else {
						ticket.tags.add(content);
					}
				} else if (chunks[0].equals("STATE")) {
					ticket.state = content;
				}
			}
		}
		Collections.sort(ticket.comments);
	}
}
