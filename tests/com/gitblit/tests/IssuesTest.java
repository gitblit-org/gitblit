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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.bouncycastle.util.Arrays;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

import com.gitblit.models.IssueModel;
import com.gitblit.models.IssueModel.Attachment;
import com.gitblit.models.IssueModel.Change;
import com.gitblit.models.IssueModel.Field;
import com.gitblit.models.IssueModel.Priority;
import com.gitblit.utils.IssueUtils;
import com.gitblit.utils.IssueUtils.IssueFilter;

public class IssuesTest {

	@Test
	public void testInsertion() throws Exception {
		Repository repository = GitBlitSuite.getIssuesTestRepository();
		// create and insert the issue
		Change c1 = newChange("Test issue " + Long.toHexString(System.currentTimeMillis()));
		IssueModel issue = IssueUtils.createIssue(repository, c1);
		assertNotNull(issue.id);

		// retrieve issue and compare
		IssueModel constructed = IssueUtils.getIssue(repository, issue.id);
		compare(issue, constructed);

		// add a note and update
		Change c2 = new Change();
		c2.author = "dave";
		c2.comment("yeah, this is working");		
		assertTrue(IssueUtils.updateIssue(repository, issue.id, c2));

		// retrieve issue again
		constructed = IssueUtils.getIssue(repository, issue.id);

		assertEquals(2, constructed.changes.size());

		Attachment a = IssueUtils.getIssueAttachment(repository, issue.id, "test.txt");
		repository.close();

		assertEquals(10, a.content.length);
		assertTrue(Arrays.areEqual(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 }, a.content));
	}

	@Test
	public void testQuery() throws Exception {
		Repository repository = GitBlitSuite.getIssuesTestRepository();
		List<IssueModel> list = IssueUtils.getIssues(repository, null);
		List<IssueModel> list2 = IssueUtils.getIssues(repository, new IssueFilter() {
			boolean hasFirst = false;
			@Override
			public boolean accept(IssueModel issue) {
				if (!hasFirst) {
					hasFirst = true;
					return true;
				}
				return false;
			}
		});
		repository.close();
		assertTrue(list.size() > 0);
		assertEquals(1, list2.size());
	}

	private Change newChange(String summary) {
		Change change = new Change();
		change.setField(Field.Reporter, "james");
		change.setField(Field.Owner, "dave");
		change.setField(Field.Summary, summary);
		change.setField(Field.Description, "this is my description");
		change.setField(Field.Priority, Priority.High);
		change.setField(Field.Labels, "helpdesk");
		change.comment("my comment");
		
		Attachment attachment = new Attachment("test.txt");		
		attachment.content = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
		change.addAttachment(attachment);		
		
		return change;
	}

	private void compare(IssueModel issue, IssueModel constructed) {
		assertEquals(issue.id, constructed.id);
		assertEquals(issue.reporter, constructed.reporter);
		assertEquals(issue.owner, constructed.owner);
		assertEquals(issue.created.getTime() / 1000, constructed.created.getTime() / 1000);
		assertEquals(issue.summary, constructed.summary);
		assertEquals(issue.description, constructed.description);

		assertTrue(issue.hasLabel("helpdesk"));
	}
}