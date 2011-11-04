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
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.GitNote;
import com.gitblit.models.PathModel;
import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.models.RefModel;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;

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
		assertTrue(JGitUtils.getLastChange(null, null).equals(new Date(0)));

		Repository repository = GitBlitSuite.getHelloworldRepository();
		assertTrue(JGitUtils.getCommit(repository, null) != null);
		Date date = JGitUtils.getLastChange(repository, null);
		repository.close();
		assertTrue("Could not get last repository change date!", date != null);
	}

	public void testCreateRepository() throws Exception {
		String[] repositories = { "NewTestRepository.git", "NewTestRepository" };
		for (String repositoryName : repositories) {
			Repository repository = JGitUtils.createRepository(GitBlitSuite.REPOSITORIES,
					repositoryName);
			File folder = FileKey.resolve(new File(GitBlitSuite.REPOSITORIES, repositoryName),
					FS.DETECTED);
			assertTrue(repository != null);
			assertFalse(JGitUtils.hasCommits(repository));
			assertTrue(JGitUtils.getFirstCommit(repository, null) == null);
			assertTrue(JGitUtils.getFirstChange(repository, null).getTime() == folder
					.lastModified());
			assertTrue(JGitUtils.getLastChange(repository, null).getTime() == folder.lastModified());
			assertTrue(JGitUtils.getCommit(repository, null) == null);
			repository.close();
			assertTrue(GitBlit.self().deleteRepository(repositoryName));
		}
	}

	public void testRefs() throws Exception {
		Repository repository = GitBlitSuite.getJGitRepository();
		Map<ObjectId, List<RefModel>> map = JGitUtils.getAllRefs(repository);
		repository.close();
		assertTrue(map.size() > 0);
		for (Map.Entry<ObjectId, List<RefModel>> entry : map.entrySet()) {
			List<RefModel> list = entry.getValue();
			for (RefModel ref : list) {
				if (ref.displayName.equals("refs/tags/spearce-gpg-pub")) {
					assertTrue(ref.toString().equals("refs/tags/spearce-gpg-pub"));
					assertTrue(ref.getObjectId().getName()
							.equals("8bbde7aacf771a9afb6992434f1ae413e010c6d8"));
					assertTrue(ref.getAuthorIdent().getEmailAddress().equals("spearce@spearce.org"));
					assertTrue(ref.getShortMessage().startsWith("GPG key"));
					assertTrue(ref.getFullMessage().startsWith("GPG key"));
					assertTrue(ref.getReferencedObjectType() == Constants.OBJ_BLOB);
				} else if (ref.displayName.equals("refs/tags/v0.12.1")) {
					assertTrue(ref.isAnnotatedTag());
				}
			}
		}
	}

	public void testBranches() throws Exception {
		Repository repository = GitBlitSuite.getJGitRepository();
		assertTrue(JGitUtils.getLocalBranches(repository, true, 0).size() == 0);
		for (RefModel model : JGitUtils.getLocalBranches(repository, true, -1)) {
			assertTrue(model.getName().startsWith(Constants.R_HEADS));
			assertTrue(model.equals(model));
			assertFalse(model.equals(""));
			assertTrue(model.hashCode() == model.getReferencedObjectId().hashCode()
					+ model.getName().hashCode());
			assertTrue(model.getShortMessage().equals(model.getShortMessage()));
		}
		for (RefModel model : JGitUtils.getRemoteBranches(repository, true, -1)) {
			assertTrue(model.getName().startsWith(Constants.R_REMOTES));
			assertTrue(model.equals(model));
			assertFalse(model.equals(""));
			assertTrue(model.hashCode() == model.getReferencedObjectId().hashCode()
					+ model.getName().hashCode());
			assertTrue(model.getShortMessage().equals(model.getShortMessage()));
		}
		assertTrue(JGitUtils.getRemoteBranches(repository, true, 8).size() == 8);
		repository.close();
	}

	public void testTags() throws Exception {
		Repository repository = GitBlitSuite.getJGitRepository();
		assertTrue(JGitUtils.getTags(repository, true, 5).size() == 5);
		for (RefModel model : JGitUtils.getTags(repository, true, -1)) {
			if (model.getObjectId().getName().equals("d28091fb2977077471138fe97da1440e0e8ae0da")) {
				assertTrue("Not an annotated tag!", model.isAnnotatedTag());
			}
			assertTrue(model.getName().startsWith(Constants.R_TAGS));
			assertTrue(model.equals(model));
			assertFalse(model.equals(""));
			assertTrue(model.hashCode() == model.getReferencedObjectId().hashCode()
					+ model.getName().hashCode());
		}
		repository.close();

		repository = GitBlitSuite.getBluezGnomeRepository();
		for (RefModel model : JGitUtils.getTags(repository, true, -1)) {
			if (model.getObjectId().getName().equals("728643ec0c438c77e182898c2f2967dbfdc231c8")) {
				assertFalse(model.isAnnotatedTag());
				assertTrue(model.getAuthorIdent().getEmailAddress().equals("marcel@holtmann.org"));
				assertTrue(model.getFullMessage().equals(
						"Update changelog and bump version number\n"));
			}
		}
		repository.close();
	}

	public void testCommitNotes() throws Exception {
		Repository repository = GitBlitSuite.getJGitRepository();
		RevCommit commit = JGitUtils.getCommit(repository,
				"690c268c793bfc218982130fbfc25870f292295e");
		List<GitNote> list = JGitUtils.getNotesOnCommit(repository, commit);
		repository.close();
		assertTrue(list.size() > 0);
		assertTrue(list.get(0).notesRef.getReferencedObjectId().getName()
				.equals("183474d554e6f68478a02d9d7888b67a9338cdff"));
	}

	public void testCreateOrphanedBranch() throws Exception {
		Repository repository = JGitUtils.createRepository(GitBlitSuite.REPOSITORIES, "orphantest");
		assertTrue(JGitUtils.createOrphanBranch(repository,
				"x" + Long.toHexString(System.currentTimeMillis()).toUpperCase()));
		FileUtils.delete(repository.getDirectory(), FileUtils.RECURSIVE);
	}

	public void testStringContent() throws Exception {
		Repository repository = GitBlitSuite.getHelloworldRepository();
		String contentA = JGitUtils.getStringContent(repository, null, "java.java");
		RevCommit commit = JGitUtils.getCommit(repository, Constants.HEAD);
		String contentB = JGitUtils.getStringContent(repository, commit.getTree(), "java.java");
		String contentC = JGitUtils.getStringContent(repository, commit.getTree(), "missing.txt");

		// manually construct a blob, calculate the hash, lookup the hash in git
		StringBuilder sb = new StringBuilder();
		sb.append("blob ").append(contentA.length()).append('\0');
		sb.append(contentA);
		String sha1 = StringUtils.getSHA1(sb.toString());
		String contentD = JGitUtils.getStringContent(repository, sha1);
		repository.close();
		assertTrue("ContentA is null!", contentA != null && contentA.length() > 0);
		assertTrue("ContentB is null!", contentB != null && contentB.length() > 0);
		assertTrue(contentA.equals(contentB));
		assertTrue(contentC == null);
		assertTrue(contentA.equals(contentD));
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
		assertTrue(JGitUtils.getRevLog(null, 0).size() == 0);
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
		assertTrue(com.gitblit.Constants.SearchType.forName("commit").equals(com.gitblit.Constants.SearchType.COMMIT));
		assertTrue(com.gitblit.Constants.SearchType.forName("committer").equals(com.gitblit.Constants.SearchType.COMMITTER));
		assertTrue(com.gitblit.Constants.SearchType.forName("author").equals(com.gitblit.Constants.SearchType.AUTHOR));
		assertTrue(com.gitblit.Constants.SearchType.forName("unknown").equals(com.gitblit.Constants.SearchType.COMMIT));

		assertTrue(com.gitblit.Constants.SearchType.COMMIT.toString().equals("commit"));
		assertTrue(com.gitblit.Constants.SearchType.COMMITTER.toString().equals("committer"));
		assertTrue(com.gitblit.Constants.SearchType.AUTHOR.toString().equals("author"));
	}

	public void testSearchRevlogs() throws Exception {
		assertTrue(JGitUtils.searchRevlogs(null, null, "java", com.gitblit.Constants.SearchType.COMMIT, 0, 0).size() == 0);
		List<RevCommit> results = JGitUtils.searchRevlogs(null, null, "java", com.gitblit.Constants.SearchType.COMMIT, 0,
				3);
		assertTrue(results.size() == 0);

		// test commit message search
		Repository repository = GitBlitSuite.getHelloworldRepository();
		results = JGitUtils.searchRevlogs(repository, null, "java", com.gitblit.Constants.SearchType.COMMIT, 0, 3);
		assertTrue(results.size() == 3);

		// test author search
		results = JGitUtils.searchRevlogs(repository, null, "timothy", com.gitblit.Constants.SearchType.AUTHOR, 0, -1);
		assertTrue(results.size() == 1);

		// test committer search
		results = JGitUtils.searchRevlogs(repository, null, "mike", com.gitblit.Constants.SearchType.COMMITTER, 0, 10);
		assertTrue(results.size() == 10);

		// test paging and offset
		RevCommit commit = JGitUtils.searchRevlogs(repository, null, "mike", com.gitblit.Constants.SearchType.COMMITTER,
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