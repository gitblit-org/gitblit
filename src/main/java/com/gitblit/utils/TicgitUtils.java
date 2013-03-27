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

/**
 * Utility class for reading Ticgit issues.
 * 
 * @author James Moger
 * 
 */
public class TicgitUtils {

	static final Logger LOGGER = LoggerFactory.getLogger(TicgitUtils.class);

	/**
	 * Returns a RefModel for the Ticgit branch in the repository. If the branch
	 * can not be found, null is returned.
	 * 
	 * @param repository
	 * @return a refmodel for the ticgit branch or null
	 */
	public static RefModel getTicketsBranch(Repository repository) {
		return JGitUtils.getBranch(repository, "ticgit");
	}

	/**
	 * Returns a list of all tickets in the ticgit branch of the repository.
	 * 
	 * @param repository
	 * @return list of tickets
	 */
	public static List<TicketModel> getTickets(Repository repository) {
		RefModel ticgitBranch = getTicketsBranch(repository);
		if (ticgitBranch == null) {
			return null;
		}
		RevCommit commit = (RevCommit) ticgitBranch.referencedObject;
		List<PathModel> paths = JGitUtils.getFilesInPath(repository, null, commit);
		List<TicketModel> tickets = new ArrayList<TicketModel>();
		for (PathModel ticketFolder : paths) {
			if (ticketFolder.isTree()) {
				try {
					TicketModel t = new TicketModel(ticketFolder.name);
					loadTicketContents(repository, ticgitBranch, t);
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

	/**
	 * Returns a TicketModel for the specified ticgit ticket. Returns null if
	 * the ticket does not exist or some other error occurs.
	 * 
	 * @param repository
	 * @param ticketFolder
	 * @return a ticket
	 */
	public static TicketModel getTicket(Repository repository, String ticketFolder) {
		RefModel ticketsBranch = getTicketsBranch(repository);
		if (ticketsBranch != null) {
			try {
				TicketModel ticket = new TicketModel(ticketFolder);
				loadTicketContents(repository, ticketsBranch, ticket);
				return ticket;
			} catch (Throwable t) {
				LOGGER.error("Failed to get ticket " + ticketFolder, t);
			}
		}
		return null;
	}

	/**
	 * Loads the contents of the ticket.
	 * 
	 * @param repository
	 * @param ticketsBranch
	 * @param ticket
	 */
	private static void loadTicketContents(Repository repository, RefModel ticketsBranch,
			TicketModel ticket) {
		RevCommit commit = (RevCommit) ticketsBranch.referencedObject;
		List<PathModel> ticketFiles = JGitUtils.getFilesInPath(repository, ticket.name, commit);
		for (PathModel file : ticketFiles) {
			String content = JGitUtils.getStringContent(repository, commit.getTree(), file.path)
					.trim();
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
