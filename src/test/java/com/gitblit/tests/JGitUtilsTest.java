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
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.revplot.PlotCommit;
import org.eclipse.jgit.revplot.PlotCommitList;
import org.eclipse.jgit.revplot.PlotLane;
import org.eclipse.jgit.revplot.PlotWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Test;

import com.gitblit.Constants.SearchType;
import com.gitblit.models.GitNote;
import com.gitblit.models.PathModel;
import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.models.RefModel;
import com.gitblit.utils.CompressionUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.JnaUtils;
import com.gitblit.utils.StringUtils;

public class JGitUtilsTest extends GitblitUnitTest {

	@Test
	public void testDisplayName() throws Exception {
		assertEquals("Napoleon Bonaparte",
				JGitUtils.getDisplayName(new PersonIdent("Napoleon Bonaparte", "")));
		assertEquals("<someone@somewhere.com>",
				JGitUtils.getDisplayName(new PersonIdent("", "someone@somewhere.com")));
		assertEquals("Napoleon Bonaparte <someone@somewhere.com>",
				JGitUtils.getDisplayName(new PersonIdent("Napoleon Bonaparte",
						"someone@somewhere.com")));
	}

	@Test
	public void testFindRepositories() {
		List<String> list = JGitUtils.getRepositoryList(null, false, true, -1, null);
		assertEquals(0, list.size());
		list.addAll(JGitUtils.getRepositoryList(new File("DoesNotExist"), true, true, -1, null));
		assertEquals(0, list.size());
		list.addAll(JGitUtils.getRepositoryList(GitBlitSuite.REPOSITORIES, false, true, -1, null));
		assertTrue("No repositories found in " + GitBlitSuite.REPOSITORIES, list.size() > 0);
	}

	@Test
	public void testFindExclusions() {
		List<String> list = JGitUtils.getRepositoryList(GitBlitSuite.REPOSITORIES, false, true, -1, null);
		assertTrue("Missing jgit repository?!", list.contains("test/jgit.git"));

		list = JGitUtils.getRepositoryList(GitBlitSuite.REPOSITORIES, false, true, -1, Arrays.asList("test/jgit\\.git"));
		assertFalse("Repository exclusion failed!", list.contains("test/jgit.git"));

		list = JGitUtils.getRepositoryList(GitBlitSuite.REPOSITORIES, false, true, -1, Arrays.asList("test/*"));
		assertFalse("Repository exclusion failed!", list.contains("test/jgit.git"));

		list = JGitUtils.getRepositoryList(GitBlitSuite.REPOSITORIES, false, true, -1, Arrays.asList(".*jgit.*"));
		assertFalse("Repository exclusion failed!", list.contains("test/jgit.git"));
		assertFalse("Repository exclusion failed!", list.contains("working/jgit"));
		assertFalse("Repository exclusion failed!", list.contains("working/jgit2"));

	}

	@Test
	public void testOpenRepository() throws Exception {
		Repository repository = GitBlitSuite.getHelloworldRepository();
		repository.close();
		assertNotNull("Could not find repository!", repository);
	}

	@Test
	public void testFirstCommit() throws Exception {
		assertEquals(new Date(0), JGitUtils.getFirstChange(null, null));

		Repository repository = GitBlitSuite.getHelloworldRepository();
		RevCommit commit = JGitUtils.getFirstCommit(repository, null);
		Date firstChange = JGitUtils.getFirstChange(repository, null);
		repository.close();
		assertNotNull("Could not get first commit!", commit);
		assertEquals("Incorrect first commit!", "f554664a346629dc2b839f7292d06bad2db4aece",
				commit.getName());
		assertTrue(firstChange.equals(new Date(commit.getCommitTime() * 1000L)));
	}

	@Test
	public void testLastCommit() throws Exception {
		assertEquals(new Date(0), JGitUtils.getLastChange(null).when);

		Repository repository = GitBlitSuite.getHelloworldRepository();
		assertTrue(JGitUtils.getCommit(repository, null) != null);
		Date date = JGitUtils.getLastChange(repository).when;
		repository.close();
		assertNotNull("Could not get last repository change date!", date);
	}

