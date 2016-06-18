/*
 * Copyright 2016 gitblit.com.
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.jgit.util.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.GitBlitException;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.UserModel;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.TicketModel.Field;
import com.gitblit.models.TicketModel.Reference;
import com.gitblit.tickets.ITicketService;

/**
 * Creates and deletes a range of ticket references via ticket comments and commits 
 */
public class TicketReferenceTest extends GitblitUnitTest {

	static File workingCopy = new File(GitBlitSuite.REPOSITORIES, "working/TicketReferenceTest.git-wc");
	
	static ITicketService ticketService;
	
	static final String account = "TicketRefTest";
	static final String password = GitBlitSuite.password;

	static final String url = GitBlitSuite.gitServletUrl;
	
	static UserModel user = null;
	static RepositoryModel repo = null;
	static CredentialsProvider cp = null;
	static Git git = null;
	
	@BeforeClass
	public static void configure() throws Exception {
		File repositoryName = new File("TicketReferenceTest.git");;
		
		GitBlitSuite.close(repositoryName);
		if (repositoryName.exists()) {
			FileUtils.delete(repositoryName, FileUtils.RECURSIVE | FileUtils.RETRY);
		}
		repo = new RepositoryModel("TicketReferenceTest.git", null, null, null);
		
		if (gitblit().hasRepository(repo.name)) {
			gitblit().deleteRepositoryModel(repo);
		}
		
		gitblit().updateRepositoryModel(repo.name, repo, true);
		
		user = new UserModel(account);
		user.displayName = account;
		user.emailAddress = account + "@example.com";
		user.password = password;

		cp = new UsernamePasswordCredentialsProvider(user.username, user.password);
		
		if (gitblit().getUserModel(user.username) != null) {
			gitblit().deleteUser(user.username);
		}

		repo.authorizationControl = AuthorizationControl.NAMED;
		repo.accessRestriction = AccessRestrictionType.PUSH;
		gitblit().updateRepositoryModel(repo.name, repo, false);
		
		// grant user push permission
		user.setRepositoryPermission(repo.name, AccessPermission.REWIND);
		gitblit().updateUserModel(user);

		ticketService = gitblit().getTicketService();
		assertTrue(ticketService.deleteAll(repo));
		
		GitBlitSuite.close(workingCopy);
		if (workingCopy.exists()) {
			FileUtils.delete(workingCopy, FileUtils.RECURSIVE | FileUtils.RETRY);
		}
		
		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/{1}", url, repo.name));
		clone.setDirectory(workingCopy);
		clone.setBare(false);
		clone.setBranch("master");
		clone.setCredentialsProvider(cp);
		GitBlitSuite.close(clone.call());
		
		git = Git.open(workingCopy);
		git.getRepository().getConfig().setString("user", null, "name", user.displayName);
		git.getRepository().getConfig().setString("user", null, "email", user.emailAddress);
		git.getRepository().getConfig().save();
		
		final RevCommit revCommit1 = makeCommit("initial commit");
		final String initialSha = revCommit1.name();
		Iterable<PushResult> results = git.push().setPushAll().setCredentialsProvider(cp).call();
		GitBlitSuite.close(git);
		for (PushResult result : results) {
			for (RemoteRefUpdate update : result.getRemoteUpdates()) {
				assertEquals(Status.OK, update.getStatus());
				assertEquals(initialSha, update.getNewObjectId().name());
			}
		}
	}
	
	@AfterClass
	public static void cleanup() throws Exception {
		GitBlitSuite.close(git);
	}
	
	@Test
	public void noReferencesOnTicketCreation() throws Exception {

		TicketModel a = ticketService.createTicket(repo, newTicket("noReferencesOnCreation"));
		assertNotNull(a);
		assertFalse(a.hasReferences());
		
		//Ensure retrieval process doesn't affect anything
		a = ticketService.getTicket(repo,  a.number);
		assertNotNull(a);
		assertFalse(a.hasReferences());
	}
	
	
	@Test
	public void commentNoUnexpectedReference() throws Exception {
		
		TicketModel a = ticketService.createTicket(repo, newTicket("commentNoUnexpectedReference-A"));
		TicketModel b = ticketService.createTicket(repo, newTicket("commentNoUnexpectedReference-B"));
		
		assertNotNull(ticketService.updateTicket(repo, a.number, newComment("comment for 1 - no reference")));
		assertNotNull(ticketService.updateTicket(repo, a.number, newComment("comment for # - no reference")));
		assertNotNull(ticketService.updateTicket(repo, a.number, newComment("comment for #999 - ignores invalid reference")));

		a = ticketService.getTicket(repo, a.number);
		b = ticketService.getTicket(repo, b.number);

		assertFalse(a.hasReferences());
		assertFalse(b.hasReferences());
	}
	
