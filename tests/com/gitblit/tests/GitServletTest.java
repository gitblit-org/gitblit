package com.gitblit.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.GitBlit;
import com.gitblit.models.RepositoryModel;

public class GitServletTest {

	static File ticgitFolder = new File(GitBlitSuite.REPOSITORIES, "working/ticgit");
	
	static File ticgit2Folder = new File(GitBlitSuite.REPOSITORIES, "working/ticgit2");

	static File jgitFolder = new File(GitBlitSuite.REPOSITORIES, "working/jgit");
	
	static File jgit2Folder = new File(GitBlitSuite.REPOSITORIES, "working/jgit2");

	String url = GitBlitSuite.url;
	String account = GitBlitSuite.account;
	String password = GitBlitSuite.password;

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
			FileUtils.delete(ticgitFolder, FileUtils.RECURSIVE);
		}
		if (ticgit2Folder.exists()) {
			FileUtils.delete(ticgit2Folder, FileUtils.RECURSIVE);
		}
		if (jgitFolder.exists()) {
			FileUtils.delete(jgitFolder, FileUtils.RECURSIVE);
		}
		if (jgit2Folder.exists()) {
			FileUtils.delete(jgit2Folder, FileUtils.RECURSIVE);
		}
	}

	@Test
	public void testClone() throws Exception {
		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/git/ticgit.git", url));
		clone.setDirectory(ticgitFolder);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider(account, password));
		close(clone.call());		
		assertTrue(true);
	}

	@Test
	public void testBogusLoginClone() throws Exception {
		// restrict repository access
		RepositoryModel model = GitBlit.self().getRepositoryModel("ticgit.git");
		model.accessRestriction = AccessRestrictionType.CLONE;
		GitBlit.self().updateRepositoryModel(model.name, model, false);

		// delete any existing working folder		
		boolean cloned = false;
		try {
			CloneCommand clone = Git.cloneRepository();
			clone.setURI(MessageFormat.format("{0}/git/ticgit.git", url));
			clone.setDirectory(ticgit2Folder);
			clone.setBare(false);
			clone.setCloneAllBranches(true);
			clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider("bogus", "bogus"));
			close(clone.call());
			cloned = true;
		} catch (Exception e) {
			// swallow the exception which we expect
		}

		// restore anonymous repository access
		model.accessRestriction = AccessRestrictionType.NONE;
		GitBlit.self().updateRepositoryModel(model.name, model, false);

		assertFalse("Bogus login cloned a repository?!", cloned);
	}

	@Test
	public void testAnonymousPush() throws Exception {
		Git git = Git.open(ticgitFolder);
		File file = new File(ticgitFolder, "TODO");
		OutputStreamWriter os = new OutputStreamWriter(new FileOutputStream(file, true), Constants.CHARSET);
		BufferedWriter w = new BufferedWriter(os);
		w.write("// hellol中文 " + new Date().toString() + "\n");
		w.close();
		git.add().addFilepattern(file.getName()).call();
		git.commit().setMessage("test commit").call();
		git.push().setPushAll().call();
		close(git);
	}

	@Test
	public void testSubfolderPush() throws Exception {
		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/git/test/jgit.git", url));
		clone.setDirectory(jgitFolder);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider(account, password));
		close(clone.call());
		assertTrue(true);

		Git git = Git.open(jgitFolder);
		File file = new File(jgitFolder, "TODO");
		OutputStreamWriter os = new OutputStreamWriter(new FileOutputStream(file, true), Constants.CHARSET);
		BufferedWriter w = new BufferedWriter(os);
		w.write("// " + new Date().toString() + "\n");
		w.close();
		git.add().addFilepattern(file.getName()).call();
		git.commit().setMessage("test commit").call();
		git.push().setPushAll().call();
		close(git);
	}
	
	@Test
	public void testPushToNonBareRepository() throws Exception {
		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/git/working/jgit", url));
		clone.setDirectory(jgit2Folder);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider(account, password));
		close(clone.call());
		assertTrue(true);

		Git git = Git.open(jgit2Folder);
		File file = new File(jgit2Folder, "NONBARE");
		OutputStreamWriter os = new OutputStreamWriter(new FileOutputStream(file, true), Constants.CHARSET);
		BufferedWriter w = new BufferedWriter(os);
		w.write("// " + new Date().toString() + "\n");
		w.close();
		git.add().addFilepattern(file.getName()).call();
		git.commit().setMessage("test commit followed by push to non-bare repository").call();
		try {
			git.push().setPushAll().call();
			assertTrue(false);
		} catch (Exception e) {
			assertTrue(e.getCause().getMessage().contains("git-receive-pack not permitted"));
		}
		close(git);
	}
	
	private void close(Git git) {
		// really close the repository
		// decrement the use counter to 0
		for (int i = 0; i < 2; i++) {
			git.getRepository().close();
		}
	}
}
