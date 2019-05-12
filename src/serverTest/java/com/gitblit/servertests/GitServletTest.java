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
package com.gitblit.servertests;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.Keys;
import com.gitblit.models.RefLogEntry;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.RefLogUtils;

public class GitServletTest extends GitblitUnitTest {

	static File ticgitFolder = new File(GitBlitSuite.REPOSITORIES, "working/ticgit");

	static File ticgit2Folder = new File(GitBlitSuite.REPOSITORIES, "working/ticgit2");

	static File jgitFolder = new File(GitBlitSuite.REPOSITORIES, "working/jgit");

	static File jgit2Folder = new File(GitBlitSuite.REPOSITORIES, "working/jgit2");

	String url = GitBlitSuite.gitServletUrl;
	String account = GitBlitSuite.account;
	String password = GitBlitSuite.password;

	private static final AtomicBoolean started = new AtomicBoolean(false);

	private static UserModel getUser() {
		UserModel user = new UserModel("james");
		user.displayName = "James Moger";
		user.emailAddress = "james.moger@gmail.com";
		user.password = "james";
		return user;
	}

	private static void delete(UserModel user) {
		if (users().getUserModel(user.username) != null) {
			users().deleteUser(user.username);
		}
	}

	@BeforeClass
	public static void startGitblit() throws Exception {
		//"refchecks" folder is used in this test class;
		//need be deleted before Gitblit server instance is started
		GitBlitSuite.deleteRefChecksFolder();
		started.set(GitBlitSuite.startGitblit());

		delete(getUser());
	}

	@AfterClass
	public static void stopGitblit() throws Exception {
		if (started.get()) {
			GitBlitSuite.stopGitblit();
			deleteWorkingFolders();
		}

		delete(getUser());
	}

	public static void deleteWorkingFolders() throws Exception {
		if (ticgitFolder.exists()) {
			GitBlitSuite.close(ticgitFolder);
			FileUtils.delete(ticgitFolder, FileUtils.RECURSIVE | FileUtils.RETRY);
		}
		if (ticgit2Folder.exists()) {
			GitBlitSuite.close(ticgit2Folder);
			FileUtils.delete(ticgit2Folder, FileUtils.RECURSIVE | FileUtils.RETRY);
		}
		if (jgitFolder.exists()) {
			GitBlitSuite.close(jgitFolder);
			FileUtils.delete(jgitFolder, FileUtils.RECURSIVE | FileUtils.RETRY);
		}
		if (jgit2Folder.exists()) {
			GitBlitSuite.close(jgit2Folder);
			FileUtils.delete(jgit2Folder, FileUtils.RECURSIVE | FileUtils.RETRY);
		}
	}

