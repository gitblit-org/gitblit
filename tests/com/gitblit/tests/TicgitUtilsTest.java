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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

import com.gitblit.models.RefModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Comment;
import com.gitblit.utils.TicgitUtils;

public class TicgitUtilsTest {

	@Test
	public void testTicgitBranch() throws Exception {
		Repository repository = GitBlitSuite.getTicgitRepository();
		RefModel branch = TicgitUtils.getTicketsBranch(repository);
		repository.close();
		assertNotNull("Ticgit branch does not exist!", branch);

		repository = GitBlitSuite.getHelloworldRepository();
		branch = TicgitUtils.getTicketsBranch(repository);
		repository.close();
		assertNull("Ticgit branch exists!", branch);
	}

	@Test
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
				assertEquals(commentA.hashCode(), commentA.text.hashCode());
			}
		}

		repository = GitBlitSuite.getHelloworldRepository();
		List<TicketModel> ticketsC = TicgitUtils.getTickets(repository);
		repository.close();
		assertNull(ticketsC);
	}

	@Test
	public void testReadTicket() throws Exception {
		Repository repository = GitBlitSuite.getTicgitRepository();
		List<TicketModel> tickets = TicgitUtils.getTickets(repository);
		TicketModel ticket = TicgitUtils
				.getTicket(repository, tickets.get(tickets.size() - 1).name);
		repository.close();
		assertNotNull(ticket);
		assertEquals("1206206148_add-attachment-to-ticket_138", ticket.name);
	}
}