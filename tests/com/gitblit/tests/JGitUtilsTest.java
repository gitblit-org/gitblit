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

import java.io.File;
import java.io.FileOutputStream;
import java.util.Date;
import java.util.List;

import junit.framework.TestCase;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;

import com.gitblit.models.Metric;
import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.models.RefModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Comment;
import com.gitblit.utils.JGitUtils;

public class JGitUtilsTest extends TestCase {

	private List<String> getRepositories() {
		return JGitUtils.getRepositoryList(GitBlitSuite.REPOSITORIES, true, true);
	}

	public void testFindRepositories() {
		List<String> list = getRepositories();
		assertTrue("No repositories found in " + GitBlitSuite.REPOSITORIES, list.size() > 0);
	}

	public void testOpenRepository() throws Exception {
		Repository repository = GitBlitSuite.getHelloworldRepository();
		repository.close();
		assertTrue("Could not find repository!", repository != null);
	}

	public void testLastChangeRepository() throws Exception {
		Repository repository = GitBlitSuite.getHelloworldRepository();
		Date date = JGitUtils.getLastChange(repository);
		repository.close();
		assertTrue("Could not get last repository change date!", date != null);
	}

	public void testFirstCommit() throws Exception {
		Repository repository = GitBlitSuite.getHelloworldRepository();
		RevCommit commit = JGitUtils.getFirstCommit(repository, null);
		Date firstChange = JGitUtils.getFirstChange(repository, null);
		repository.close();
		assertTrue("Could not get first commit!", commit != null);
		assertTrue("Incorrect first commit!",
				commit.getName().equals("f554664a346629dc2b839f7292d06bad2db4aece"));
		assertTrue(firstChange.equals(new Date(commit.getCommitTime() * 1000L)));
	}

	public void testRefs() throws Exception {
		Repository repository = GitBlitSuite.getTicgitRepository();
		for (RefModel model : JGitUtils.getLocalBranches(repository, -1)) {
			assertTrue(model.getName().startsWith(Constants.R_HEADS));
			assertTrue(model.equals(model));
			assertFalse(model.equals(""));
			assertTrue(model.hashCode() == model.getCommitId().hashCode()
					+ model.getName().hashCode());
			assertTrue(model.getShortLog().equals(model.commit.getShortMessage()));
		}
		for (RefModel model : JGitUtils.getRemoteBranches(repository, -1)) {
			assertTrue(model.getName().startsWith(Constants.R_REMOTES));
			assertTrue(model.equals(model));
			assertFalse(model.equals(""));
			assertTrue(model.hashCode() == model.getCommitId().hashCode()
					+ model.getName().hashCode());
			assertTrue(model.getShortLog().equals(model.commit.getShortMessage()));
		}
		for (RefModel model : JGitUtils.getTags(repository, -1)) {
			if (model.getObjectId().getName().equals("283035e4848054ff1803cb0e690270787dc92399")) {
				assertTrue("Not an annotated tag!", model.isAnnotatedTag());
			}
			assertTrue(model.getName().startsWith(Constants.R_TAGS));
			assertTrue(model.equals(model));
			assertFalse(model.equals(""));
			assertTrue(model.hashCode() == model.getCommitId().hashCode()
					+ model.getName().hashCode());
			assertTrue(model.getShortLog().equals(model.commit.getShortMessage()));
		}
		repository.close();
	}

	public void testRetrieveRevObject() throws Exception {
		Repository repository = GitBlitSuite.getHelloworldRepository();
		RevCommit commit = JGitUtils.getCommit(repository, Constants.HEAD);
		RevTree tree = commit.getTree();
		RevObject object = JGitUtils.getRevObject(repository, tree, "java.java");
		repository.close();
		assertTrue("Object is null!", object != null);
	}

	public void testRetrieveStringContent() throws Exception {
		Repository repository = GitBlitSuite.getHelloworldRepository();
		RevCommit commit = JGitUtils.getCommit(repository, Constants.HEAD);
		RevTree tree = commit.getTree();
		RevBlob blob = (RevBlob) JGitUtils.getRevObject(repository, tree, "java.java");
		String content = JGitUtils.getRawContentAsString(repository, blob);
		repository.close();
		assertTrue("Content is null!", content != null && content.length() > 0);
	}

	public void testFilesInCommit() throws Exception {
		Repository repository = GitBlitSuite.getHelloworldRepository();
		RevCommit commit = JGitUtils.getCommit(repository,
				"1d0c2933a4ae69c362f76797d42d6bd182d05176");
		List<PathChangeModel> paths = JGitUtils.getFilesInCommit(repository, commit);
		repository.close();
		assertTrue("No changed paths found!", paths.size() == 1);
		for (PathChangeModel path : paths) {
			assertTrue("PathChangeModel hashcode incorrect!",
					path.hashCode() == (path.commitId.hashCode() + path.path.hashCode()));
			assertTrue("PathChangeModel equals itself failed!", path.equals(path));
			assertFalse("PathChangeModel equals string failed!", path.equals(""));
		}
	}

	public void testZip() throws Exception {
		Repository repository = GitBlitSuite.getHelloworldRepository();
		File zipFile = new File(GitBlitSuite.REPOSITORIES, "helloworld.zip");
		FileOutputStream fos = new FileOutputStream(zipFile);
		boolean success = JGitUtils.zip(repository, null, Constants.HEAD, fos);
		assertTrue("Failed to generate zip file!", success);
		assertTrue(zipFile.length() > 0);
		fos.close();
		zipFile.delete();
		repository.close();
	}

	public void testMetrics() throws Exception {
		Repository repository = GitBlitSuite.getHelloworldRepository();
		List<Metric> metrics = JGitUtils.getDateMetrics(repository);
		repository.close();
		assertTrue("No metrics found!", metrics.size() > 0);
	}

	public void testTicGit() throws Exception {
		Repository repository = GitBlitSuite.getTicgitRepository();
		RefModel branch = JGitUtils.getTicketsBranch(repository);
		assertTrue("Ticgit branch does not exist!", branch != null);
		List<TicketModel> ticketsA = JGitUtils.getTickets(repository);
		List<TicketModel> ticketsB = JGitUtils.getTickets(repository);
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
	}
}