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

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;

import com.gitblit.FileSettings;
import com.gitblit.GitBlit;
import com.gitblit.GitBlitException;
import com.gitblit.JettyLoginService;
import com.gitblit.models.RepositoryModel;

public class GitBlitSuite extends TestSetup {
	public static final File REPOSITORIES = new File("git");

	private GitBlitSuite(TestSuite suite) {
		super(suite);
	}

	public static Test suite() {
		TestSuite suite = new TestSuite();
		suite.addTestSuite(TimeUtilsTest.class);
		suite.addTestSuite(StringUtilsTest.class);
		suite.addTestSuite(ByteFormatTest.class);
		suite.addTestSuite(MarkdownUtilsTest.class);
		suite.addTestSuite(JGitUtilsTest.class);
		suite.addTestSuite(DiffUtilsTest.class);
		suite.addTestSuite(MetricUtilsTest.class);
		suite.addTestSuite(TicgitUtilsTest.class);
		suite.addTestSuite(GitBlitTest.class);
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
		FileSettings settings = new FileSettings("distrib/gitblit.properties");
		GitBlit.self().configureContext(settings);
		JettyLoginService loginService = new JettyLoginService(new File("distrib/users.properties"));
		loginService.loadUsers();
		GitBlit.self().setLoginService(loginService);

		if (REPOSITORIES.exists() || REPOSITORIES.mkdirs()) {
			cloneOrFetch("helloworld.git", "https://github.com/git/hello-world.git", true);
			cloneOrFetch("nested/helloworld.git", "https://github.com/git/hello-world.git", true);
			cloneOrFetch("ticgit.git", "https://github.com/jeffWelling/ticgit.git", true);

			enableTickets("ticgit.git");
			enableDocs("ticgit.git");
			showRemoteBranches("ticgit.git");
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

	private void enableTickets(String repositoryName) {
		try {
			RepositoryModel model = GitBlit.self().getRepositoryModel(repositoryName);
			model.useTickets = true;
			GitBlit.self().editRepositoryModel(model.name, model, false);
		} catch (GitBlitException g) {
			g.printStackTrace();
		}
	}
	
	private void enableDocs(String repositoryName) {
		try {
			RepositoryModel model = GitBlit.self().getRepositoryModel(repositoryName);
			model.useDocs = true;
			GitBlit.self().editRepositoryModel(model.name, model, false);
		} catch (GitBlitException g) {
			g.printStackTrace();
		}
	}
	
	private void showRemoteBranches(String repositoryName) {
		try {
			RepositoryModel model = GitBlit.self().getRepositoryModel(repositoryName);
			model.showRemoteBranches = true;
			GitBlit.self().editRepositoryModel(model.name, model, false);
		} catch (GitBlitException g) {
			g.printStackTrace();
		}
	}
}