	@Test
	public void commentNoSelfReference() throws Exception {
			
		TicketModel a = ticketService.createTicket(repo, newTicket("commentNoSelfReference-A"));
		
		final Change comment = newComment(String.format("comment for #%d - no self reference", a.number));
		assertNotNull(ticketService.updateTicket(repo, a.number, comment)); 
		
		a = ticketService.getTicket(repo, a.number);
		
		assertFalse(a.hasReferences());
	}
	
	@Test
	public void commentSingleReference() throws Exception {
		
		TicketModel a = ticketService.createTicket(repo, newTicket("commentSingleReference-A"));
		TicketModel b = ticketService.createTicket(repo, newTicket("commentSingleReference-B"));
		
		final Change comment = newComment(String.format("comment for #%d - single reference", b.number));
		assertNotNull(ticketService.updateTicket(repo, a.number, comment));
		
		a = ticketService.getTicket(repo, a.number);
		b = ticketService.getTicket(repo, b.number);
		
		assertFalse(a.hasReferences());
		assertTrue(b.hasReferences());
		
		List<Reference> cRefB = b.getReferences();
		assertNotNull(cRefB);
		assertEquals(1, cRefB.size());
		assertEquals(a.number, cRefB.get(0).ticketId.longValue());
		assertEquals(comment.comment.id, cRefB.get(0).hash);
	}
		
	@Test
	public void commentSelfAndOtherReference() throws Exception {
		TicketModel a = ticketService.createTicket(repo, newTicket("commentSelfAndOtherReference-A"));
		TicketModel b = ticketService.createTicket(repo, newTicket("commentSelfAndOtherReference-B"));
		
		final Change comment = newComment(String.format("comment for #%d and #%d - self and other reference", a.number, b.number));
		assertNotNull(ticketService.updateTicket(repo, a.number, comment));
		
		a = ticketService.getTicket(repo, a.number);
		b = ticketService.getTicket(repo, b.number);

		assertFalse(a.hasReferences());
		assertTrue(b.hasReferences());
		
		List<Reference> cRefB = b.getReferences();
		assertNotNull(cRefB);
		assertEquals(1, cRefB.size());
		assertEquals(a.number, cRefB.get(0).ticketId.longValue());
		assertEquals(comment.comment.id, cRefB.get(0).hash);
	}
	
	@Test
	public void commentMultiReference() throws Exception {
		TicketModel a = ticketService.createTicket(repo, newTicket("commentMultiReference-A"));
		TicketModel b = ticketService.createTicket(repo, newTicket("commentMultiReference-B"));
		TicketModel c = ticketService.createTicket(repo, newTicket("commentMultiReference-C"));
		
		final Change comment = newComment(String.format("comment for #%d and #%d - multi reference", b.number, c.number));
		assertNotNull(ticketService.updateTicket(repo, a.number, comment));
		
		a = ticketService.getTicket(repo, a.number);
		b = ticketService.getTicket(repo, b.number);
		c = ticketService.getTicket(repo, c.number);

		assertFalse(a.hasReferences());
		assertTrue(b.hasReferences());
		assertTrue(c.hasReferences());
		
		List<Reference> cRefB = b.getReferences();
		assertNotNull(cRefB);
		assertEquals(1, cRefB.size());
		assertEquals(a.number, cRefB.get(0).ticketId.longValue());
		assertEquals(comment.comment.id, cRefB.get(0).hash);
		
		List<Reference> cRefC = c.getReferences();
		assertNotNull(cRefC);
		assertEquals(1, cRefC.size());
		assertEquals(a.number, cRefC.get(0).ticketId.longValue());
		assertEquals(comment.comment.id, cRefC.get(0).hash);
	}
	
	

	@Test
	public void commitMasterNoUnexpectedReference() throws Exception {
		TicketModel a = ticketService.createTicket(repo, newTicket("commentMultiReference-A"));
		
		final String branchName = "master";
		git.checkout().setCreateBranch(false).setName(branchName).call();
		
		makeCommit("commit for 1 - no reference");
		makeCommit("comment for # - no reference");
		final RevCommit revCommit1 = makeCommit("comment for #999 - ignores invalid reference");
		final String commit1Sha = revCommit1.name();
		
		assertPushSuccess(commit1Sha, branchName);
		
		a = ticketService.getTicket(repo, a.number);
		assertFalse(a.hasReferences());
	}
	
