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
package com.gitblit.tests;

import java.util.List;

import junit.framework.TestCase;

import org.eclipse.jgit.lib.Repository;

import com.gitblit.models.RefModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Comment;
import com.gitblit.utils.TicgitUtils;

public class TicgitUtilsTest extends TestCase {

	public void testTicgitBranch() throws Exception {
		Repository repository = GitBlitSuite.getTicgitRepository();
		RefModel branch = TicgitUtils.getTicketsBranch(repository);
		repository.close();
		assertTrue("Ticgit branch does not exist!", branch != null);
		
		repository = GitBlitSuite.getHelloworldRepository();
		branch = TicgitUtils.getTicketsBranch(repository);
		repository.close();
		assertTrue("Ticgit branch exists!", branch == null);
	}

	public void testRetrieveTickets() throws Exception {
		Repository repository = GitBlitSuite.getTicgitRepository();
		List<TicketModel> ticketsA = TicgitUtils.getTickets(repository);
		List<TicketModel> ticketsB = TicgitUtils.getTickets(repository);
		repository.close();
		assertTrue("No tickets found!", ticketsA.size() > 0);
		for (int i = 0; i < ticketsA.size(); i++) {
			TicketModel ticketA = ticketsA.get(i);
			TicketModel ticketB = ticketsB.get(i);
			assertTrue("Tickets are not equal!", ticketA.equals(ticketB));
			assertFalse(ticketA.equals(""));
			assertTrue(ticketA.hashCode() == ticketA.id.hashCode());
			for (int j = 0; j < ticketA.comments.size(); j++) {
				Comment commentA = ticketA.comments.get(j);
				Comment commentB = ticketB.comments.get(j);
				assertTrue("Comments are not equal!", commentA.equals(commentB));
				assertFalse(commentA.equals(""));
				assertTrue(commentA.hashCode() == commentA.text.hashCode());
			}
		}
		
		repository = GitBlitSuite.getHelloworldRepository();
		List<TicketModel> ticketsC = TicgitUtils.getTickets(repository);
		repository.close();
		assertTrue(ticketsC == null);
	}

	public void testReadTicket() throws Exception {
		Repository repository = GitBlitSuite.getTicgitRepository();
		List<TicketModel> tickets = TicgitUtils.getTickets(repository);
		TicketModel ticket = TicgitUtils
				.getTicket(repository, tickets.get(tickets.size() - 1).name);
		repository.close();
		assertTrue(ticket != null);
		assertTrue(ticket.name
				.equals("1254123752_comments-on-ticgits-longer-than-5-lines-can-t-be-viewed-entirely_266"));
	}
}