	@Test
	public void testCreateRepository() throws Exception {
		String[] repositories = { "NewTestRepository.git", "NewTestRepository" };
		for (String repositoryName : repositories) {
			Repository repository = JGitUtils.createRepository(GitBlitSuite.REPOSITORIES,
					repositoryName);
			File folder = FileKey.resolve(new File(GitBlitSuite.REPOSITORIES, repositoryName),
					FS.DETECTED);
			assertNotNull(repository);
			assertFalse(JGitUtils.hasCommits(repository));
			assertNull(JGitUtils.getFirstCommit(repository, null));
			assertEquals(folder.lastModified(), JGitUtils.getFirstChange(repository, null)
					.getTime());
			assertEquals(folder.lastModified(), JGitUtils.getLastChange(repository).when.getTime());
			assertNull(JGitUtils.getCommit(repository, null));
			repository.close();
			RepositoryCache.close(repository);
			FileUtils.delete(repository.getDirectory(), FileUtils.RECURSIVE);
		}
	}

	@Test
	public void testCreateRepositoryShared() throws Exception {
		String[] repositories = { "NewSharedTestRepository.git" };
		for (String repositoryName : repositories) {
			Repository repository = JGitUtils.createRepository(GitBlitSuite.REPOSITORIES,
					repositoryName, "group");
			File folder = FileKey.resolve(new File(GitBlitSuite.REPOSITORIES, repositoryName),
					FS.DETECTED);
			assertNotNull(repository);
			assertFalse(JGitUtils.hasCommits(repository));
			assertNull(JGitUtils.getFirstCommit(repository, null));

			assertEquals("1", repository.getConfig().getString("core", null, "sharedRepository"));

			assertTrue(folder.exists());
			if (! JnaUtils.isWindows()) {
				int mode = JnaUtils.getFilemode(folder);
				assertEquals(JnaUtils.S_ISGID, mode & JnaUtils.S_ISGID);
				assertEquals(JnaUtils.S_IRWXG, mode & JnaUtils.S_IRWXG);

				mode = JnaUtils.getFilemode(folder.getAbsolutePath() + "/HEAD");
				assertEquals(JnaUtils.S_IRGRP | JnaUtils.S_IWGRP, mode & JnaUtils.S_IRWXG);

				mode = JnaUtils.getFilemode(folder.getAbsolutePath() + "/config");
				assertEquals(JnaUtils.S_IRGRP | JnaUtils.S_IWGRP, mode & JnaUtils.S_IRWXG);
			}

			repository.close();
			RepositoryCache.close(repository);
			FileUtils.delete(repository.getDirectory(), FileUtils.RECURSIVE);
		}
	}

	@Test
	public void testCreateRepositorySharedCustom() throws Exception {
		String[] repositories = { "NewSharedTestRepository.git" };
		for (String repositoryName : repositories) {
			Repository repository = JGitUtils.createRepository(GitBlitSuite.REPOSITORIES,
					repositoryName, "660");
			File folder = FileKey.resolve(new File(GitBlitSuite.REPOSITORIES, repositoryName),
					FS.DETECTED);
			assertNotNull(repository);
			assertFalse(JGitUtils.hasCommits(repository));
			assertNull(JGitUtils.getFirstCommit(repository, null));

			assertEquals("0660", repository.getConfig().getString("core", null, "sharedRepository"));

			assertTrue(folder.exists());
			if (! JnaUtils.isWindows()) {
				int mode = JnaUtils.getFilemode(folder);
				assertEquals(JnaUtils.S_ISGID, mode & JnaUtils.S_ISGID);
				assertEquals(JnaUtils.S_IRWXG, mode & JnaUtils.S_IRWXG);
				assertEquals(0, mode & JnaUtils.S_IRWXO);

				mode = JnaUtils.getFilemode(folder.getAbsolutePath() + "/HEAD");
				assertEquals(JnaUtils.S_IRGRP | JnaUtils.S_IWGRP, mode & JnaUtils.S_IRWXG);
				assertEquals(0, mode & JnaUtils.S_IRWXO);

				mode = JnaUtils.getFilemode(folder.getAbsolutePath() + "/config");
				assertEquals(JnaUtils.S_IRGRP | JnaUtils.S_IWGRP, mode & JnaUtils.S_IRWXG);
				assertEquals(0, mode & JnaUtils.S_IRWXO);
			}

			repository.close();
			RepositoryCache.close(repository);
			FileUtils.delete(repository.getDirectory(), FileUtils.RECURSIVE);
		}
	}