	@Test
	public void testClone() throws Exception {
		GitBlitSuite.close(ticgitFolder);
		if (ticgitFolder.exists()) {
			FileUtils.delete(ticgitFolder, FileUtils.RECURSIVE | FileUtils.RETRY);
		}

		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/ticgit.git", url));
		clone.setDirectory(ticgitFolder);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider(account, password));
		GitBlitSuite.close(clone.call());
		assertTrue(true);
	}

	@Test
	public void testBogusLoginClone() throws Exception {
		// restrict repository access
		RepositoryModel model = repositories().getRepositoryModel("ticgit.git");
		model.accessRestriction = AccessRestrictionType.CLONE;
		repositories().updateRepositoryModel(model.name, model, false);

		// delete any existing working folder
		boolean cloned = false;
		try {
			CloneCommand clone = Git.cloneRepository();
			clone.setURI(MessageFormat.format("{0}/ticgit.git", url));
			clone.setDirectory(ticgit2Folder);
			clone.setBare(false);
			clone.setCloneAllBranches(true);
			clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider("bogus", "bogus"));
			GitBlitSuite.close(clone.call());
			cloned = true;
		} catch (Exception e) {
			// swallow the exception which we expect
		}

		// restore anonymous repository access
		model.accessRestriction = AccessRestrictionType.NONE;
		repositories().updateRepositoryModel(model.name, model, false);

		assertFalse("Bogus login cloned a repository?!", cloned);
	}

	@Test
	public void testUnauthorizedLoginClone() throws Exception {
		// restrict repository access
		RepositoryModel model = repositories().getRepositoryModel("ticgit.git");
		model.accessRestriction = AccessRestrictionType.CLONE;
		model.authorizationControl = AuthorizationControl.NAMED;
		UserModel user = new UserModel("james");
		user.password = "james";
		gitblit().addUser(user);
		repositories().updateRepositoryModel(model.name, model, false);

		FileUtils.delete(ticgit2Folder, FileUtils.RECURSIVE);

		// delete any existing working folder
		boolean cloned = false;
		try {
			CloneCommand clone = Git.cloneRepository();
			clone.setURI(MessageFormat.format("{0}/ticgit.git", url));
			clone.setDirectory(ticgit2Folder);
			clone.setBare(false);
			clone.setCloneAllBranches(true);
			clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider(user.username, user.password));
			GitBlitSuite.close(clone.call());
			cloned = true;
		} catch (Exception e) {
			// swallow the exception which we expect
		}

		assertFalse("Unauthorized login cloned a repository?!", cloned);

		FileUtils.delete(ticgit2Folder, FileUtils.RECURSIVE);

		// switch to authenticated
		model.authorizationControl = AuthorizationControl.AUTHENTICATED;
		repositories().updateRepositoryModel(model.name, model, false);

		// try clone again
		cloned = false;
		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/ticgit.git", url));
		clone.setDirectory(ticgit2Folder);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider(user.username, user.password));
		GitBlitSuite.close(clone.call());
		cloned = true;

		assertTrue("Authenticated login could not clone!", cloned);

		FileUtils.delete(ticgit2Folder, FileUtils.RECURSIVE);

		// restore anonymous repository access
		model.accessRestriction = AccessRestrictionType.NONE;
		model.authorizationControl = AuthorizationControl.NAMED;
		repositories().updateRepositoryModel(model.name, model, false);

		delete(user);
	}

	@Test
	public void testAnonymousPush() throws Exception {
		GitBlitSuite.close(ticgitFolder);
		if (ticgitFolder.exists()) {
			FileUtils.delete(ticgitFolder, FileUtils.RECURSIVE | FileUtils.RETRY);
		}

		RepositoryModel model = repositories().getRepositoryModel("ticgit.git");
		model.accessRestriction = AccessRestrictionType.NONE;
		repositories().updateRepositoryModel(model.name, model, false);

		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/ticgit.git", url));
		clone.setDirectory(ticgitFolder);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider(account, password));
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
	public void testSubfolderPush() throws Exception {
		GitBlitSuite.close(jgitFolder);
		if (jgitFolder.exists()) {
			FileUtils.delete(jgitFolder, FileUtils.RECURSIVE | FileUtils.RETRY);
		}

		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/test/jgit.git", url));
		clone.setDirectory(jgitFolder);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider(account, password));
		GitBlitSuite.close(clone.call());
		assertTrue(true);

		Git git = Git.open(jgitFolder);
		File file = new File(jgitFolder, "TODO");
		OutputStreamWriter os = new OutputStreamWriter(new FileOutputStream(file, true), Constants.CHARSET);
		BufferedWriter w = new BufferedWriter(os);
		w.write("// " + new Date().toString() + "\n");
		w.close();
		git.add().addFilepattern(file.getName()).call();
		git.commit().setMessage("test commit").call();
		Iterable<PushResult> results = git.push().setPushAll().setCredentialsProvider(new UsernamePasswordCredentialsProvider(account, password)).call();
		GitBlitSuite.close(git);
		for (PushResult result : results) {
			for (RemoteRefUpdate update : result.getRemoteUpdates()) {
				assertEquals(Status.OK, update.getStatus());
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
		clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider(account, password));
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

		Iterable<PushResult> results = git.push().setPushAll().setCredentialsProvider(new UsernamePasswordCredentialsProvider(account, password)).call();
		for (PushResult result : results) {
			for (RemoteRefUpdate update : result.getRemoteUpdates()) {
				assertEquals(Status.REJECTED_OTHER_REASON, update.getStatus());
			}
		}

		// unfreeze repo
		model.isFrozen = false;
		repositories().updateRepositoryModel(model.name, model, false);

		results = git.push().setPushAll().setCredentialsProvider(new UsernamePasswordCredentialsProvider(account, password)).call();
		GitBlitSuite.close(git);
		for (PushResult result : results) {
			for (RemoteRefUpdate update : result.getRemoteUpdates()) {
				assertEquals(Status.OK, update.getStatus());
			}
		}
	}

	@Test
	public void testPushToNonBareRepository() throws Exception {
		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/working/jgit", url));
		clone.setDirectory(jgit2Folder);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider(account, password));
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
		Iterable<PushResult> results = git.push().setPushAll().setCredentialsProvider(new UsernamePasswordCredentialsProvider(account, password)).call();
		GitBlitSuite.close(git);
		for (PushResult result : results) {
			for (RemoteRefUpdate update : result.getRemoteUpdates()) {
				assertEquals(Status.REJECTED_OTHER_REASON, update.getStatus());
			}
		}
	}

	@Test
	public void testCommitterVerification() throws Exception {
		UserModel user = getUser();

		testCommitterVerification(user, "joe", null, false);
		testCommitterVerification(user, "joe", user.emailAddress, false);
		testCommitterVerification(user, user.username, null, false);
		testCommitterVerification(user, user.username, user.emailAddress, true);

		user.displayName = "James Moger";
		testCommitterVerification(user, user.displayName, null, false);
		testCommitterVerification(user, user.displayName, "something", false);
		testCommitterVerification(user, user.displayName, user.emailAddress, true);
	}

	private void testCommitterVerification(UserModel user, String displayName, String emailAddress, boolean expectedSuccess) throws Exception {

		delete(user);

		CredentialsProvider cp = new UsernamePasswordCredentialsProvider(user.username, user.password);

		// fork from original to a temporary bare repo
		File verification = new File(GitBlitSuite.REPOSITORIES, "refchecks/verify-committer.git");
		if (verification.exists()) {
			FileUtils.delete(verification, FileUtils.RECURSIVE | FileUtils.RETRY);
		}
		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/ticgit.git", url));
		clone.setDirectory(verification);
		clone.setBare(true);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(cp);
		GitBlitSuite.close(clone.call());

		// require push permissions and committer verification
		RepositoryModel model = repositories().getRepositoryModel("refchecks/verify-committer.git");
		model.authorizationControl = AuthorizationControl.NAMED;
		model.accessRestriction = AccessRestrictionType.PUSH;
		model.verifyCommitter = true;

		// grant user push permission
		user.setRepositoryPermission(model.name, AccessPermission.PUSH);

		gitblit().addUser(user);
		repositories().updateRepositoryModel(model.name, model, false);

		// clone temp bare repo to working copy
		File local = new File(GitBlitSuite.REPOSITORIES, "refchecks/verify-wc");
		if (local.exists()) {
			FileUtils.delete(local, FileUtils.RECURSIVE);
		}
		clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/{1}", url, model.name));
		clone.setDirectory(local);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(cp);
		GitBlitSuite.close(clone.call());

		Git git = Git.open(local);

		// force an identity which may or may not match the account's identity
		git.getRepository().getConfig().setString("user", null, "name", displayName);
		git.getRepository().getConfig().setString("user", null, "email", emailAddress);
		git.getRepository().getConfig().save();

		// commit a file and push it
		File file = new File(local, "PUSHCHK");
		OutputStreamWriter os = new OutputStreamWriter(new FileOutputStream(file, true), Constants.CHARSET);
		BufferedWriter w = new BufferedWriter(os);
		w.write("// " + new Date().toString() + "\n");
		w.close();
		git.add().addFilepattern(file.getName()).call();
		git.commit().setMessage("push test").call();
		Iterable<PushResult> results = git.push().setCredentialsProvider(cp).setRemote("origin").call();

		for (PushResult result : results) {
			RemoteRefUpdate ref = result.getRemoteUpdate("refs/heads/master");
			Status status = ref.getStatus();
			if (expectedSuccess) {
				assertTrue("Verification failed! User was NOT able to push commit! " + status.name(), Status.OK.equals(status));
			} else {
				assertTrue("Verification failed! User was able to push commit! " + status.name(), Status.REJECTED_OTHER_REASON.equals(status));
			}
		}

		GitBlitSuite.close(git);
		// close serving repository
		GitBlitSuite.close(verification);
	}

	@Test
	public void testMergeCommitterVerification() throws Exception {

		testMergeCommitterVerification(false);

		testMergeCommitterVerification(true);
	}

	private void testMergeCommitterVerification(boolean expectedSuccess) throws Exception {
		UserModel user = getUser();

		delete(user);

		CredentialsProvider cp = new UsernamePasswordCredentialsProvider(user.username, user.password);

		// fork from original to a temporary bare repo
		File verification = new File(GitBlitSuite.REPOSITORIES, "refchecks/verify-committer.git");
		if (verification.exists()) {
			FileUtils.delete(verification, FileUtils.RECURSIVE | FileUtils.RETRY);
		}
		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/ticgit.git", url));
		clone.setDirectory(verification);
		clone.setBare(true);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(cp);
		GitBlitSuite.close(clone.call());

		// require push permissions and committer verification
		RepositoryModel model = repositories().getRepositoryModel("refchecks/verify-committer.git");
		model.authorizationControl = AuthorizationControl.NAMED;
		model.accessRestriction = AccessRestrictionType.PUSH;
		model.verifyCommitter = true;

		// grant user push permission
		user.setRepositoryPermission(model.name, AccessPermission.PUSH);

		gitblit().addUser(user);
		repositories().updateRepositoryModel(model.name, model, false);

		// clone temp bare repo to working copy
		File local = new File(GitBlitSuite.REPOSITORIES, "refchecks/verify-wc");
		if (local.exists()) {
			FileUtils.delete(local, FileUtils.RECURSIVE);
		}
		clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/{1}", url, model.name));
		clone.setDirectory(local);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(cp);
		GitBlitSuite.close(clone.call());

		Git git = Git.open(local);

		// checkout a mergetest branch
		git.checkout().setCreateBranch(true).setName("mergetest").call();

		// change identity
		git.getRepository().getConfig().setString("user", null, "name", "mergetest");
		git.getRepository().getConfig().setString("user", null, "email", "mergetest@merge.com");
		git.getRepository().getConfig().save();

		// commit a file
		File file = new File(local, "MERGECHK2");
		OutputStreamWriter os = new OutputStreamWriter(new FileOutputStream(file, true), Constants.CHARSET);
		BufferedWriter w = new BufferedWriter(os);
		w.write("// " + new Date().toString() + "\n");
		w.close();
		git.add().addFilepattern(file.getName()).call();
		RevCommit mergeTip = git.commit().setMessage(file.getName() + " test").call();

		// return to master
		git.checkout().setName("master").call();

		// restore identity
		if (expectedSuccess) {
			git.getRepository().getConfig().setString("user", null, "name", user.username);
			git.getRepository().getConfig().setString("user", null, "email", user.emailAddress);
			git.getRepository().getConfig().save();
		}

		// commit a file
		file = new File(local, "MERGECHK1");
		os = new OutputStreamWriter(new FileOutputStream(file, true), Constants.CHARSET);
		w = new BufferedWriter(os);
		w.write("// " + new Date().toString() + "\n");
		w.close();
		git.add().addFilepattern(file.getName()).call();
		git.commit().setMessage(file.getName() + " test").call();

		// merge the tip of the mergetest branch into master with --no-ff
		MergeResult mergeResult = git.merge().setFastForward(FastForwardMode.NO_FF).include(mergeTip.getId()).call();
		assertEquals(MergeResult.MergeStatus.MERGED, mergeResult.getMergeStatus());

		// push the merged master to the origin
		Iterable<PushResult> results = git.push().setCredentialsProvider(cp).setRemote("origin").call();

		for (PushResult result : results) {
			RemoteRefUpdate ref = result.getRemoteUpdate("refs/heads/master");
			Status status = ref.getStatus();
			if (expectedSuccess) {
				assertTrue("Verification failed! User was NOT able to push commit! " + status.name(), Status.OK.equals(status));
			} else {
				assertTrue("Verification failed! User was able to push commit! " + status.name(), Status.REJECTED_OTHER_REASON.equals(status));
			}
		}

		GitBlitSuite.close(git);
		// close serving repository
		GitBlitSuite.close(verification);
	}

	@Test
	public void testBlockClone() throws Exception {
		testRefChange(AccessPermission.VIEW, null, null, null);
	}

	@Test
	public void testBlockPush() throws Exception {
		testRefChange(AccessPermission.CLONE, null, null, null);
	}

	@Test
	public void testBlockBranchCreation() throws Exception {
		testRefChange(AccessPermission.PUSH, Status.REJECTED_OTHER_REASON, null, null);
	}

	@Test
	public void testBlockBranchDeletion() throws Exception {
		testRefChange(AccessPermission.CREATE, Status.OK, Status.REJECTED_OTHER_REASON, null);
	}

	@Test
	public void testBlockBranchRewind() throws Exception {
		testRefChange(AccessPermission.DELETE, Status.OK, Status.OK, Status.REJECTED_OTHER_REASON);
	}

	@Test
	public void testBranchRewind() throws Exception {
		testRefChange(AccessPermission.REWIND, Status.OK, Status.OK, Status.OK);
	}

	private void testRefChange(AccessPermission permission, Status expectedCreate, Status expectedDelete, Status expectedRewind) throws Exception {

		final String originName = "ticgit.git";
		final String forkName = "refchecks/ticgit.git";
		final String workingCopy = "refchecks/ticgit-wc";


		// lower access restriction on origin repository
		RepositoryModel origin = repositories().getRepositoryModel(originName);
		origin.accessRestriction = AccessRestrictionType.NONE;
		repositories().updateRepositoryModel(origin.name, origin, false);

		UserModel user = getUser();
		delete(user);

		CredentialsProvider cp = new UsernamePasswordCredentialsProvider(user.username, user.password);

		// fork from original to a temporary bare repo
		File refChecks = new File(GitBlitSuite.REPOSITORIES, forkName);
		if (refChecks.exists()) {
			FileUtils.delete(refChecks, FileUtils.RECURSIVE | FileUtils.RETRY);
		}
		CloneCommand clone = Git.cloneRepository();
		clone.setURI(url + "/" + originName);
		clone.setDirectory(refChecks);
		clone.setBare(true);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(cp);
		GitBlitSuite.close(clone.call());

		// elevate repository to clone permission
		RepositoryModel model = repositories().getRepositoryModel(forkName);
		switch (permission) {
			case VIEW:
				model.accessRestriction = AccessRestrictionType.CLONE;
				break;
			case CLONE:
				model.accessRestriction = AccessRestrictionType.CLONE;
				break;
			default:
				model.accessRestriction = AccessRestrictionType.PUSH;
		}
		model.authorizationControl = AuthorizationControl.NAMED;

		// grant user specified
		user.setRepositoryPermission(model.name, permission);

		gitblit().addUser(user);
		repositories().updateRepositoryModel(model.name, model, false);

		// clone temp bare repo to working copy
		File local = new File(GitBlitSuite.REPOSITORIES, workingCopy);
		if (local.exists()) {
			FileUtils.delete(local, FileUtils.RECURSIVE | FileUtils.RETRY);
		}
		clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/{1}", url, model.name));
		clone.setDirectory(local);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(cp);

		try {
			GitBlitSuite.close(clone.call());
		} catch (GitAPIException e) {
			if (permission.atLeast(AccessPermission.CLONE)) {
				throw e;
			} else {
				// close serving repository
				GitBlitSuite.close(refChecks);

				// user does not have clone permission
				assertTrue(e.getMessage(), e.getMessage().contains("not permitted"));
				return;
			}
		}

		Git git = Git.open(local);

		// commit a file and push it
		File file = new File(local, "PUSHCHK");
		OutputStreamWriter os = new OutputStreamWriter(new FileOutputStream(file, true), Constants.CHARSET);
		BufferedWriter w = new BufferedWriter(os);
		w.write("// " + new Date().toString() + "\n");
		w.close();
		git.add().addFilepattern(file.getName()).call();
		git.commit().setMessage("push test").call();
		Iterable<PushResult> results = null;
		try {
			results = git.push().setCredentialsProvider(cp).setRemote("origin").call();
		} catch (GitAPIException e) {
			if (permission.atLeast(AccessPermission.PUSH)) {
				throw e;
			} else {
				// close serving repository
				GitBlitSuite.close(refChecks);

				// user does not have push permission
				assertTrue(e.getMessage(), e.getMessage().contains("not permitted"));
				GitBlitSuite.close(git);
				return;
			}
		}

		for (PushResult result : results) {
			RemoteRefUpdate ref = result.getRemoteUpdate("refs/heads/master");
			Status status = ref.getStatus();
			if (permission.atLeast(AccessPermission.PUSH)) {
				assertTrue("User failed to push commit?! " + status.name(), Status.OK.equals(status));
			} else {
				// close serving repository
				GitBlitSuite.close(refChecks);

				assertTrue("User was able to push commit! " + status.name(), Status.REJECTED_OTHER_REASON.equals(status));
				GitBlitSuite.close(git);
				// skip delete test
				return;
			}
		}

		// create a local branch and push the new branch back to the origin
		git.branchCreate().setName("protectme").call();
		RefSpec refSpec = new RefSpec("refs/heads/protectme:refs/heads/protectme");
		results = git.push().setCredentialsProvider(cp).setRefSpecs(refSpec).setRemote("origin").call();
		for (PushResult result : results) {
			RemoteRefUpdate ref = result.getRemoteUpdate("refs/heads/protectme");
			Status status = ref.getStatus();
			if (Status.OK.equals(expectedCreate)) {
				assertTrue("User failed to push creation?! " + status.name(), status.equals(expectedCreate));
			} else {
				// close serving repository
				GitBlitSuite.close(refChecks);

				assertTrue("User was able to push ref creation! " + status.name(), status.equals(expectedCreate));
				GitBlitSuite.close(git);
				// skip delete test
				return;
			}
		}

		// delete the branch locally
		git.branchDelete().setBranchNames("protectme").call();

		// push a delete ref command
		refSpec = new RefSpec(":refs/heads/protectme");
		results = git.push().setCredentialsProvider(cp).setRefSpecs(refSpec).setRemote("origin").call();
		for (PushResult result : results) {
			RemoteRefUpdate ref = result.getRemoteUpdate("refs/heads/protectme");
			Status status = ref.getStatus();
			if (Status.OK.equals(expectedDelete)) {
				assertTrue("User failed to push ref deletion?! " + status.name(), status.equals(Status.OK));
			} else {
				// close serving repository
				GitBlitSuite.close(refChecks);

				assertTrue("User was able to push ref deletion?! " + status.name(), status.equals(expectedDelete));
				GitBlitSuite.close(git);
				// skip rewind test
				return;
			}
		}

		// rewind master by two commits
		git.reset().setRef("HEAD~2").setMode(ResetType.HARD).call();

		// commit a change on this detached HEAD
		file = new File(local, "REWINDCHK");
		os = new OutputStreamWriter(new FileOutputStream(file, true), Constants.CHARSET);
		w = new BufferedWriter(os);
		w.write("// " + new Date().toString() + "\n");
		w.close();
		git.add().addFilepattern(file.getName()).call();
		RevCommit commit = git.commit().setMessage("rewind master and new commit").call();

		// Reset master to our new commit now we our local branch tip is no longer
		// upstream of the remote branch tip.  It is an alternate tip of the branch.
		JGitUtils.setBranchRef(git.getRepository(), "refs/heads/master", commit.getName());

		// Try pushing our new tip to the origin.
		// This requires the server to "rewind" it's master branch and update it
		// to point to our alternate tip.  This leaves the original master tip
		// unreferenced.
		results = git.push().setCredentialsProvider(cp).setRemote("origin").setForce(true).call();
		for (PushResult result : results) {
			RemoteRefUpdate ref = result.getRemoteUpdate("refs/heads/master");
			Status status = ref.getStatus();
			if (Status.OK.equals(expectedRewind)) {
				assertTrue("User failed to rewind master?! " + status.name(), status.equals(expectedRewind));
			} else {
				assertTrue("User was able to rewind master?! " + status.name(), status.equals(expectedRewind));
			}
		}
		GitBlitSuite.close(git);

		// close serving repository
		GitBlitSuite.close(refChecks);

		delete(user);
	}

	@Test
	public void testCreateOnPush() throws Exception {
		testCreateOnPush(false, false);
		testCreateOnPush(true, false);
		testCreateOnPush(false, true);
	}

	private void testCreateOnPush(boolean canCreate, boolean canAdmin) throws Exception {

		UserModel user = new UserModel("sampleuser");
		user.password = user.username;

		delete(user);

		user.canCreate = canCreate;
		user.canAdmin = canAdmin;

		gitblit().addUser(user);

		CredentialsProvider cp = new UsernamePasswordCredentialsProvider(user.username, user.password);

		// fork from original to a temporary bare repo
		File tmpFolder = File.createTempFile("gitblit", "").getParentFile();
		File createCheck = new File(tmpFolder, "ticgit.git");
		if (createCheck.exists()) {
			FileUtils.delete(createCheck, FileUtils.RECURSIVE);
		}

		File personalRepo = new File(GitBlitSuite.REPOSITORIES, MessageFormat.format("~{0}/ticgit.git", user.username));
		GitBlitSuite.close(personalRepo);
		if (personalRepo.exists()) {
			FileUtils.delete(personalRepo, FileUtils.RECURSIVE);
		}

		File projectRepo = new File(GitBlitSuite.REPOSITORIES, "project/ticgit.git");
		GitBlitSuite.close(projectRepo);
		if (projectRepo.exists()) {
			FileUtils.delete(projectRepo, FileUtils.RECURSIVE);
		}

		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/ticgit.git", url));
		clone.setDirectory(createCheck);
		clone.setBare(true);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(cp);
		Git git = clone.call();

		GitBlitSuite.close(personalRepo);

		// add a personal repository remote and a project remote
		git.getRepository().getConfig().setString("remote", "user", "url", MessageFormat.format("{0}/~{1}/ticgit.git", url, user.username));
		git.getRepository().getConfig().setString("remote", "project", "url", MessageFormat.format("{0}/project/ticgit.git", url));
		git.getRepository().getConfig().save();

		// push to non-existent user repository
		try {
			Iterable<PushResult> results = git.push().setRemote("user").setPushAll().setCredentialsProvider(cp).call();

			for (PushResult result : results) {
				RemoteRefUpdate ref = result.getRemoteUpdate("refs/heads/master");
				Status status = ref.getStatus();
				assertTrue("User failed to create repository?! " + status.name(), Status.OK.equals(status));
			}

			assertTrue("User canAdmin:" + user.canAdmin + " canCreate:" + user.canCreate, user.canAdmin || user.canCreate);

			// confirm default personal repository permissions
			RepositoryModel model = repositories().getRepositoryModel(MessageFormat.format("~{0}/ticgit.git", user.username));
			assertEquals("Unexpected owner", user.username, ArrayUtils.toString(model.owners));
			assertEquals("Unexpected authorization control", AuthorizationControl.NAMED, model.authorizationControl);
			assertEquals("Unexpected access restriction", AccessRestrictionType.VIEW, model.accessRestriction);

		} catch (GitAPIException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("git-receive-pack not found"));
			assertFalse("User canAdmin:" + user.canAdmin + " canCreate:" + user.canCreate, user.canAdmin || user.canCreate);
		}

		// push to non-existent project repository
		try {
			Iterable<PushResult> results = git.push().setRemote("project").setPushAll().setCredentialsProvider(cp).call();
			GitBlitSuite.close(git);

			for (PushResult result : results) {
				RemoteRefUpdate ref = result.getRemoteUpdate("refs/heads/master");
				Status status = ref.getStatus();
				assertTrue("User failed to create repository?! " + status.name(), Status.OK.equals(status));
			}

			assertTrue("User canAdmin:" + user.canAdmin, user.canAdmin);

			// confirm default project repository permissions
			RepositoryModel model = repositories().getRepositoryModel("project/ticgit.git");
			assertEquals("Unexpected owner", user.username, ArrayUtils.toString(model.owners));
			assertEquals("Unexpected authorization control", AuthorizationControl.fromName(settings().getString(Keys.git.defaultAuthorizationControl, "NAMED")), model.authorizationControl);
			assertEquals("Unexpected access restriction", AccessRestrictionType.fromName(settings().getString(Keys.git.defaultAccessRestriction, "NONE")), model.accessRestriction);

		} catch (GitAPIException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("git-receive-pack not found"));
			assertFalse("User canAdmin:" + user.canAdmin, user.canAdmin);
		}

		GitBlitSuite.close(git);
		delete(user);
	}

	@Test
	public void testPushLog() throws IOException {
		String name = "refchecks/ticgit.git";
		File refChecks = new File(GitBlitSuite.REPOSITORIES, name);
		Repository repository = new FileRepositoryBuilder().setGitDir(refChecks).build();
		List<RefLogEntry> pushes = RefLogUtils.getRefLog(name, repository);
		GitBlitSuite.close(repository);
		assertTrue("Repository has an empty push log!", pushes.size() > 0);
	}
}
