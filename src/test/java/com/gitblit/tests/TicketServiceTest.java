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
package com.gitblit.tests;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.bouncycastle.util.Arrays;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.manager.NotificationManager;
import com.gitblit.manager.RepositoryManager;
import com.gitblit.manager.RuntimeManager;
import com.gitblit.manager.UserManager;
import com.gitblit.models.Mailing;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Attachment;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.TicketModel.Field;
import com.gitblit.models.TicketModel.Patchset;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.models.TicketModel.Type;
import com.gitblit.tests.mock.MemorySettings;
import com.gitblit.tickets.ITicketService;
import com.gitblit.tickets.QueryResult;
import com.gitblit.tickets.RedisTicketService;
import com.gitblit.tickets.RepositoryTicketService;
import com.gitblit.tickets.TicketIndexer.Lucene;
import com.gitblit.tickets.TicketNotifier;
import com.gitblit.utils.JGitUtils;

/**
 * Tests the mechanics of Gitblit ticket management.
 *
 * @author James Moger
 *
 */
public class TicketServiceTest extends GitblitUnitTest {

	final String name = "gb-tickets.git";

	@BeforeClass
	public static void prepare() {
	}

	private ITicketService getService(boolean deleteAll) throws Exception {
		return getRepositoryService(deleteAll);
	}

	private ITicketService getRedisService(boolean deleteAll) throws Exception {
		File dir = new File(GitBlitSuite.REPOSITORIES, name);
		if (deleteAll) {
			FileUtils.deleteDirectory(dir);
			JGitUtils.createRepository(GitBlitSuite.REPOSITORIES, name).close();
		}

		File luceneDir = new File(dir, "tickets");

		Map<String, Object> map = new HashMap<String, Object>();
		map.put(Keys.git.repositoriesFolder, GitBlitSuite.REPOSITORIES.getAbsolutePath());
		map.put(Keys.tickets.redisUrl, "redis://localhost:6379/1");
		map.put(Keys.tickets.indexFolder, luceneDir.getAbsolutePath());

		IStoredSettings settings = new MemorySettings(map);
		IRuntimeManager runtimeManager = new RuntimeManager(settings).start();
		INotificationManager notificationManager = new NotificationManager(settings).start();
		IUserManager userManager = new UserManager(runtimeManager).start();
		IRepositoryManager repositoryManager = new RepositoryManager(runtimeManager, userManager).start();

		RedisTicketService service = new RedisTicketService(
				runtimeManager,
				notificationManager,
				userManager,
				repositoryManager).start();

		if (deleteAll) {
			service.deleteAll();
		}
		return service;
	}

	private ITicketService getRepositoryService(boolean deleteAll) throws Exception {
		File dir = new File(GitBlitSuite.REPOSITORIES, name);
		if (deleteAll) {
			FileUtils.deleteDirectory(dir);
			JGitUtils.createRepository(GitBlitSuite.REPOSITORIES, name).close();
		}

		File luceneDir = new File(dir, "tickets");

		Map<String, Object> map = new HashMap<String, Object>();
		map.put(Keys.git.repositoriesFolder, GitBlitSuite.REPOSITORIES.getAbsolutePath());
		map.put(Keys.tickets.redisUrl, "redis://localhost:6379/1");
		map.put(Keys.tickets.indexFolder, luceneDir.getAbsolutePath());

		IStoredSettings settings = new MemorySettings(map);
		IRuntimeManager runtimeManager = new RuntimeManager(settings).start();
		INotificationManager notificationManager = new NotificationManager(settings).start();
		IUserManager userManager = new UserManager(runtimeManager).start();
		IRepositoryManager repositoryManager = new RepositoryManager(runtimeManager, userManager).start();

		RepositoryTicketService service = new RepositoryTicketService(
				runtimeManager,
				notificationManager,
				userManager,
				repositoryManager).start();

		if (deleteAll) {
			service.deleteAll();
		}
		return service;
	}

