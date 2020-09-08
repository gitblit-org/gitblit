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
package com.gitblit.servertests;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.MessageFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.jgit.util.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.models.RepositoryModel;

public class GitDaemonTest extends GitblitUnitTest {

	static File ticgitFolder = new File(GitBlitSuite.REPOSITORIES, "working/ticgit");

	static File ticgit2Folder = new File(GitBlitSuite.REPOSITORIES, "working/ticgit2");

	static File jgitFolder = new File(GitBlitSuite.REPOSITORIES, "working/jgit");

	static File jgit2Folder = new File(GitBlitSuite.REPOSITORIES, "working/jgit2");

	String url = GitBlitSuite.gitDaemonUrl;

	private static final AtomicBoolean started = new AtomicBoolean(false);

	@BeforeClass
	public static void startGitblit() throws Exception {
		started.set(GitBlitSuite.startGitblit());
	}

	@AfterClass
	public static void stopGitblit() throws Exception {
		if (started.get()) {
			GitBlitSuite.stopGitblit();
			deleteWorkingFolders();
		}
	}

	public static void deleteWorkingFolders() throws Exception {
		if (ticgitFolder.exists()) {
			GitBlitSuite.close(ticgitFolder);
			FileUtils.delete(ticgitFolder, FileUtils.RECURSIVE);
		}
		if (ticgit2Folder.exists()) {
			GitBlitSuite.close(ticgit2Folder);
			FileUtils.delete(ticgit2Folder, FileUtils.RECURSIVE);
		}
		if (jgitFolder.exists()) {
			GitBlitSuite.close(jgitFolder);
			FileUtils.delete(jgitFolder, FileUtils.RECURSIVE);
		}
		if (jgit2Folder.exists()) {
			GitBlitSuite.close(jgit2Folder);
			FileUtils.delete(jgit2Folder, FileUtils.RECURSIVE);
		}
	}

	@Test
	public void testAnonymousClone() throws Exception {
		GitBlitSuite.close(ticgitFolder);
		if (ticgitFolder.exists()) {
			FileUtils.delete(ticgitFolder, FileUtils.RECURSIVE | FileUtils.RETRY);
		}

		// set push restriction
		RepositoryModel model = repositories().getRepositoryModel("ticgit.git");
		model.accessRestriction = AccessRestrictionType.PUSH;
		model.authorizationControl = AuthorizationControl.NAMED;
		repositories().updateRepositoryModel(model.name, model, false);

		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/ticgit.git", url));
		clone.setDirectory(ticgitFolder);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		GitBlitSuite.close(clone.call());
		assertTrue(true);

		// restore anonymous repository access
		model.accessRestriction = AccessRestrictionType.NONE;
		model.authorizationControl = AuthorizationControl.NAMED;
		repositories().updateRepositoryModel(model.name, model, false);
	}

	@Test
	public void testCloneRestrictedRepo() throws Exception {
		GitBlitSuite.close(ticgit2Folder);
		if (ticgit2Folder.exists()) {
			FileUtils.delete(ticgit2Folder, FileUtils.RECURSIVE);
		}

		// restrict repository access
		RepositoryModel model = repositories().getRepositoryModel("ticgit.git");
		model.accessRestriction = AccessRestrictionType.CLONE;
		model.authorizationControl = AuthorizationControl.NAMED;
		repositories().updateRepositoryModel(model.name, model, false);

		// delete any existing working folder
		boolean cloned = false;
		try {
			CloneCommand clone = Git.cloneRepository();
			clone.setURI(MessageFormat.format("{0}/ticgit.git", url));
			clone.setDirectory(ticgit2Folder);
			clone.setBare(false);
			clone.setCloneAllBranches(true);
			GitBlitSuite.close(clone.call());
			cloned = true;
		} catch (Exception e) {
			// swallow the exception which we expect
		}

		assertFalse("Anonymous was able to clone the repository?!", cloned);

		FileUtils.delete(ticgit2Folder, FileUtils.RECURSIVE);

		// restore anonymous repository access
		model.accessRestriction = AccessRestrictionType.NONE;
		model.authorizationControl = AuthorizationControl.NAMED;
		repositories().updateRepositoryModel(model.name, model, false);
	}

	@Test
	public void testAnonymousPush() throws Exception {
		GitBlitSuite.close(ticgitFolder);
		if (ticgitFolder.exists()) {
			FileUtils.delete(ticgitFolder, FileUtils.RECURSIVE | FileUtils.RETRY);
		}

		// restore anonymous repository access
		RepositoryModel model = repositories().getRepositoryModel("ticgit.git");
		model.accessRestriction = AccessRestrictionType.NONE;
		model.authorizationControl = AuthorizationControl.NAMED;
		repositories().updateRepositoryModel(model.name, model, false);

		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/ticgit.git", url));
		clone.setDirectory(ticgitFolder);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		GitBlitSuite.close(clone.call());
		assertTrue(true);

		Git git = Git.open(ticgitFolder);
		File file = new File(ticgitFolder, "TODO");
		OutputStreamWriter os = new OutputStreamWriter(new FileOutputStream(file, true), Constants.CHARSET);
		BufferedWriter w = new BufferedWriter(os);
		w.write("// hellol中文 " + new Date().toString() + "\n");
		w.close();
		git.add().addFilepattern(file.getName()).call();
		git.commit().setMessage("test commit").call();
		Iterable<PushResult> results = git.push().setPushAll().call();
		GitBlitSuite.close(git);
		for (PushResult result : results) {
			for (RemoteRefUpdate update : result.getRemoteUpdates()) {
				assertEquals(Status.OK, update.getStatus());
			}
		}
	}