	@Test
	public void commitMasterSingleReference() throws Exception {
		TicketModel a = ticketService.createTicket(repo, newTicket("commitMasterSingleReference-A"));
		
		final String branchName = "master";
		git.checkout().setCreateBranch(false).setName(branchName).call();
		
		final String message = String.format("commit for #%d - single reference", a.number);
		final RevCommit revCommit1 = makeCommit(message);
		final String commit1Sha = revCommit1.name();
		
		assertPushSuccess(commit1Sha, branchName);
		
		a = ticketService.getTicket(repo, a.number);
		assertTrue(a.hasReferences());
		
		List<Reference> cRefA = a.getReferences();
		assertNotNull(cRefA);
		assertEquals(1, cRefA.size());
		assertNull(cRefA.get(0).ticketId);
		assertEquals(commit1Sha, cRefA.get(0).hash);
	}
	
	@Test
	public void commitMasterMultiReference() throws Exception {
		TicketModel a = ticketService.createTicket(repo, newTicket("commitMasterMultiReference-A"));
		TicketModel b = ticketService.createTicket(repo, newTicket("commitMasterMultiReference-B"));
		
		final String branchName = "master";
		git.checkout().setCreateBranch(false).setName(branchName).call();
		
		final String message = String.format("commit for #%d and #%d - multi reference", a.number, b.number);
		final RevCommit revCommit1 = makeCommit(message);
		final String commit1Sha = revCommit1.name();
		
		assertPushSuccess(commit1Sha, branchName);
		
		a = ticketService.getTicket(repo, a.number);
		b = ticketService.getTicket(repo, b.number);
		assertTrue(a.hasReferences());
		assertTrue(b.hasReferences());
		
		List<Reference> cRefA = a.getReferences();
		assertNotNull(cRefA);
		assertEquals(1, cRefA.size());
		assertNull(cRefA.get(0).ticketId);
		assertEquals(commit1Sha, cRefA.get(0).hash);
		
		List<Reference> cRefB = a.getReferences();
		assertNotNull(cRefB);
		assertEquals(1, cRefB.size());
		assertNull(cRefB.get(0).ticketId);
		assertEquals(commit1Sha, cRefB.get(0).hash);
	}
	
	@Test
	public void commitMasterAmendReference() throws Exception {
		TicketModel a = ticketService.createTicket(repo, newTicket("commitMasterAmendReference-A"));
		TicketModel b = ticketService.createTicket(repo, newTicket("commitMasterAmendReference-B"));
		
		final String branchName = "master";
		git.checkout().setCreateBranch(false).setName(branchName).call();
		
		String message = String.format("commit before amend for #%d and #%d", a.number, b.number);
		final RevCommit revCommit1 = makeCommit(message);
		final String commit1Sha = revCommit1.name();
		assertPushSuccess(commit1Sha, branchName);
		
		a = ticketService.getTicket(repo, a.number);
		b = ticketService.getTicket(repo, b.number);
		assertTrue(a.hasReferences());
		assertTrue(b.hasReferences());
		
		List<Reference> cRefA = a.getReferences();
		assertNotNull(cRefA);
		assertEquals(1, cRefA.size());
		assertNull(cRefA.get(0).ticketId);
		assertEquals(commit1Sha, cRefA.get(0).hash);
		
		List<Reference> cRefB = b.getReferences();
		assertNotNull(cRefB);
		assertEquals(1, cRefB.size());
		assertNull(cRefB.get(0).ticketId);
		assertEquals(commit1Sha, cRefB.get(0).hash);
		
		//Confirm that old invalid references removed for both tickets
		//and new reference added for one referenced ticket
		message = String.format("commit after amend for #%d", a.number);
		final String commit2Sha = amendCommit(message);
		
		assertForcePushSuccess(commit2Sha, branchName);
		
		a = ticketService.getTicket(repo, a.number);
		b = ticketService.getTicket(repo, b.number);
		assertTrue(a.hasReferences());
		assertFalse(b.hasReferences());
		
		cRefA = a.getReferences();
		assertNotNull(cRefA);
		assertEquals(1, cRefA.size());
		assertNull(cRefA.get(0).ticketId);
		assertEquals(commit2Sha, cRefA.get(0).hash);
	}
	
	
	@Test
	public void commitPatchsetNoUnexpectedReference() throws Exception {
		setPatchsetAvailable(true);
		TicketModel a = ticketService.createTicket(repo, newTicket("commitPatchsetNoUnexpectedReference-A"));
		
		String branchName = String.format("ticket/%d", a.number);
		git.checkout().setCreateBranch(true).setName(branchName).call();
		
		makeCommit("commit for 1 - no reference");
		makeCommit("commit for # - no reference");
		final String message = "commit for #999 - ignores invalid reference";
		final RevCommit revCommit1 = makeCommit(message);
		final String commit1Sha = revCommit1.name();
		
		assertPushSuccess(commit1Sha, branchName);
		
		a = ticketService.getTicket(repo, a.number);
		assertFalse(a.hasReferences());
	}