	@Test
	public void testLifecycle() throws Exception {
		ITicketService service = getService(true);

		// create and insert a ticket
		Change c1 = newChange("testCreation() " + Long.toHexString(System.currentTimeMillis()));
		TicketModel ticket = service.createTicket(name, c1);
		assertNotNull(ticket.changeId);

		// retrieve ticket and compare
		TicketModel constructed = service.getTicket(name, ticket.changeId);
		compare(ticket, constructed);

		assertEquals(1, constructed.changes.size());

		// C1: create the ticket
		int changeCount = 0;
		c1 = newChange("testUpdates() " + Long.toHexString(System.currentTimeMillis()));
		ticket = service.createTicket(name, c1);
		assertNotNull(ticket.changeId);
		changeCount++;

		constructed = service.getTicket(name, ticket.changeId);
		compare(ticket, constructed);
		assertEquals(1, constructed.changes.size());

		// C2: set owner
		Change c2 = new Change("C2");
		c2.comment("I'll fix this");
		c2.setField(Field.assignedTo, c2.createdBy);
		constructed = service.updateTicket(name, ticket.changeId, c2);
		assertNotNull(constructed);
		assertEquals(2, constructed.changes.size());
		assertEquals(c2.createdBy, constructed.assignedTo);
		changeCount++;

		// C3: add a note
		Change c3 = new Change("C3");
		c3.comment("yeah, this is working");
		constructed = service.updateTicket(name, ticket.changeId, c3);
		assertNotNull(constructed);
		assertEquals(3, constructed.changes.size());
		changeCount++;

		if (service.supportsAttachments()) {
			// C4: add attachment
			Change c4 = new Change("C4");
			Attachment a = newAttachment();
			c4.addAttachment(a);
			constructed = service.updateTicket(name, ticket.changeId, c4);
			assertNotNull(constructed);
			assertTrue(constructed.hasAttachments());
			Attachment a1 = service.getAttachment(name, ticket.changeId, a.name);
			assertEquals(a.content.length, a1.content.length);
			assertTrue(Arrays.areEqual(a.content, a1.content));
			changeCount++;
		}

		// C5: close the issue
		Change c5 = new Change("C5");
		c5.comment("closing issue");
		c5.setField(Field.status, Status.Resolved);
		constructed = service.updateTicket(name, ticket.changeId, c5);
		assertNotNull(constructed);
		changeCount++;
		assertTrue(constructed.isClosed());
		assertEquals(changeCount, constructed.changes.size());

		List<TicketModel> allTickets = service.getTickets(name);
		List<TicketModel> openTickets = service.getOpenTickets(name);
		List<TicketModel> closedTickets = service.getClosedTickets(name);
		assertTrue(allTickets.size() > 0);
		assertEquals(1, openTickets.size());
		assertEquals(1, closedTickets.size());

		// build a new Lucene index
		service.reindex(name);
		List<QueryResult> hits = service.searchFor(name, "working", 1, 10);
		assertEquals(1, hits.size());

		// reindex a ticket
		ticket = allTickets.get(0);
		Change change = new Change("reindex");
		change.comment("this is a test of reindexing a ticket");
		service.updateTicket(name, ticket.changeId, change);
		ticket = service.getTicket(name, ticket.changeId);

		hits = service.searchFor(name, "reindexing", 1, 10);
		assertEquals(1, hits.size());

		service.stop();
		service = getService(false);

		// Lucene field query
		List<QueryResult> results = service.queryFor(Lucene.status.matches("open"), 1, 10, Lucene.created.name(), true);
		assertEquals(1, results.size());
		assertTrue(results.get(0).title.startsWith("testCreation"));

		// Lucene field query
		results = service.queryFor(Lucene.status.matches("closed"), 1, 10, Lucene.created.name(), true);
		assertEquals(1, results.size());
		assertTrue(results.get(0).title.startsWith("testUpdates"));

		// delete all tickets
		for (TicketModel aTicket : allTickets) {
			assertTrue(service.deleteTicket(name, aTicket.changeId, "D"));
		}

		service.stop();
	}

