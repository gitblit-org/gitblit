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
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.GitBlit;
import com.gitblit.models.RepositoryModel;

public class GitServletTest {

	File folder = new File(GitBlitSuite.REPOSITORIES, "working/ticgit");

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
		}
	}

	@Test
	public void testClone() throws Exception {
		if (folder.exists()) {
			FileUtils.delete(folder, FileUtils.RECURSIVE);
		}
		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("{0}/git/ticgit.git", url));
		clone.setDirectory(folder);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider(account, password));
		clone.call();
		assertTrue(true);
	}

	@Test
	public void testBogusLoginClone() throws Exception {
		// restrict repository access
		RepositoryModel model = GitBlit.self().getRepositoryModel("ticgit.git");
		model.accessRestriction = AccessRestrictionType.CLONE;
		GitBlit.self().updateRepositoryModel(model.name, model, false);

		// delete any existing working folder
		File folder = new File(GitBlitSuite.REPOSITORIES, "working/gitblit");
		if (folder.exists()) {
			FileUtils.delete(folder, FileUtils.RECURSIVE);
		}
		boolean cloned = false;
		try {
			CloneCommand clone = Git.cloneRepository();
			clone.setURI(MessageFormat.format("{0}/git/ticgit.git", url));
			clone.setDirectory(folder);
			clone.setBare(false);
			clone.setCloneAllBranches(true);
			clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider("bogus", "bogus"));
			clone.call();
			cloned = true;
		} catch (Exception e) {
			e.printStackTrace();
		}

		// restore anonymous repository access
		model.accessRestriction = AccessRestrictionType.NONE;
		GitBlit.self().updateRepositoryModel(model.name, model, false);

		assertFalse("Bogus login cloned a repository?!", cloned);
	}

	@Test
	public void testAnonymousCommit() throws Exception {
		Git git = Git.open(folder);
		File file = new File(folder, "TODO");
		OutputStreamWriter os = new OutputStreamWriter(new FileOutputStream(file, true));
		BufferedWriter w = new BufferedWriter(os);
		w.write("// " + new Date().toString() + "\n");
		w.close();
		git.add().addFilepattern(file.getName()).call();
		git.commit().setMessage("test commit").call();
		git.push().setPushAll().call();
		git.getRepository().close();
	}
}