	@Test
	public void commitPatchsetNoSelfReference() throws Exception {
		setPatchsetAvailable(true);
		TicketModel a = ticketService.createTicket(repo, newTicket("commitPatchsetNoSelfReference-A"));
		
		String branchName = String.format("ticket/%d", a.number);
		git.checkout().setCreateBranch(true).setName(branchName).call();
		
		final String message = String.format("commit for #%d - patchset self reference", a.number);
		final RevCommit revCommit1 = makeCommit(message);
		final String commit1Sha = revCommit1.name();
		
		assertPushSuccess(commit1Sha, branchName);
		
		a = ticketService.getTicket(repo, a.number);
		assertFalse(a.hasReferences());
	}
	
	@Test
	public void commitPatchsetSingleReference() throws Exception {
		setPatchsetAvailable(true);
		TicketModel a = ticketService.createTicket(repo, newTicket("commitPatchsetSingleReference-A"));
		TicketModel b = ticketService.createTicket(repo, newTicket("commitPatchsetSingleReference-B"));
		
		String branchName = String.format("ticket/%d", a.number);
		git.checkout().setCreateBranch(true).setName(branchName).call();
		
		final String message = String.format("commit for #%d - patchset single reference", b.number);
		final RevCommit revCommit1 = makeCommit(message);
		final String commit1Sha = revCommit1.name();
		
		assertPushSuccess(commit1Sha, branchName);
		
		a = ticketService.getTicket(repo, a.number);
		b = ticketService.getTicket(repo, b.number);
		assertFalse(a.hasReferences());
		assertTrue(b.hasReferences());
		
		List<Reference> cRefB = b.getReferences();
		assertNotNull(cRefB);
		assertEquals(1, cRefB.size());
		assertNull(cRefB.get(0).ticketId);
		assertEquals(commit1Sha, cRefB.get(0).hash);
	}
	
	@Test
	public void commitPatchsetMultiReference() throws Exception {
		setPatchsetAvailable(true);
		TicketModel a = ticketService.createTicket(repo, newTicket("commitPatchsetMultiReference-A"));
		TicketModel b = ticketService.createTicket(repo, newTicket("commitPatchsetMultiReference-B"));
		TicketModel c = ticketService.createTicket(repo, newTicket("commitPatchsetMultiReference-C"));
		
		String branchName = String.format("ticket/%d", a.number);
		git.checkout().setCreateBranch(true).setName(branchName).call();
		
		final String message = String.format("commit for #%d and #%d- patchset multi reference", b.number, c.number);
		final RevCommit revCommit1 = makeCommit(message);
		final String commit1Sha = revCommit1.name();
		
		assertPushSuccess(commit1Sha, branchName);
		
		a = ticketService.getTicket(repo, a.number);
		b = ticketService.getTicket(repo, b.number);
		c = ticketService.getTicket(repo, c.number);
		assertFalse(a.hasReferences());
		assertTrue(b.hasReferences());
		assertTrue(c.hasReferences());
		
		List<Reference> cRefB = b.getReferences();
		assertNotNull(cRefB);
		assertEquals(1, cRefB.size());
		assertNull(cRefB.get(0).ticketId);
		assertEquals(commit1Sha, cRefB.get(0).hash);
		
		List<Reference> cRefC = c.getReferences();
		assertNotNull(cRefC);
		assertEquals(1, cRefC.size());
		assertNull(cRefC.get(0).ticketId);
		assertEquals(commit1Sha, cRefC.get(0).hash);
	}
	
