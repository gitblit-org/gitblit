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
import java.util.Date;
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
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Attachment;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.TicketModel.Field;
import com.gitblit.models.TicketModel.Patchset;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.models.TicketModel.Type;
import com.gitblit.tests.mock.MemorySettings;
import com.gitblit.tickets.ITicketService;
import com.gitblit.tickets.ITicketService.TicketFilter;
import com.gitblit.tickets.QueryResult;
import com.gitblit.tickets.RedisTicketService;
import com.gitblit.tickets.RepositoryTicketService;
import com.gitblit.tickets.TicketIndexer.Lucene;
import com.gitblit.tickets.TicketLabel;
import com.gitblit.tickets.TicketMilestone;
import com.gitblit.tickets.TicketNotifier;
import com.gitblit.utils.JGitUtils;

/**
 * Tests the mechanics of Gitblit ticket management.
 *
 * @author James Moger
 *
 */
public class TicketServiceTest extends GitblitUnitTest {

	private final RepositoryModel repository = new RepositoryModel("gb-tickets.git", null, null, null);

	@BeforeClass
	public static void prepare() {
	}

	private ITicketService getService(boolean deleteAll) throws Exception {
		return getRepositoryService(deleteAll);
	}

	private ITicketService getRedisService(boolean deleteAll) throws Exception {
		File dir = new File(GitBlitSuite.REPOSITORIES, repository.name);
		if (deleteAll) {
			FileUtils.deleteDirectory(dir);
			JGitUtils.createRepository(GitBlitSuite.REPOSITORIES, repository.name).close();
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
			service.deleteAll(repository);
		}
		return service;
	}

	private ITicketService getRepositoryService(boolean deleteAll) throws Exception {
		File dir = new File(GitBlitSuite.REPOSITORIES, repository.name);
		if (deleteAll) {
			FileUtils.deleteDirectory(dir);
			JGitUtils.createRepository(GitBlitSuite.REPOSITORIES, repository.name).close();
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
			service.deleteAll(repository);
		}
		return service;
	}

	@Test
	public void testLifecycle() throws Exception {
		ITicketService service = getService(true);

		// create and insert a ticket
		Change c1 = newChange("testCreation() " + Long.toHexString(System.currentTimeMillis()));
		TicketModel ticket = service.createTicket(repository, c1);
		assertTrue(ticket.number > 0);

		// retrieve ticket and compare
		TicketModel constructed = service.getTicket(repository, ticket.number);
		compare(ticket, constructed);

		assertEquals(1, constructed.changes.size());

		// C1: create the ticket
		int changeCount = 0;
		c1 = newChange("testUpdates() " + Long.toHexString(System.currentTimeMillis()));
		ticket = service.createTicket(repository, c1);
		assertTrue(ticket.number > 0);
		changeCount++;

		constructed = service.getTicket(repository, ticket.number);
		compare(ticket, constructed);
		assertEquals(1, constructed.changes.size());

		// C2: set owner
		Change c2 = new Change("C2");
		c2.comment("I'll fix this");
		c2.setField(Field.responsible, c2.author);
		constructed = service.updateTicket(repository, ticket.number, c2);
		assertNotNull(constructed);
		assertEquals(2, constructed.changes.size());
		assertEquals(c2.author, constructed.responsible);
		changeCount++;

		// C3: add a note
		Change c3 = new Change("C3");
		c3.comment("yeah, this is working");
		constructed = service.updateTicket(repository, ticket.number, c3);
		assertNotNull(constructed);
		assertEquals(3, constructed.changes.size());
		changeCount++;

		if (service.supportsAttachments()) {
			// C4: add attachment
			Change c4 = new Change("C4");
			Attachment a = newAttachment();
			c4.addAttachment(a);
			constructed = service.updateTicket(repository, ticket.number, c4);
			assertNotNull(constructed);
			assertTrue(constructed.hasAttachments());
			Attachment a1 = service.getAttachment(repository, ticket.number, a.name);
			assertEquals(a.content.length, a1.content.length);
			assertTrue(Arrays.areEqual(a.content, a1.content));
			changeCount++;
		}

		// C5: close the issue
		Change c5 = new Change("C5");
		c5.comment("closing issue");
		c5.setField(Field.status, Status.Resolved);
		constructed = service.updateTicket(repository, ticket.number, c5);
		assertNotNull(constructed);
		changeCount++;
		assertTrue(constructed.isClosed());
		assertEquals(changeCount, constructed.changes.size());

		List<TicketModel> allTickets = service.getTickets(repository);
		List<TicketModel> openTickets = service.getTickets(repository, new TicketFilter() {
			@Override
			public boolean accept(TicketModel ticket) {
				return ticket.isOpen();
			}
		});
		List<TicketModel> closedTickets = service.getTickets(repository, new TicketFilter() {
			@Override
			public boolean accept(TicketModel ticket) {
				return ticket.isClosed();
			}
		});
		assertTrue(allTickets.size() > 0);
		assertEquals(1, openTickets.size());
		assertEquals(1, closedTickets.size());

		// build a new Lucene index
		service.reindex(repository);
		List<QueryResult> hits = service.searchFor(repository, "working", 1, 10);
		assertEquals(1, hits.size());

		// reindex a ticket
		ticket = allTickets.get(0);
		Change change = new Change("reindex");
		change.comment("this is a test of reindexing a ticket");
		service.updateTicket(repository, ticket.number, change);
		ticket = service.getTicket(repository, ticket.number);

		hits = service.searchFor(repository, "reindexing", 1, 10);
		assertEquals(1, hits.size());

		service.stop();
		service = getService(false);

		// Lucene field query
		List<QueryResult> results = service.queryFor(Lucene.status.matches(Status.New.name()), 1, 10, Lucene.created.name(), true);
		assertEquals(1, results.size());
		assertTrue(results.get(0).title.startsWith("testCreation"));

		// Lucene field query
		results = service.queryFor(Lucene.status.matches(Status.Resolved.name()), 1, 10, Lucene.created.name(), true);
		assertEquals(1, results.size());
		assertTrue(results.get(0).title.startsWith("testUpdates"));

		// delete all tickets
		for (TicketModel aTicket : allTickets) {
			assertTrue(service.deleteTicket(repository, aTicket.number, "D"));
		}

		service.stop();
	}

