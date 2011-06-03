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
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.PathModel;
import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.models.RefModel;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.JGitUtils.SearchType;

public class JGitUtilsTest extends TestCase {

	public void testDisplayName() throws Exception {
		assertTrue(JGitUtils.getDisplayName(new PersonIdent("Napoleon Bonaparte", "")).equals(
				"Napoleon Bonaparte"));
		assertTrue(JGitUtils.getDisplayName(new PersonIdent("", "someone@somewhere.com")).equals(
				"<someone@somewhere.com>"));
		assertTrue(JGitUtils.getDisplayName(
				new PersonIdent("Napoleon Bonaparte", "someone@somewhere.com")).equals(
				"Napoleon Bonaparte <someone@somewhere.com>"));
	}

	public void testFindRepositories() {
		List<String> list = JGitUtils.getRepositoryList(null, true, true);
		assertTrue(list.size() == 0);
		list.addAll(JGitUtils.getRepositoryList(new File("DoesNotExist"), true, true));
		assertTrue(list.size() == 0);
		list.addAll(JGitUtils.getRepositoryList(GitBlitSuite.REPOSITORIES, true, true));
		assertTrue("No repositories found in " + GitBlitSuite.REPOSITORIES, list.size() > 0);
	}

	public void testOpenRepository() throws Exception {
		Repository repository = GitBlitSuite.getHelloworldRepository();
		repository.close();
		assertTrue("Could not find repository!", repository != null);
	}

	public void testFirstCommit() throws Exception {
		assertTrue(JGitUtils.getFirstChange(null, null).equals(new Date(0)));

		Repository repository = GitBlitSuite.getHelloworldRepository();
		RevCommit commit = JGitUtils.getFirstCommit(repository, null);
		Date firstChange = JGitUtils.getFirstChange(repository, null);
		repository.close();
		assertTrue("Could not get first commit!", commit != null);
		assertTrue("Incorrect first commit!",
				commit.getName().equals("f554664a346629dc2b839f7292d06bad2db4aece"));
		assertTrue(firstChange.equals(new Date(commit.getCommitTime() * 1000L)));
	}

	public void testLastCommit() throws Exception {
		assertTrue(JGitUtils.getLastChange(null).equals(new Date(0)));

		Repository repository = GitBlitSuite.getHelloworldRepository();
		assertTrue(JGitUtils.getCommit(repository, null) != null);
		Date date = JGitUtils.getLastChange(repository);
		repository.close();
		assertTrue("Could not get last repository change date!", date != null);
	}

	public void testCreateRepository() throws Exception {
		String[] repositories = { "NewTestRepository.git", "NewTestRepository" };
		for (String repositoryName : repositories) {
			boolean isBare = repositoryName.endsWith(".git");
			Repository repository = JGitUtils.createRepository(GitBlitSuite.REPOSITORIES,
					repositoryName, isBare);
			File folder;
			if (isBare) {
				folder = new File(GitBlitSuite.REPOSITORIES, repositoryName);
			} else {
				folder = new File(GitBlitSuite.REPOSITORIES, repositoryName + "/.git");
			}
			assertTrue(repository != null);
			assertFalse(JGitUtils.hasCommits(repository));
			assertTrue(JGitUtils.getFirstCommit(repository, null) == null);
			assertTrue(JGitUtils.getFirstChange(repository, null).getTime() == folder
					.lastModified());
			assertTrue(JGitUtils.getLastChange(repository).getTime() == folder.lastModified());
			assertTrue(JGitUtils.getCommit(repository, null) == null);
			repository.close();
			assertTrue(GitBlit.self().deleteRepository(repositoryName));
		}
	}

	public void testRefs() throws Exception {
		Repository repository = GitBlitSuite.getTicgitRepository();
		Map<ObjectId, List<String>> map = JGitUtils.getAllRefs(repository);
		repository.close();
		assertTrue(map.size() > 0);
	}

	public void testBranches() throws Exception {
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
		assertTrue(JGitUtils.getRemoteBranches(repository, 10).size() == 10);
		repository.close();
	}