	@Test
	public void commitPatchsetAmendReference() throws Exception {
		setPatchsetAvailable(true);

		TicketModel a = ticketService.createTicket(repo, newTicket("commitPatchsetAmendReference-A"));
		TicketModel b = ticketService.createTicket(repo, newTicket("commitPatchsetAmendReference-B"));
		TicketModel c = ticketService.createTicket(repo, newTicket("commitPatchsetAmendReference-C"));
		assertFalse(c.hasPatchsets());
		
		String branchName = String.format("ticket/%d", c.number);
		git.checkout().setCreateBranch(true).setName(branchName).call();
		
		String message = String.format("commit before amend for #%d and #%d", a.number, b.number);
		final RevCommit revCommit1 = makeCommit(message);
		final String commit1Sha = revCommit1.name();
		assertPushSuccess(commit1Sha, branchName);
		
		a = ticketService.getTicket(repo, a.number);
		b = ticketService.getTicket(repo, b.number);
		c = ticketService.getTicket(repo, c.number);
		assertTrue(a.hasReferences());
		assertTrue(b.hasReferences());
		assertFalse(c.hasReferences());
		
		assertTrue(c.hasPatchsets());
		assertNotNull(c.getPatchset(1, 1));
		
		List<Reference> cRefA = a.getReferences();
		assertNotNull(cRefA);
		assertEquals(1, cRefA.size());
		assertNull(cRefA.get(0).ticketId);
		assertEquals(commit1Sha, cRefA.get(0).hash);
		
		List<Reference> cRefB = b.getReferences();
		assertNotNull(cRefB);
		assertEquals(1, cRefB.size());
		assertNull(cRefB.get(0).ticketId);
		assertEquals(commit1Sha, cRefB.get(0).hash);
		
		//As a new patchset is created the references will remain until deleted
		message = String.format("commit after amend for #%d", a.number);
		final String commit2Sha = amendCommit(message);

		assertForcePushSuccess(commit2Sha, branchName);
		
		a = ticketService.getTicket(repo, a.number);
		b = ticketService.getTicket(repo, b.number);
		c = ticketService.getTicket(repo, c.number);
		assertTrue(a.hasReferences());
		assertTrue(b.hasReferences());
		assertFalse(c.hasReferences());
		
		assertNotNull(c.getPatchset(1, 1));
		assertNotNull(c.getPatchset(2, 1));
		
		cRefA = a.getReferences();
		assertNotNull(cRefA);
		assertEquals(2, cRefA.size());
		assertNull(cRefA.get(0).ticketId);
		assertNull(cRefA.get(1).ticketId);
		assertEquals(commit1Sha, cRefA.get(0).hash);
		assertEquals(commit2Sha, cRefA.get(1).hash);
		
		cRefB = b.getReferences();
		assertNotNull(cRefB);
		assertEquals(1, cRefB.size());
		assertNull(cRefB.get(0).ticketId);
		assertEquals(commit1Sha, cRefB.get(0).hash);
		
		//Delete the original patchset and confirm old references are removed
		ticketService.deletePatchset(c, c.getPatchset(1, 1), user.username);
		
		a = ticketService.getTicket(repo, a.number);
		b = ticketService.getTicket(repo, b.number);
		c = ticketService.getTicket(repo, c.number);
		assertTrue(a.hasReferences());
		assertFalse(b.hasReferences());
		assertFalse(c.hasReferences());
		
		assertNull(c.getPatchset(1, 1));
		assertNotNull(c.getPatchset(2, 1));
		
		cRefA = a.getReferences();
		assertNotNull(cRefA);
		assertEquals(1, cRefA.size());
		assertNull(cRefA.get(0).ticketId);
		assertEquals(commit2Sha, cRefA.get(0).hash);
	}
		
	
	@Test
	public void commitTicketBranchNoUnexpectedReference() throws Exception {
		setPatchsetAvailable(false);
		TicketModel a = ticketService.createTicket(repo, newTicket("commitTicketBranchNoUnexpectedReference-A"));
		
		String branchName = String.format("ticket/%d", a.number);
		git.checkout().setCreateBranch(true).setName(branchName).call();
		
		makeCommit("commit for 1 - no reference");
		makeCommit("commit for # - no reference");
		final String message = "commit for #999 - ignores invalid reference";
		final RevCommit revCommit1 = makeCommit(message);
		final String commit1Sha = revCommit1.name();
		assertPushSuccess(commit1Sha, branchName);
		
		a = ticketService.getTicket(repo, a.number);
		assertFalse(a.hasReferences());
	}

	@Test
	public void commitTicketBranchSelfReference() throws Exception {
		setPatchsetAvailable(false);
		TicketModel a = ticketService.createTicket(repo, newTicket("commitTicketBranchSelfReference-A"));
		
		String branchName = String.format("ticket/%d", a.number);
		git.checkout().setCreateBranch(true).setName(branchName).call();
		
		final String message = String.format("commit for #%d - patchset self reference", a.number);
		final RevCommit revCommit1 = makeCommit(message);
		final String commit1Sha = revCommit1.name();
		
		assertPushSuccess(commit1Sha, branchName);
		
		a = ticketService.getTicket(repo, a.number);
		assertTrue(a.hasReferences());
		
		List<Reference> cRefA = a.getReferences();
		assertNotNull(cRefA);
		assertEquals(1, cRefA.size());
		assertNull(cRefA.get(0).ticketId);
		assertEquals(commit1Sha, cRefA.get(0).hash);
	}
	
	@Test
	public void commitTicketBranchSingleReference() throws Exception {
		setPatchsetAvailable(false);
		TicketModel a = ticketService.createTicket(repo, newTicket("commitTicketBranchSingleReference-A"));
		TicketModel b = ticketService.createTicket(repo, newTicket("commitTicketBranchSingleReference-B"));
		
		String branchName = String.format("ticket/%d", a.number);
		git.checkout().setCreateBranch(true).setName(branchName).call();
		
		final String message = String.format("commit for #%d - patchset single reference", b.number);
		final RevCommit revCommit1 = makeCommit(message);
		final String commit1Sha = revCommit1.name();
		
		assertPushSuccess(commit1Sha, branchName);
		
		a = ticketService.getTicket(repo, a.number);
		b = ticketService.getTicket(repo, b.number);
		assertFalse(a.hasReferences());
		assertTrue(b.hasReferences());
		
		List<Reference> cRefB = b.getReferences();
		assertNotNull(cRefB);
		assertEquals(1, cRefB.size());
		assertNull(cRefB.get(0).ticketId);
		assertEquals(commit1Sha, cRefB.get(0).hash);
	}
	
