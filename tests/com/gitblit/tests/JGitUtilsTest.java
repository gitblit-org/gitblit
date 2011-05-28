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

import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.models.RefModel;
import com.gitblit.models.TicketModel;
import com.gitblit.utils.DiffUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.JGitUtils.DiffOutputType;

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
		repository.close();
		assertTrue("Could not get first commit!", commit != null);
		assertTrue("Incorrect first commit!",
				commit.getName().equals("f554664a346629dc2b839f7292d06bad2db4aece"));
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
	}

	public void testCommitDiff() throws Exception {
		Repository repository = GitBlitSuite.getHelloworldRepository();
		RevCommit commit = JGitUtils.getCommit(repository,
				"1d0c2933a4ae69c362f76797d42d6bd182d05176");
		String diff = DiffUtils.getCommitDiff(repository, commit, DiffOutputType.PLAIN);
		repository.close();
		assertTrue("Failed to generate diff!", diff != null && diff.length() > 0);
		String expected = "-		system.out.println(\"Hello World\");\n+		System.out.println(\"Hello World\"";
		assertTrue("Diff content mismatch!", diff.indexOf(expected) > -1);
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

	public void testTicGit() throws Exception {
		Repository repository = GitBlitSuite.getTicgitRepository();
		RefModel branch = JGitUtils.getTicketsBranch(repository);
		assertTrue("Ticgit branch does not exist!", branch != null);
		List<TicketModel> tickets = JGitUtils.getTickets(repository);
		repository.close();
		assertTrue("No tickets found!", tickets.size() > 0);
	}
}