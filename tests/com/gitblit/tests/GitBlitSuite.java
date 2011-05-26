package com.gitblit.tests;

import java.io.File;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;

public class GitBlitSuite extends TestSetup {
	public static final File REPOSITORIES = new File("git");

	private GitBlitSuite(TestSuite suite) {
		super(suite);
	}

	public static Test suite() {
		TestSuite suite = new TestSuite();
		suite.addTestSuite(JGitUtilsTest.class);
		return new GitBlitSuite(suite);
	}
	
	public static Repository getHelloworldRepository() throws Exception {
		return new FileRepository(new File(REPOSITORIES, "helloworld.git"));
	}

	public static Repository getTicgitRepository() throws Exception {
		return new FileRepository(new File(REPOSITORIES, "ticgit.git"));
	}

	@Override
	protected void setUp() throws Exception {
		if (REPOSITORIES.exists() || REPOSITORIES.mkdirs()) {
			cloneOrFetch("helloworld.git", "https://github.com/git/hello-world.git", true);
			cloneOrFetch("nested/helloworld.git", "https://github.com/git/hello-world.git", true);
			cloneOrFetch("ticgit.git", "https://github.com/jeffWelling/ticgit.git", true);
		}
	}

	private void cloneOrFetch(String toFolder, String fromUrl, boolean bare) throws Exception {
		File folder = new File(REPOSITORIES, toFolder + (bare ? "" : "/.git"));
		if (folder.exists()) {
			System.out.print("Updating " + (bare ? "bare " : " ") + toFolder + "... ");
			FileRepository repository = new FileRepository(new File(REPOSITORIES, toFolder));
			Git git = new Git(repository);
			git.fetch().call();
			repository.close();
			System.out.println("done.");
		} else {
			System.out.println("Cloning " + (bare ? "bare " : " ") + toFolder + "... ");
			CloneCommand clone = new CloneCommand();
			clone.setBare(bare);
			clone.setCloneAllBranches(true);
			clone.setURI(fromUrl);
			clone.setDirectory(folder);
			clone.call();
			System.out.println("done.");
		}
	}
}
