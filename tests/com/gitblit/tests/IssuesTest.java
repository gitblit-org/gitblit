/*
 * Copyright 2012 gitblit.com.
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
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.bouncycastle.util.Arrays;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

import com.gitblit.LuceneExecutor;
import com.gitblit.models.IssueModel;
import com.gitblit.models.IssueModel.Attachment;
import com.gitblit.models.IssueModel.Change;
import com.gitblit.models.IssueModel.Field;
import com.gitblit.models.IssueModel.Priority;
import com.gitblit.models.IssueModel.Status;
import com.gitblit.models.SearchResult;
import com.gitblit.utils.FileUtils;
import com.gitblit.utils.IssueUtils;
import com.gitblit.utils.IssueUtils.IssueFilter;

/**
 * Tests the mechanics of distributed issue management on the gb-issues branch.
 * 
 * @author James Moger
 * 
 */
public class IssuesTest {

	@Test
	public void testLifecycle() throws Exception {
		Repository repository = GitBlitSuite.getIssuesTestRepository();
		String name = FileUtils.getRelativePath(GitBlitSuite.REPOSITORIES, repository.getDirectory());
		
		// create and insert an issue
		Change c1 = newChange("testCreation() " + Long.toHexString(System.currentTimeMillis()));
		IssueModel issue = IssueUtils.createIssue(repository, c1);
		assertNotNull(issue.id);

		// retrieve issue and compare
		IssueModel constructed = IssueUtils.getIssue(repository, issue.id);
		compare(issue, constructed);

		assertEquals(1, constructed.changes.size());
		
		// C1: create the issue
		c1 = newChange("testUpdates() " + Long.toHexString(System.currentTimeMillis()));
		issue = IssueUtils.createIssue(repository, c1);
		assertNotNull(issue.id);

		constructed = IssueUtils.getIssue(repository, issue.id);
		compare(issue, constructed);
		assertEquals(1, constructed.changes.size());

		// C2: set owner
		Change c2 = new Change("C2");
		c2.comment("I'll fix this");
		c2.setField(Field.Owner, c2.author);
		assertTrue(IssueUtils.updateIssue(repository, issue.id, c2));
		constructed = IssueUtils.getIssue(repository, issue.id);
		assertEquals(2, constructed.changes.size());
		assertEquals(c2.author, constructed.owner);

		// C3: add a note
		Change c3 = new Change("C3");
		c3.comment("yeah, this is working");
		assertTrue(IssueUtils.updateIssue(repository, issue.id, c3));
		constructed = IssueUtils.getIssue(repository, issue.id);
		assertEquals(3, constructed.changes.size());

		// C4: add attachment
		Change c4 = new Change("C4");
		Attachment a = newAttachment();
		c4.addAttachment(a);
		assertTrue(IssueUtils.updateIssue(repository, issue.id, c4));

		Attachment a1 = IssueUtils.getIssueAttachment(repository, issue.id, a.name);
		assertEquals(a.content.length, a1.content.length);
		assertTrue(Arrays.areEqual(a.content, a1.content));

		// C5: close the issue
		Change c5 = new Change("C5");
		c5.comment("closing issue");
		c5.setField(Field.Status, Status.Fixed);
		assertTrue(IssueUtils.updateIssue(repository, issue.id, c5));

		// retrieve issue again
		constructed = IssueUtils.getIssue(repository, issue.id);

		assertEquals(5, constructed.changes.size());
		assertTrue(constructed.status.isClosed());

		List<IssueModel> allIssues = IssueUtils.getIssues(repository, null);
		List<IssueModel> openIssues = IssueUtils.getIssues(repository, new IssueFilter() {
			@Override
			public boolean accept(IssueModel issue) {
				return !issue.status.isClosed();
			}
		});
		List<IssueModel> closedIssues = IssueUtils.getIssues(repository, new IssueFilter() {
			@Override
			public boolean accept(IssueModel issue) {
				return issue.status.isClosed();
			}
		});
		
		assertTrue(allIssues.size() > 0);
		assertEquals(1, openIssues.size());
		assertEquals(1, closedIssues.size());
		
		// build a new Lucene index
		LuceneExecutor lucene = new LuceneExecutor(null, GitBlitSuite.REPOSITORIES);
		lucene.deleteIndex(name);
		for (IssueModel anIssue : allIssues) {
			lucene.index(name, anIssue);
		}
		List<SearchResult> hits = lucene.search("working", 1, 10, name);
		assertTrue(hits.size() == 1);
		
		// reindex an issue
		issue = allIssues.get(0);
		Change change = new Change("reindex");
		change.comment("this is a test of reindexing an issue");
		IssueUtils.updateIssue(repository, issue.id, change);
		issue = IssueUtils.getIssue(repository, issue.id);
		lucene.index(name, issue);

		hits = lucene.search("working", 1, 10, name);
		assertTrue(hits.size() == 1);

		
		// delete all issues
		for (IssueModel anIssue : allIssues) {
			assertTrue(IssueUtils.deleteIssue(repository, anIssue.id, "D"));
		}
				
		lucene.close();
		repository.close();
	}
	