	@Test
	public void testCreateRepositorySharedSgidParent() throws Exception {
		if (! JnaUtils.isWindows()) {
			String repositoryAll = "NewTestRepositoryAll.git";
			String repositoryUmask = "NewTestRepositoryUmask.git";
			String sgidParent = "sgid";

			File parent = new File(GitBlitSuite.REPOSITORIES, sgidParent);
			File folder = null;
			boolean parentExisted = parent.exists();
			try {
				if (!parentExisted) {
					assertTrue("Could not create SGID parent folder.", parent.mkdir());
				}
				int mode = JnaUtils.getFilemode(parent);
				assertTrue(mode > 0);
				assertEquals(0, JnaUtils.setFilemode(parent, mode | JnaUtils.S_ISGID | JnaUtils.S_IWGRP));

				Repository repository = JGitUtils.createRepository(parent, repositoryAll, "all");
				folder = FileKey.resolve(new File(parent, repositoryAll), FS.DETECTED);
				assertNotNull(repository);

				assertEquals("2", repository.getConfig().getString("core", null, "sharedRepository"));

				assertTrue(folder.exists());
				mode = JnaUtils.getFilemode(folder);
				assertEquals(JnaUtils.S_ISGID, mode & JnaUtils.S_ISGID);

				mode = JnaUtils.getFilemode(folder.getAbsolutePath() + "/HEAD");
				assertEquals(JnaUtils.S_IRGRP | JnaUtils.S_IWGRP, mode & JnaUtils.S_IRWXG);
				assertEquals(JnaUtils.S_IROTH, mode & JnaUtils.S_IRWXO);

				mode = JnaUtils.getFilemode(folder.getAbsolutePath() + "/config");
				assertEquals(JnaUtils.S_IRGRP | JnaUtils.S_IWGRP, mode & JnaUtils.S_IRWXG);
				assertEquals(JnaUtils.S_IROTH, mode & JnaUtils.S_IRWXO);

				repository.close();
				RepositoryCache.close(repository);



				repository = JGitUtils.createRepository(parent, repositoryUmask, "umask");
				folder = FileKey.resolve(new File(parent, repositoryUmask), FS.DETECTED);
				assertNotNull(repository);

				assertEquals(null, repository.getConfig().getString("core", null, "sharedRepository"));

				assertTrue(folder.exists());
				mode = JnaUtils.getFilemode(folder);
				assertEquals(JnaUtils.S_ISGID, mode & JnaUtils.S_ISGID);

				repository.close();
				RepositoryCache.close(repository);
			}
			finally {
				FileUtils.delete(new File(parent, repositoryAll), FileUtils.RECURSIVE | FileUtils.IGNORE_ERRORS);
				FileUtils.delete(new File(parent, repositoryUmask), FileUtils.RECURSIVE | FileUtils.IGNORE_ERRORS);
				if (!parentExisted) {
					FileUtils.delete(parent, FileUtils.RECURSIVE | FileUtils.IGNORE_ERRORS);
				}
			}
		}
	}

	@Test
	public void testRefs() throws Exception {
		Repository repository = GitBlitSuite.getJGitRepository();
		Map<ObjectId, List<RefModel>> map = JGitUtils.getAllRefs(repository);
		repository.close();
		assertTrue(map.size() > 0);
		for (Map.Entry<ObjectId, List<RefModel>> entry : map.entrySet()) {
			List<RefModel> list = entry.getValue();
			for (RefModel ref : list) {
				if (ref.displayName.equals("refs/tags/spearce-gpg-pub")) {
					assertEquals("refs/tags/spearce-gpg-pub", ref.toString());
					assertEquals("8bbde7aacf771a9afb6992434f1ae413e010c6d8", ref.getObjectId()
							.getName());
					assertEquals("spearce@spearce.org", ref.getAuthorIdent().getEmailAddress());
					assertTrue(ref.getShortMessage().startsWith("GPG key"));
					assertTrue(ref.getFullMessage().startsWith("GPG key"));
					assertEquals(Constants.OBJ_BLOB, ref.getReferencedObjectType());
				} else if (ref.displayName.equals("refs/tags/v0.12.1")) {
					assertTrue(ref.isAnnotatedTag());
				}
			}
		}
	}

