package com.gitblit.tests;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.MessageFormat;
import java.util.Date;
import java.util.concurrent.Executors;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gitblit.GitBlitServer;

public class GitServletTest {

	File folder = new File(GitBlitSuite.REPOSITORIES, "working/ticgit");

	static int port = 8180;

	static int shutdownPort = 8181;

	@BeforeClass
	public static void startGitblit() throws Exception {
		// Start a Gitblit instance
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				GitBlitServer.main("--httpPort", "" + port, "--httpsPort", "0", "--shutdownPort",
						"" + shutdownPort, "--repositoriesFolder",
						"\"" + GitBlitSuite.REPOSITORIES.getAbsolutePath() + "\"", "--userService",
						"distrib/users.conf");
			}
		});

		// Wait a few seconds for it to be running
		Thread.sleep(2500);
	}

	@AfterClass
	public static void stopGitblit() throws Exception {
		// Stop Gitblit
		GitBlitServer.main("--stop", "--shutdownPort", "" + shutdownPort);

		// Wait a few seconds for it to be running
		Thread.sleep(2500);
	}

	@Test
	public void testClone() throws Exception {
		if (folder.exists()) {
			FileUtils.delete(folder, FileUtils.RECURSIVE);
		}
		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("http://localhost:{0,number,#}/git/ticgit.git", port));
		clone.setDirectory(folder);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		clone.call();
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

	@Test
	public void testBogusLoginClone() throws Exception {
		File folder = new File(GitBlitSuite.REPOSITORIES, "working/gitblit");
		if (folder.exists()) {
			FileUtils.delete(folder, FileUtils.RECURSIVE);
		}
		CloneCommand clone = Git.cloneRepository();
		clone.setURI(MessageFormat.format("http://localhost:{0,number,#}/git/gitblit.git", port));
		clone.setDirectory(folder);
		clone.setBare(false);
		clone.setCloneAllBranches(true);
		clone.setCredentialsProvider(new UsernamePasswordCredentialsProvider("bogus", "bogus"));
		clone.call();
	}
}