	@Test
	public void commitTicketBranchMultiCommit() throws Exception {
		setPatchsetAvailable(false);
		TicketModel a = ticketService.createTicket(repo, newTicket("commitTicketBranchMultiCommit-A"));
		TicketModel b = ticketService.createTicket(repo, newTicket("commitTicketBranchMultiCommit-B"));
		
		String branchName = String.format("ticket/%d", a.number);
		git.checkout().setCreateBranch(true).setName(branchName).call();
		
		final String message1 = String.format("commit for #%d - patchset multi commit 1", b.number);
		final RevCommit revCommit1 = makeCommit(message1);
		final String commit1Sha = revCommit1.name();
		
		final String message2 = String.format("commit for #%d - patchset multi commit 2", b.number);
		final RevCommit revCommit2 = makeCommit(message2);
		final String commit2Sha = revCommit2.name();
		
		assertPushSuccess(commit2Sha, branchName);
		
		a = ticketService.getTicket(repo, a.number);
		b = ticketService.getTicket(repo, b.number);
		assertFalse(a.hasReferences());
		assertTrue(b.hasReferences());
		
		List<Reference> cRefB = b.getReferences();
		assertNotNull(cRefB);
		assertEquals(2, cRefB.size());
		assertNull(cRefB.get(0).ticketId);
		assertEquals(commit1Sha, cRefB.get(1).hash);
		assertEquals(commit2Sha, cRefB.get(0).hash);
	}
	
	@Test
	public void commitTicketBranchMultiReference() throws Exception {
		setPatchsetAvailable(false);
		TicketModel a = ticketService.createTicket(repo, newTicket("commitTicketBranchMultiReference-A"));
		TicketModel b = ticketService.createTicket(repo, newTicket("commitTicketBranchMultiReference-B"));
		TicketModel c = ticketService.createTicket(repo, newTicket("commitTicketBranchMultiReference-C"));
		
		String branchName = String.format("ticket/%d", a.number);
		git.checkout().setCreateBranch(true).setName(branchName).call();
		
		final String message = String.format("commit for #%d and #%d- patchset multi reference", b.number, c.number);
		final RevCommit revCommit1 = makeCommit(message);
		final String commit1Sha = revCommit1.name();
		
		assertPushSuccess(commit1Sha, branchName);
		
		a = ticketService.getTicket(repo, a.number);
		b = ticketService.getTicket(repo, b.number);
		c = ticketService.getTicket(repo, c.number);
		assertFalse(a.hasReferences());
		assertTrue(b.hasReferences());
		assertTrue(c.hasReferences());
		
		List<Reference> cRefB = b.getReferences();
		assertNotNull(cRefB);
		assertEquals(1, cRefB.size());
		assertNull(cRefB.get(0).ticketId);
		assertEquals(commit1Sha, cRefB.get(0).hash);
		
		List<Reference> cRefC = c.getReferences();
		assertNotNull(cRefC);
		assertEquals(1, cRefC.size());
		assertNull(cRefC.get(0).ticketId);
		assertEquals(commit1Sha, cRefC.get(0).hash);
	}
	
	@Test
	public void commitTicketBranchAmendReference() throws Exception {
		setPatchsetAvailable(false);

		TicketModel a = ticketService.createTicket(repo, newTicket("commitTicketBranchAmendReference-A"));
		TicketModel b = ticketService.createTicket(repo, newTicket("commitTicketBranchAmendReference-B"));
		TicketModel c = ticketService.createTicket(repo, newTicket("commitTicketBranchAmendReference-C"));
		assertFalse(c.hasPatchsets());
		
		String branchName = String.format("ticket/%d", c.number);
		git.checkout().setCreateBranch(true).setName(branchName).call();
		
		String message = String.format("commit before amend for #%d and #%d", a.number, b.number);
		final RevCommit revCommit1 = makeCommit(message);
		final String commit1Sha = revCommit1.name();
		assertPushSuccess(commit1Sha, branchName);
		
		a = ticketService.getTicket(repo, a.number);
		b = ticketService.getTicket(repo, b.number);
		c = ticketService.getTicket(repo, c.number);
		assertTrue(a.hasReferences());
		assertTrue(b.hasReferences());
		assertFalse(c.hasReferences());
		assertFalse(c.hasPatchsets());
		
		List<Reference> cRefA = a.getReferences();
		assertNotNull(cRefA);
		assertEquals(1, cRefA.size());
		assertNull(cRefA.get(0).ticketId);
		assertEquals(commit1Sha, cRefA.get(0).hash);
		
		List<Reference> cRefB = b.getReferences();
		assertNotNull(cRefB);
		assertEquals(1, cRefB.size());
		assertNull(cRefB.get(0).ticketId);
		assertEquals(commit1Sha, cRefB.get(0).hash);
		
		//Confirm that old invalid references removed for both tickets
		//and new reference added for one referenced ticket
		message = String.format("commit after amend for #%d", a.number);
		final String commit2Sha = amendCommit(message);
		
		assertForcePushSuccess(commit2Sha, branchName);
		
		a = ticketService.getTicket(repo, a.number);
		b = ticketService.getTicket(repo, b.number);
		c = ticketService.getTicket(repo, c.number);
		assertTrue(a.hasReferences());
		assertFalse(b.hasReferences());
		assertFalse(c.hasReferences());
		assertFalse(c.hasPatchsets());
		
		cRefA = a.getReferences();
		assertNotNull(cRefA);
		assertEquals(1, cRefA.size());
		assertNull(cRefA.get(0).ticketId);
		assertEquals(commit2Sha, cRefA.get(0).hash);
	}
	