	@Test
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

	@Test
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

		repository = GitBlitSuite.getGitectiveRepository();
		for (RefModel model : JGitUtils.getTags(repository, true, -1)) {
			if (model.getObjectId().getName().equals("035254295a9bba11f72b1f9d6791a6b957abee7b")) {
				assertFalse(model.isAnnotatedTag());
				assertTrue(model.getAuthorIdent().getEmailAddress().equals("kevinsawicki@gmail.com"));
				assertEquals("Add scm and issue tracker elements to pom.xml\n", model.getFullMessage());
			}
		}
		repository.close();
	}

	@Test
	public void testCommitNotes() throws Exception {
		Repository repository = GitBlitSuite.getJGitRepository();
		RevCommit commit = JGitUtils.getCommit(repository,
				"690c268c793bfc218982130fbfc25870f292295e");
		List<GitNote> list = JGitUtils.getNotesOnCommit(repository, commit);
		repository.close();
		assertTrue(list.size() > 0);
		assertEquals("183474d554e6f68478a02d9d7888b67a9338cdff", list.get(0).notesRef
				.getReferencedObjectId().getName());
	}

	@Test
	public void testRelinkHEAD() throws Exception {
		Repository repository = GitBlitSuite.getJGitRepository();
		// confirm HEAD is master
		String currentRef = JGitUtils.getHEADRef(repository);
		assertEquals("refs/heads/master", currentRef);
		List<String> availableHeads = JGitUtils.getAvailableHeadTargets(repository);
		assertTrue(availableHeads.size() > 0);

		// set HEAD to stable-1.2
		JGitUtils.setHEADtoRef(repository, "refs/heads/stable-1.2");
		currentRef = JGitUtils.getHEADRef(repository);
		assertEquals("refs/heads/stable-1.2", currentRef);

		// restore HEAD to master
		JGitUtils.setHEADtoRef(repository, "refs/heads/master");
		currentRef = JGitUtils.getHEADRef(repository);
		assertEquals("refs/heads/master", currentRef);

		repository.close();
	}

	@Test
	public void testRelinkBranch() throws Exception {
		Repository repository = GitBlitSuite.getJGitRepository();

		// create/set the branch
		JGitUtils.setBranchRef(repository, "refs/heads/reftest", "3b358ce514ec655d3ff67de1430994d8428cdb04");
		assertEquals(1, JGitUtils.getAllRefs(repository).get(ObjectId.fromString("3b358ce514ec655d3ff67de1430994d8428cdb04")).size());
		assertEquals(null, JGitUtils.getAllRefs(repository).get(ObjectId.fromString("755dfdb40948f5c1ec79e06bde3b0a78c352f27f")));

		// reset the branch
		JGitUtils.setBranchRef(repository, "refs/heads/reftest", "755dfdb40948f5c1ec79e06bde3b0a78c352f27f");
		assertEquals(null, JGitUtils.getAllRefs(repository).get(ObjectId.fromString("3b358ce514ec655d3ff67de1430994d8428cdb04")));
		assertEquals(1, JGitUtils.getAllRefs(repository).get(ObjectId.fromString("755dfdb40948f5c1ec79e06bde3b0a78c352f27f")).size());

		// delete the branch
		assertTrue(JGitUtils.deleteBranchRef(repository, "refs/heads/reftest"));
		repository.close();
	}

	@Test
	public void testCreateOrphanedBranch() throws Exception {
		Repository repository = JGitUtils.createRepository(GitBlitSuite.REPOSITORIES, "orphantest");
		assertTrue(JGitUtils.createOrphanBranch(repository,
				"x" + Long.toHexString(System.currentTimeMillis()).toUpperCase(), null));
		 FileUtils.delete(repository.getDirectory(), FileUtils.RECURSIVE);
	}

	@Test
	public void testStringContent() throws Exception {
		Repository repository = GitBlitSuite.getHelloworldRepository();
		String contentA = JGitUtils.getStringContent(repository, (RevTree) null, "java.java");
		RevCommit commit = JGitUtils.getCommit(repository, Constants.HEAD);
		String contentB = JGitUtils.getStringContent(repository, commit.getTree(), "java.java");

		assertTrue("ContentA is null!", contentA != null && contentA.length() > 0);
		assertTrue("ContentB is null!", contentB != null && contentB.length() > 0);
		assertTrue(contentA.equals(contentB));

		String contentC = JGitUtils.getStringContent(repository, commit.getTree(), "missing.txt");

		// manually construct a blob, calculate the hash, lookup the hash in git
		StringBuilder sb = new StringBuilder();
		sb.append("blob ").append(contentA.length()).append('\0');
		sb.append(contentA);
		String sha1 = StringUtils.getSHA1(sb.toString());
		String contentD = JGitUtils.getStringContent(repository, sha1);
		repository.close();
		assertNull(contentC);
		assertTrue(contentA.equals(contentD));
	}

	@Test
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
		assertEquals(ChangeType.DELETE, deletions.get(0).changeType);
		assertEquals(ChangeType.ADD, additions.get(0).changeType);
		assertTrue(latestChanges.size() > 0);
	}

	@Test
	public void testFilesInPath() throws Exception {
		assertEquals(0, JGitUtils.getFilesInPath(null, null, null).size());
		Repository repository = GitBlitSuite.getHelloworldRepository();
		List<PathModel> files = JGitUtils.getFilesInPath(repository, null, null);
		repository.close();
		assertTrue(files.size() > 10);
	}

	@Test
	public void testFilesInPath2() throws Exception {
		assertEquals(0, JGitUtils.getFilesInPath2(null, null, null).size());
		Repository repository = GitBlitSuite.getHelloworldRepository();
		List<PathModel> files = JGitUtils.getFilesInPath2(repository, null, null);
		repository.close();
		assertTrue(files.size() > 10);
	}

	@Test
	public void testDocuments() throws Exception {
		Repository repository = GitBlitSuite.getTicgitRepository();
		List<String> extensions = Arrays.asList(new String[] { ".mkd", ".md" });
		List<PathModel> markdownDocs = JGitUtils.getDocuments(repository, extensions);
		List<PathModel> allFiles = JGitUtils.getDocuments(repository, null);
		repository.close();
		assertTrue(markdownDocs.size() > 0);
		assertTrue(allFiles.size() > markdownDocs.size());
	}

	@Test
	public void testFileModes() throws Exception {
		assertEquals("drwxr-xr-x", JGitUtils.getPermissionsFromMode(FileMode.TREE.getBits()));
		assertEquals("-rw-r--r--",
				JGitUtils.getPermissionsFromMode(FileMode.REGULAR_FILE.getBits()));
		assertEquals("-rwxr-xr-x",
				JGitUtils.getPermissionsFromMode(FileMode.EXECUTABLE_FILE.getBits()));
		assertEquals("symlink", JGitUtils.getPermissionsFromMode(FileMode.SYMLINK.getBits()));
		assertEquals("submodule", JGitUtils.getPermissionsFromMode(FileMode.GITLINK.getBits()));
		assertEquals("missing", JGitUtils.getPermissionsFromMode(FileMode.MISSING.getBits()));
	}

	@Test
	public void testRevlog() throws Exception {
		assertTrue(JGitUtils.getRevLog(null, 0).size() == 0);
		List<RevCommit> commits = JGitUtils.getRevLog(null, 10);
		assertEquals(0, commits.size());

		Repository repository = GitBlitSuite.getHelloworldRepository();
		// get most recent 10 commits
		commits = JGitUtils.getRevLog(repository, 10);
		assertEquals(10, commits.size());

		// test paging and offset by getting the 10th most recent commit
		RevCommit lastCommit = JGitUtils.getRevLog(repository, null, 9, 1).get(0);
		assertEquals(lastCommit, commits.get(9));

		// grab the two most recent commits to java.java
		commits = JGitUtils.getRevLog(repository, null, "java.java", 0, 2);
		assertEquals(2, commits.size());

		// grab the commits since 2008-07-15
		commits = JGitUtils.getRevLog(repository, null,
				new SimpleDateFormat("yyyy-MM-dd").parse("2008-07-15"));
		assertEquals(12, commits.size());
		repository.close();
	}

	@Test
	public void testRevLogRange() throws Exception {
		Repository repository = GitBlitSuite.getHelloworldRepository();
		List<RevCommit> commits = JGitUtils.getRevLog(repository,
				"fbd14fa6d1a01d4aefa1fca725792683800fc67e",
				"85a0e4087b8439c0aa6b1f4f9e08c26052ab7e87");
		repository.close();
		assertEquals(14, commits.size());
	}

	@Test
	public void testSearchTypes() throws Exception {
		assertEquals(SearchType.COMMIT, SearchType.forName("commit"));
		assertEquals(SearchType.COMMITTER, SearchType.forName("committer"));
		assertEquals(SearchType.AUTHOR, SearchType.forName("author"));
		assertEquals(SearchType.COMMIT, SearchType.forName("unknown"));

		assertEquals("commit", SearchType.COMMIT.toString());
		assertEquals("committer", SearchType.COMMITTER.toString());
		assertEquals("author", SearchType.AUTHOR.toString());
	}

	@Test
	public void testSearchRevlogs() throws Exception {
		assertEquals(0, JGitUtils.searchRevlogs(null, null, "java", SearchType.COMMIT, 0, 0).size());
		List<RevCommit> results = JGitUtils.searchRevlogs(null, null, "java", SearchType.COMMIT, 0,
				3);
		assertEquals(0, results.size());

		// test commit message search
		Repository repository = GitBlitSuite.getHelloworldRepository();
		results = JGitUtils.searchRevlogs(repository, null, "java", SearchType.COMMIT, 0, 3);
		assertEquals(3, results.size());

		// test author search
		results = JGitUtils.searchRevlogs(repository, null, "timothy", SearchType.AUTHOR, 0, -1);
		assertEquals(1, results.size());

		// test committer search
		results = JGitUtils.searchRevlogs(repository, null, "mike", SearchType.COMMITTER, 0, 10);
		assertEquals(10, results.size());

		// test paging and offset
		RevCommit commit = JGitUtils.searchRevlogs(repository, null, "mike", SearchType.COMMITTER,
				9, 1).get(0);
		assertEquals(results.get(9), commit);

		repository.close();
	}

	@Test
	public void testZip() throws Exception {
		assertFalse(CompressionUtils.zip(null, null, null, null, null));
		Repository repository = GitBlitSuite.getHelloworldRepository();
		File zipFileA = new File(GitBlitSuite.REPOSITORIES, "helloworld.zip");
		FileOutputStream fosA = new FileOutputStream(zipFileA);
		boolean successA = CompressionUtils.zip(repository, null, null, Constants.HEAD, fosA);
		fosA.close();

		File zipFileB = new File(GitBlitSuite.REPOSITORIES, "helloworld-java.zip");
		FileOutputStream fosB = new FileOutputStream(zipFileB);
		boolean successB = CompressionUtils.zip(repository, null, "java.java", Constants.HEAD, fosB);
		fosB.close();

		repository.close();
		assertTrue("Failed to generate zip file!", successA);
		assertTrue(zipFileA.length() > 0);
		zipFileA.delete();

		assertTrue("Failed to generate zip file!", successB);
		assertTrue(zipFileB.length() > 0);
		zipFileB.delete();
	}

	@Test
	public void testPlots() throws Exception {
		Repository repository = GitBlitSuite.getTicgitRepository();
		PlotWalk pw = new PlotWalk(repository);
		PlotCommitList<PlotLane> commits = new PlotCommitList<PlotLane>();
		commits.source(pw);
		commits.fillTo(25);
		for (PlotCommit<PlotLane> commit : commits) {
			System.out.println(commit);
		}
		repository.close();
	}
}