	@Test
	public void testChangeComment() throws Exception {
		ITicketService service = getService(true);

		// C1: create the ticket
		Change c1 = newChange("testChangeComment() " + Long.toHexString(System.currentTimeMillis()));
		TicketModel ticket = service.createTicket(repository, c1);
		assertTrue(ticket.number > 0);
		assertTrue(ticket.changes.get(0).hasComment());

		ticket = service.updateComment(ticket, c1.comment.id, "E1", "I changed the comment");
		assertNotNull(ticket);
		assertTrue(ticket.changes.get(0).hasComment());
		assertEquals("I changed the comment", ticket.changes.get(0).comment.text);

		assertTrue(service.deleteTicket(repository, ticket.number, "D"));

		service.stop();
	}

	@Test
	public void testDeleteComment() throws Exception {
		ITicketService service = getService(true);

		// C1: create the ticket
		Change c1 = newChange("testDeleteComment() " + Long.toHexString(System.currentTimeMillis()));
		TicketModel ticket = service.createTicket(repository, c1);
		assertTrue(ticket.number > 0);
		assertTrue(ticket.changes.get(0).hasComment());

		ticket = service.deleteComment(ticket, c1.comment.id, "D1");
		assertNotNull(ticket);
		assertEquals(1, ticket.changes.size());
		assertFalse(ticket.changes.get(0).hasComment());

		assertTrue(service.deleteTicket(repository, ticket.number, "D"));

		service.stop();
	}

	@Test
	public void testMilestones() throws Exception {
		ITicketService service = getService(true);

		service.createMilestone(repository, "M1", "james");
		service.createMilestone(repository, "M2", "frank");
		service.createMilestone(repository, "M3", "joe");

		List<TicketMilestone> milestones = service.getMilestones(repository, Status.Open);
		assertEquals("Unexpected open milestones count", 3, milestones.size());

		for (TicketMilestone milestone : milestones) {
			milestone.status = Status.Resolved;
			milestone.due = new Date();
			assertTrue("failed to update milestone " + milestone.name, service.updateMilestone(repository, milestone, "ted"));
		}

		milestones = service.getMilestones(repository, Status.Open);
		assertEquals("Unexpected open milestones count", 0, milestones.size());

		milestones = service.getMilestones(repository, Status.Resolved);
		assertEquals("Unexpected resolved milestones count", 3, milestones.size());

		for (TicketMilestone milestone : milestones) {
			assertTrue("failed to delete milestone " + milestone.name, service.deleteMilestone(repository, milestone.name, "lucifer"));
		}

		service.stop();
	}

	@Test
	public void testLabels() throws Exception {
		ITicketService service = getService(true);

		service.createLabel(repository, "L1", "james");
		service.createLabel(repository, "L2", "frank");
		service.createLabel(repository, "L3", "joe");

		List<TicketLabel> labels = service.getLabels(repository);
		assertEquals("Unexpected open labels count", 3, labels.size());

		for (TicketLabel label : labels) {
			label.color = "#ffff00";
			assertTrue("failed to update label " + label.name, service.updateLabel(repository, label, "ted"));
		}

		labels = service.getLabels(repository);
		assertEquals("Unexpected labels count", 3, labels.size());

		for (TicketLabel label : labels) {
			assertTrue("failed to delete label " + label.name, service.deleteLabel(repository, label.name, "lucifer"));
		}

		service.stop();
	}



	private Change newChange(String summary) {
		Change change = new Change("C1");
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
		assertEquals(ticket.number, constructed.number);
		assertEquals(ticket.createdBy, constructed.createdBy);
		assertEquals(ticket.responsible, constructed.responsible);
		assertEquals(ticket.title, constructed.title);
		assertEquals(ticket.body, constructed.body);
		assertEquals(ticket.created, constructed.created);

		assertTrue(ticket.hasLabel("helpdesk"));
	}

	@Test
	public void testNotifier() throws Exception {
		Change kernel = new Change("james");
		kernel.setField(Field.title, "Sample ticket");
		kernel.setField(Field.body, "this **is** my sample body\n\n- I hope\n- you really\n- *really* like it");
		kernel.setField(Field.status, Status.New);
		kernel.setField(Field.type, Type.Proposal);

		kernel.comment("this is a sample comment on a kernel change");

		Patchset patchset = new Patchset();
		patchset.insertions = 100;
		patchset.deletions = 10;
		patchset.rev = 25;
		patchset.tip = "50f57913f816d04a16b7407134de5d8406421f37";
		kernel.patchset = patchset;

		ITicketService service = getService(true);

		TicketModel ticket = service.createTicket(repository, 0L, kernel);

		Change merge = new Change("james");
		merge.setField(Field.mergeSha, patchset.tip);
		merge.setField(Field.mergeTo, "master");
		merge.setField(Field.status, Status.Merged);

		ticket = service.updateTicket(repository, ticket.number, merge);

		ticket.repository = "test.git";

		TicketNotifier notifier = service.createNotifier();
		Mailing mailing = notifier.queueMailing(ticket);
		assertNotNull(mailing);
	}
}