	@Test
	public void commitTicketBranchDeleteNoMergeReference() throws Exception {
		setPatchsetAvailable(false);

		TicketModel a = ticketService.createTicket(repo, newTicket("commitTicketBranchDeleteNoMergeReference-A"));
		TicketModel b = ticketService.createTicket(repo, newTicket("commitTicketBranchDeleteNoMergeReference-B"));
		TicketModel c = ticketService.createTicket(repo, newTicket("commitTicketBranchDeleteNoMergeReference-C"));
		assertFalse(c.hasPatchsets());
		
		String branchName = String.format("ticket/%d", c.number);
		git.checkout().setCreateBranch(true).setName(branchName).call();
		
		String message = String.format("commit before amend for #%d and #%d", a.number, b.number);
		final RevCommit revCommit1 = makeCommit(message);
		final String commit1Sha = revCommit1.name();
		assertPushSuccess(commit1Sha, branchName);
		
		a = ticketService.getTicket(repo, a.number);
		b = ticketService.getTicket(repo, b.number);
		c = ticketService.getTicket(repo, c.number);
		assertTrue(a.hasReferences());
		assertTrue(b.hasReferences());
		assertFalse(c.hasReferences());
		
		List<Reference> cRefA = a.getReferences();
		assertNotNull(cRefA);
		assertEquals(1, cRefA.size());
		assertNull(cRefA.get(0).ticketId);
		assertEquals(commit1Sha, cRefA.get(0).hash);
		
		List<Reference> cRefB = b.getReferences();
		assertNotNull(cRefB);
		assertEquals(1, cRefB.size());
		assertNull(cRefB.get(0).ticketId);
		assertEquals(commit1Sha, cRefB.get(0).hash);
		
		//Confirm that old invalid references removed for both tickets
		assertDeleteBranch(branchName);
		
		a = ticketService.getTicket(repo, a.number);
		b = ticketService.getTicket(repo, b.number);
		c = ticketService.getTicket(repo, c.number);
		assertFalse(a.hasReferences());
		assertFalse(b.hasReferences());
		assertFalse(c.hasReferences());
	}
	