	@Test
	public void testChangeComment() throws Exception {
		ITicketService service = getService(true);

		// C1: create the ticket
		Change c1 = newChange("testChangeComment() " + Long.toHexString(System.currentTimeMillis()));
		TicketModel ticket = service.createTicket(name, c1);
		assertNotNull(ticket.changeId);
		assertTrue(ticket.changes.get(0).hasComment());

		ticket = service.updateComment(ticket, c1.comment.id, "E1", "I changed the comment");
		assertNotNull(ticket);
		assertTrue(ticket.changes.get(0).hasComment());
		assertEquals("I changed the comment", ticket.changes.get(0).comment.text);

		assertTrue(service.deleteTicket(name, ticket.changeId, "D"));

		service.stop();
	}

	@Test
	public void testDeleteComment() throws Exception {
		ITicketService service = getService(true);

		// C1: create the ticket
		Change c1 = newChange("testDeleteComment() " + Long.toHexString(System.currentTimeMillis()));
		TicketModel ticket = service.createTicket(name, c1);
		assertNotNull(ticket.changeId);
		assertTrue(ticket.changes.get(0).hasComment());

		ticket = service.deleteComment(ticket, c1.comment.id, "D1");
		assertNotNull(ticket);
		assertEquals(1, ticket.changes.size());
		assertFalse(ticket.changes.get(0).hasComment());

		assertTrue(service.deleteTicket(name, ticket.changeId, "D"));

		service.stop();
	}

	private Change newChange(String summary) {
		Change change = new Change("C1");
		change.setField(Field.repository, name);
		change.setField(Field.title, summary);
		change.setField(Field.body, "this is my description");
		change.setField(Field.labels, "helpdesk");
		change.comment("my comment");
		return change;
	}

	private Attachment newAttachment() {
		Attachment attachment = new Attachment("test1.txt");
		attachment.content = new byte[] { 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
				0x4a };
		return attachment;
	}

	private void compare(TicketModel ticket, TicketModel constructed) {
		assertEquals(ticket.changeId, constructed.changeId);
		assertEquals(ticket.createdBy, constructed.createdBy);
		assertEquals(ticket.assignedTo, constructed.assignedTo);
		assertEquals(ticket.title, constructed.title);
		assertEquals(ticket.body, constructed.body);
		assertEquals(ticket.createdAt, constructed.createdAt);

		assertTrue(ticket.hasLabel("helpdesk"));
	}

	@Test
	public void testNotifier() throws Exception {
		Change kernel = new Change("james");
		kernel.setField(Field.repository, name);
		kernel.setField(Field.title, "Sample ticket");
		kernel.setField(Field.body, "this **is** my sample body\n\n- I hope\n- you really\n- *really* like it");
		kernel.setField(Field.status, Status.New);
		kernel.setField(Field.type, Type.Proposal);

		kernel.comment("this is a sample comment on a kernel change");

		Patchset patchset = new Patchset();
		patchset.insertions = 100;
		patchset.deletions = 10;
		patchset.rev = 25;
		patchset.ref = "refs/changes/01/1/25";
		patchset.tip = "50f57913f816d04a16b7407134de5d8406421f37";
		kernel.patch = patchset;

		ITicketService service = getService(true);

		TicketModel ticket = service.createTicket(name, kernel);

		Change merge = new Change("james");
		merge.setField(Field.mergeSha, patchset.tip);
		merge.setField(Field.mergeTo, "master");
		merge.setField(Field.status, Status.Merged);

		ticket = service.updateTicket(name, ticket.changeId, merge);

		ticket.repository = "test.git";

		TicketNotifier notifier = service.createNotifier();
		Mailing mailing = notifier.queueMailing(ticket);
		assertNotNull(mailing);
	}
}