	public void testTags() throws Exception {
		Repository repository = GitBlitSuite.getTicgitRepository();
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

	public void testCommitNotes() throws Exception {
//		Repository repository = new FileRepository(new File("c:/projects/git/jgit.git/.git"));
//		RevCommit commit = JGitUtils.getCommit(repository,
//				"ada903085d1b4ef8c79e3e2d91f49fee7e188f53");
//		List<GitNote> list = JGitUtils.getNotesOnCommit(repository, commit);
//		repository.close();
//		assertTrue(list.size() > 0);
	}

	public void testStringContent() throws Exception {
		Repository repository = GitBlitSuite.getHelloworldRepository();
		String contentA = JGitUtils.getRawContentAsString(repository, null, "java.java");
		RevCommit commit = JGitUtils.getCommit(repository, Constants.HEAD);
		String contentB = JGitUtils.getRawContentAsString(repository, commit, "java.java");
		String contentC = JGitUtils.getRawContentAsString(repository, commit, "missing.txt");
		repository.close();
		assertTrue("ContentA is null!", contentA != null && contentA.length() > 0);
		assertTrue("ContentB is null!", contentB != null && contentB.length() > 0);
		assertTrue(contentA.equals(contentB));
		assertTrue(contentC == null);
	}

	public void testFilesInCommit() throws Exception {
		Repository repository = GitBlitSuite.getHelloworldRepository();
		RevCommit commit = JGitUtils.getCommit(repository,
				"1d0c2933a4ae69c362f76797d42d6bd182d05176");
		List<PathChangeModel> paths = JGitUtils.getFilesInCommit(repository, commit);

		commit = JGitUtils.getCommit(repository, "af0e9b2891fda85afc119f04a69acf7348922830");
		List<PathChangeModel> deletions = JGitUtils.getFilesInCommit(repository, commit);

		commit = JGitUtils.getFirstCommit(repository, null);
		List<PathChangeModel> additions = JGitUtils.getFilesInCommit(repository, commit);

		List<PathChangeModel> latestChanges = JGitUtils.getFilesInCommit(repository, null);

		repository.close();
		assertTrue("No changed paths found!", paths.size() == 1);
		for (PathChangeModel path : paths) {
			assertTrue("PathChangeModel hashcode incorrect!",
					path.hashCode() == (path.commitId.hashCode() + path.path.hashCode()));
			assertTrue("PathChangeModel equals itself failed!", path.equals(path));
			assertFalse("PathChangeModel equals string failed!", path.equals(""));
		}
		assertTrue(deletions.get(0).changeType.equals(ChangeType.DELETE));
		assertTrue(additions.get(0).changeType.equals(ChangeType.ADD));
		assertTrue(latestChanges.size() > 0);
	}

	public void testFilesInPath() throws Exception {
		assertTrue(JGitUtils.getFilesInPath(null, null, null).size() == 0);
		Repository repository = GitBlitSuite.getHelloworldRepository();
		List<PathModel> files = JGitUtils.getFilesInPath(repository, null, null);
		repository.close();
		assertTrue(files.size() > 10);
	}

	public void testDocuments() throws Exception {
		Repository repository = GitBlitSuite.getTicgitRepository();
		List<String> extensions = GitBlit.getStrings(Keys.web.markdownExtensions);
		List<PathModel> markdownDocs = JGitUtils.getDocuments(repository, extensions);
		List<PathModel> markdownDocs2 = JGitUtils.getDocuments(repository,
				Arrays.asList(new String[] { ".mkd", ".md" }));
		List<PathModel> allFiles = JGitUtils.getDocuments(repository, null);
		repository.close();
		assertTrue(markdownDocs.size() > 0);
		assertTrue(markdownDocs2.size() > 0);
		assertTrue(allFiles.size() > markdownDocs.size());
	}

	public void testFileModes() throws Exception {
		assertTrue(JGitUtils.getPermissionsFromMode(FileMode.TREE.getBits()).equals("drwxr-xr-x"));
		assertTrue(JGitUtils.getPermissionsFromMode(FileMode.REGULAR_FILE.getBits()).equals(
				"-rw-r--r--"));
		assertTrue(JGitUtils.getPermissionsFromMode(FileMode.EXECUTABLE_FILE.getBits()).equals(
				"-rwxr-xr-x"));
		assertTrue(JGitUtils.getPermissionsFromMode(FileMode.SYMLINK.getBits()).equals("symlink"));
		assertTrue(JGitUtils.getPermissionsFromMode(FileMode.GITLINK.getBits()).equals("gitlink"));
		assertTrue(JGitUtils.getPermissionsFromMode(FileMode.MISSING.getBits()).equals("missing"));
	}

	public void testRevlog() throws Exception {
		List<RevCommit> commits = JGitUtils.getRevLog(null, 10);
		assertTrue(commits.size() == 0);

		Repository repository = GitBlitSuite.getHelloworldRepository();
		// get most recent 10 commits
		commits = JGitUtils.getRevLog(repository, 10);
		assertTrue(commits.size() == 10);

		// test paging and offset by getting the 10th most recent commit
		RevCommit lastCommit = JGitUtils.getRevLog(repository, null, 9, 1).get(0);
		assertTrue(commits.get(9).equals(lastCommit));

		// grab the two most recent commits to java.java
		commits = JGitUtils.getRevLog(repository, null, "java.java", 0, 2);
		assertTrue(commits.size() == 2);
		repository.close();
	}

	public void testSearchTypes() throws Exception {
		assertTrue(SearchType.forName("commit").equals(SearchType.COMMIT));
		assertTrue(SearchType.forName("committer").equals(SearchType.COMMITTER));
		assertTrue(SearchType.forName("author").equals(SearchType.AUTHOR));
		assertTrue(SearchType.forName("unknown").equals(SearchType.COMMIT));

		assertTrue(SearchType.COMMIT.toString().equals("commit"));
		assertTrue(SearchType.COMMITTER.toString().equals("committer"));
		assertTrue(SearchType.AUTHOR.toString().equals("author"));
	}

	public void testSearchRevlogs() throws Exception {
		List<RevCommit> results = JGitUtils.searchRevlogs(null, null, "java", SearchType.COMMIT, 0,
				3);
		assertTrue(results.size() == 0);

		// test commit message search
		Repository repository = GitBlitSuite.getHelloworldRepository();
		results = JGitUtils.searchRevlogs(repository, null, "java", SearchType.COMMIT, 0, 3);
		assertTrue(results.size() == 3);

		// test author search
		results = JGitUtils.searchRevlogs(repository, null, "timothy", SearchType.AUTHOR, 0, -1);
		assertTrue(results.size() == 1);

		// test committer search
		results = JGitUtils.searchRevlogs(repository, null, "mike", SearchType.COMMITTER, 0, 10);
		assertTrue(results.size() == 10);

		// test paging and offset
		RevCommit commit = JGitUtils.searchRevlogs(repository, null, "mike", SearchType.COMMITTER,
				9, 1).get(0);
		assertTrue(results.get(9).equals(commit));

		repository.close();
	}

	public void testZip() throws Exception {
		assertFalse(JGitUtils.zip(null, null, null, null));
		Repository repository = GitBlitSuite.getHelloworldRepository();
		File zipFileA = new File(GitBlitSuite.REPOSITORIES, "helloworld.zip");
		FileOutputStream fosA = new FileOutputStream(zipFileA);
		boolean successA = JGitUtils.zip(repository, null, Constants.HEAD, fosA);
		fosA.close();

		File zipFileB = new File(GitBlitSuite.REPOSITORIES, "helloworld-java.zip");
		FileOutputStream fosB = new FileOutputStream(zipFileB);
		boolean successB = JGitUtils.zip(repository, "java.java", Constants.HEAD, fosB);
		fosB.close();

		repository.close();
		assertTrue("Failed to generate zip file!", successA);
		assertTrue(zipFileA.length() > 0);
		zipFileA.delete();

		assertTrue("Failed to generate zip file!", successB);
		assertTrue(zipFileB.length() > 0);
		zipFileB.delete();
	}
}