	@Test
	public void commitTicketBranchDeletePostMergeReference() throws Exception {
		setPatchsetAvailable(false);

		TicketModel a = ticketService.createTicket(repo, newTicket("commitTicketBranchDeletePostMergeReference-A"));
		TicketModel b = ticketService.createTicket(repo, newTicket("commitTicketBranchDeletePostMergeReference-B"));
		TicketModel c = ticketService.createTicket(repo, newTicket("commitTicketBranchDeletePostMergeReference-C"));
		assertFalse(c.hasPatchsets());
		
		String branchName = String.format("ticket/%d", c.number);
		git.checkout().setCreateBranch(true).setName(branchName).call();
		
		String message = String.format("commit before amend for #%d and #%d", a.number, b.number);
		final RevCommit revCommit1 = makeCommit(message);
		final String commit1Sha = revCommit1.name();
		assertPushSuccess(commit1Sha, branchName);
		
		a = ticketService.getTicket(repo, a.number);
		b = ticketService.getTicket(repo, b.number);
		c = ticketService.getTicket(repo, c.number);
		assertTrue(a.hasReferences());
		assertTrue(b.hasReferences());
		assertFalse(c.hasReferences());
		
		List<Reference> cRefA = a.getReferences();
		assertNotNull(cRefA);
		assertEquals(1, cRefA.size());
		assertNull(cRefA.get(0).ticketId);
		assertEquals(commit1Sha, cRefA.get(0).hash);
		
		List<Reference> cRefB = b.getReferences();
		assertNotNull(cRefB);
		assertEquals(1, cRefB.size());
		assertNull(cRefB.get(0).ticketId);
		assertEquals(commit1Sha, cRefB.get(0).hash);
		
		git.checkout().setCreateBranch(false).setName("refs/heads/master").call();
		
		// merge the tip of the branch into master
		MergeResult mergeResult = git.merge().setFastForward(FastForwardMode.NO_FF).include(revCommit1.getId()).call();
		assertEquals(MergeResult.MergeStatus.MERGED, mergeResult.getMergeStatus());

		// push the merged master to the origin
		Iterable<PushResult> results = git.push().setCredentialsProvider(cp).setRemote("origin").call();
		for (PushResult result : results) {
			RemoteRefUpdate ref = result.getRemoteUpdate("refs/heads/master");
			assertEquals(Status.OK, ref.getStatus());
		}
		
		//As everything has been merged no references should be changed
		assertDeleteBranch(branchName);
		
		a = ticketService.getTicket(repo, a.number);
		b = ticketService.getTicket(repo, b.number);
		c = ticketService.getTicket(repo, c.number);
		assertTrue(a.hasReferences());
		assertTrue(b.hasReferences());
		assertFalse(c.hasReferences());
		
		cRefA = a.getReferences();
		assertNotNull(cRefA);
		assertEquals(1, cRefA.size());
		assertNull(cRefA.get(0).ticketId);
		assertEquals(commit1Sha, cRefA.get(0).hash);
		
		cRefB = b.getReferences();
		assertNotNull(cRefB);
		assertEquals(1, cRefB.size());
		assertNull(cRefB.get(0).ticketId);
		assertEquals(commit1Sha, cRefB.get(0).hash);
	}
	
	private static Change newComment(String text) {
		Change change = new Change("JUnit");
		change.comment(text);
		return change;
	}
	
	private static Change newTicket(String title) {
		Change change = new Change("JUnit");
		change.setField(Field.title, title);
		change.setField(Field.type, TicketModel.Type.Bug );
		return change;
	}
	
	private static RevCommit makeCommit(String message) throws Exception {
		File file = new File(workingCopy, "testFile.txt");
		OutputStreamWriter os = new OutputStreamWriter(new FileOutputStream(file, true), Constants.CHARSET);
		BufferedWriter w = new BufferedWriter(os);
		w.write("// " + new Date().toString() + "\n");
		w.close();
		git.add().addFilepattern(file.getName()).call();
		RevCommit rev = git.commit().setMessage(message).call();
		return rev;
	}
	
	private static String amendCommit(String message) throws Exception {
		File file = new File(workingCopy, "testFile.txt");
		OutputStreamWriter os = new OutputStreamWriter(new FileOutputStream(file, true), Constants.CHARSET);
		BufferedWriter w = new BufferedWriter(os);
		w.write("// " + new Date().toString() + "\n");
		w.close();
		git.add().addFilepattern(file.getName()).call();
		RevCommit rev = git.commit().setAmend(true).setMessage(message).call();
		return rev.getId().name();
	}
	
	
	private void setPatchsetAvailable(boolean state) throws GitBlitException {
		repo.acceptNewPatchsets = state;
		gitblit().updateRepositoryModel(repo.name, repo, false);
	}
	
	
	private void assertPushSuccess(String commitSha, String branchName) throws Exception {
		Iterable<PushResult> results = git.push().setRemote("origin").setCredentialsProvider(cp).call();
		
		for (PushResult result : results) {
			RemoteRefUpdate ref = result.getRemoteUpdate("refs/heads/" + branchName);
			assertEquals(Status.OK, ref.getStatus());
			assertEquals(commitSha, ref.getNewObjectId().name());
		}
	}
	
	private void assertForcePushSuccess(String commitSha, String branchName) throws Exception {
		Iterable<PushResult> results = git.push().setForce(true).setRemote("origin").setCredentialsProvider(cp).call();
		
		for (PushResult result : results) {
			RemoteRefUpdate ref = result.getRemoteUpdate("refs/heads/" + branchName);
			assertEquals(Status.OK, ref.getStatus());
			assertEquals(commitSha, ref.getNewObjectId().name());
		}
	}
	
	private void assertDeleteBranch(String branchName) throws Exception {
		
		RefSpec refSpec = new RefSpec()
        .setSource(null)
        .setDestination("refs/heads/" + branchName);
		
		Iterable<PushResult> results = git.push().setRefSpecs(refSpec).setRemote("origin").setCredentialsProvider(cp).call();
		
		for (PushResult result : results) {
			RemoteRefUpdate ref = result.getRemoteUpdate("refs/heads/" + branchName);
			assertEquals(Status.OK, ref.getStatus());
		}
	}
}