	@Test
	public void testPushRestrictedRepo() throws Exception {
		GitBlitSuite.close(ticgitFolder);
		if (ticgitFolder.exists()) {
			FileUtils.delete(ticgitFolder, FileUtils.RECURSIVE | FileUtils.RETRY);
		}

		// restore anonymous repository access
		RepositoryModel model = repositories().getRepositoryModel("ticgit.git");
		model.accessRestriction = AccessRestrictionType.PUSH;
		model.authorizationControl = AuthorizationControl.NAMED;
		repositories().updateRepositoryModel(model.name, model, false);

		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/ticgit.git", url));
		clone.setDirectory(ticgitFolder);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		GitBlitSuite.close(clone.call());
		assertTrue(true);

		Git git = Git.open(ticgitFolder);
		File file = new File(ticgitFolder, "TODO");
		OutputStreamWriter os = new OutputStreamWriter(new FileOutputStream(file, true), Constants.CHARSET);
		BufferedWriter w = new BufferedWriter(os);
		w.write("// hellol中文 " + new Date().toString() + "\n");
		w.close();
		git.add().addFilepattern(file.getName()).call();
		git.commit().setMessage("test commit").call();
		Iterable<PushResult> results = git.push().setPushAll().call();
		GitBlitSuite.close(git);
		for (PushResult result : results) {
			for (RemoteRefUpdate update : result.getRemoteUpdates()) {
				assertEquals(Status.REJECTED_OTHER_REASON, update.getStatus());
			}
		}
	}

	@Test
	public void testPushToFrozenRepo() throws Exception {
		GitBlitSuite.close(jgitFolder);
		if (jgitFolder.exists()) {
			FileUtils.delete(jgitFolder, FileUtils.RECURSIVE | FileUtils.RETRY);
		}

		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/test/jgit.git", url));
		clone.setDirectory(jgitFolder);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		GitBlitSuite.close(clone.call());
		assertTrue(true);

		// freeze repo
		RepositoryModel model = repositories().getRepositoryModel("test/jgit.git");
		model.isFrozen = true;
		repositories().updateRepositoryModel(model.name, model, false);

		Git git = Git.open(jgitFolder);
		File file = new File(jgitFolder, "TODO");
		OutputStreamWriter os = new OutputStreamWriter(new FileOutputStream(file, true), Constants.CHARSET);
		BufferedWriter w = new BufferedWriter(os);
		w.write("// " + new Date().toString() + "\n");
		w.close();
		git.add().addFilepattern(file.getName()).call();
		git.commit().setMessage("test commit").call();

		Iterable<PushResult> results = git.push().call();
		for (PushResult result : results) {
			for (RemoteRefUpdate update : result.getRemoteUpdates()) {
				assertEquals(Status.REJECTED_OTHER_REASON, update.getStatus());
			}
		}

		// unfreeze repo
		model.isFrozen = false;
		repositories().updateRepositoryModel(model.name, model, false);

		results = git.push().setPushAll().call();
		GitBlitSuite.close(git);
		for (PushResult result : results) {
			for (RemoteRefUpdate update : result.getRemoteUpdates()) {
				assertEquals(Status.OK, update.getStatus());
			}
		}
	}

	@Test
	public void testPushToNonBareRepository() throws Exception {
		GitBlitSuite.close(jgit2Folder);
		if (jgit2Folder.exists()) {
			FileUtils.delete(jgit2Folder, FileUtils.RECURSIVE | FileUtils.RETRY);
		}

		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/working/jgit", url));
		clone.setDirectory(jgit2Folder);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		GitBlitSuite.close(clone.call());
		assertTrue(true);

		Git git = Git.open(jgit2Folder);
		File file = new File(jgit2Folder, "NONBARE");
		OutputStreamWriter os = new OutputStreamWriter(new FileOutputStream(file, true), Constants.CHARSET);
		BufferedWriter w = new BufferedWriter(os);
		w.write("// " + new Date().toString() + "\n");
		w.close();
		git.add().addFilepattern(file.getName()).call();
		git.commit().setMessage("test commit followed by push to non-bare repository").call();

		Iterable<PushResult> results = git.push().setPushAll().call();
		GitBlitSuite.close(git);

		for (PushResult result : results) {
			for (RemoteRefUpdate update : result.getRemoteUpdates()) {
				assertEquals(Status.REJECTED_OTHER_REASON, update.getStatus());
			}
		}
	}

}