	@Test
	public void testChangeComment() throws Exception {
		Repository repository = GitBlitSuite.getIssuesTestRepository();
		// C1: create the issue
		Change c1 = newChange("testChangeComment() " + Long.toHexString(System.currentTimeMillis()));
		IssueModel issue = IssueUtils.createIssue(repository, c1);
		assertNotNull(issue.id);
		assertTrue(issue.changes.get(0).hasComment());

		assertTrue(IssueUtils.changeComment(repository, issue, c1, "E1", "I changed the comment"));
		issue = IssueUtils.getIssue(repository, issue.id);
		assertTrue(issue.changes.get(0).hasComment());
		assertEquals("I changed the comment", issue.changes.get(0).comment.text);

		assertTrue(IssueUtils.deleteIssue(repository, issue.id, "D"));

		repository.close();
	}

	@Test
	public void testDeleteComment() throws Exception {
		Repository repository = GitBlitSuite.getIssuesTestRepository();
		// C1: create the issue
		Change c1 = newChange("testDeleteComment() " + Long.toHexString(System.currentTimeMillis()));
		IssueModel issue = IssueUtils.createIssue(repository, c1);
		assertNotNull(issue.id);
		assertTrue(issue.changes.get(0).hasComment());

		assertTrue(IssueUtils.deleteComment(repository, issue, c1, "D1"));
		issue = IssueUtils.getIssue(repository, issue.id);
		assertEquals(1, issue.changes.size());
		assertFalse(issue.changes.get(0).hasComment());

		issue = IssueUtils.getIssue(repository, issue.id, false);
		assertEquals(2, issue.changes.size());
		assertTrue(issue.changes.get(0).hasComment());
		assertFalse(issue.changes.get(1).hasComment());

		assertTrue(IssueUtils.deleteIssue(repository, issue.id, "D"));

		repository.close();
	}

	private Change newChange(String summary) {
		Change change = new Change("C1");
		change.setField(Field.Summary, summary);
		change.setField(Field.Description, "this is my description");
		change.setField(Field.Priority, Priority.High);
		change.setField(Field.Labels, "helpdesk");
		change.comment("my comment");
		return change;
	}

	private Attachment newAttachment() {
		Attachment attachment = new Attachment(Long.toHexString(System.currentTimeMillis())
				+ ".txt");
		attachment.content = new byte[] { 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
				0x4a };
		return attachment;
	}

	private void compare(IssueModel issue, IssueModel constructed) {
		assertEquals(issue.id, constructed.id);
		assertEquals(issue.reporter, constructed.reporter);
		assertEquals(issue.owner, constructed.owner);
		assertEquals(issue.summary, constructed.summary);
		assertEquals(issue.description, constructed.description);
		assertEquals(issue.created, constructed.created);

		assertTrue(issue.hasLabel("helpdesk"));
	}
}