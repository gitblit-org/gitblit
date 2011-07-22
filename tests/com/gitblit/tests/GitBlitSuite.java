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

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;

import com.gitblit.FileSettings;
import com.gitblit.FileUserService;
import com.gitblit.GitBlit;
import com.gitblit.GitBlitException;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.JGitUtils;

public class GitBlitSuite extends TestSetup {
	public static final File REPOSITORIES = new File("git");

	private GitBlitSuite(TestSuite suite) {
		super(suite);
	}

	public static Test suite() {
		TestSuite suite = new TestSuite();
		suite.addTestSuite(FileUtilsTest.class);
		suite.addTestSuite(TimeUtilsTest.class);
		suite.addTestSuite(StringUtilsTest.class);
		suite.addTestSuite(ByteFormatTest.class);
		suite.addTestSuite(MarkdownUtilsTest.class);
		suite.addTestSuite(JGitUtilsTest.class);
		suite.addTestSuite(SyndicationUtilsTest.class);
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

	public static Repository getJGitRepository() throws Exception {
		return new FileRepository(new File(REPOSITORIES, "test/jgit.git"));
	}

	public static Repository getBluezGnomeRepository() throws Exception {
		return new FileRepository(new File(REPOSITORIES, "test/bluez-gnome.git"));
	}

	@Override
	protected void setUp() throws Exception {
		FileSettings settings = new FileSettings("distrib/gitblit.properties");
		GitBlit.self().configureContext(settings);
		FileUserService loginService = new FileUserService(new File("distrib/users.properties"));
		GitBlit.self().setUserService(loginService);

		if (REPOSITORIES.exists() || REPOSITORIES.mkdirs()) {
			cloneOrFetch("helloworld.git", "https://github.com/git/hello-world.git");
			cloneOrFetch("ticgit.git", "https://github.com/jeffWelling/ticgit.git");
			cloneOrFetch("test/bluez-gnome.git",
					"https://git.kernel.org/pub/scm/bluetooth/bluez-gnome.git");
			cloneOrFetch("test/jgit.git", "https://github.com/eclipse/jgit.git");
			cloneOrFetch("test/helloworld.git", "https://github.com/git/hello-world.git");

			enableTickets("ticgit.git");
			enableDocs("ticgit.git");
			showRemoteBranches("ticgit.git");
			showRemoteBranches("test/jgit.git");
		}
	}

	private void cloneOrFetch(String name, String fromUrl) throws Exception {
		System.out.print("Fetching " + name + "... ");
		JGitUtils.cloneRepository(REPOSITORIES, name, fromUrl);
		System.out.println("done.");
	}

	private void enableTickets(String repositoryName) {
		try {
			RepositoryModel model = GitBlit.self().getRepositoryModel(repositoryName);
			model.useTickets = true;
			GitBlit.self().updateRepositoryModel(model.name, model, false);
		} catch (GitBlitException g) {
			g.printStackTrace();
		}
	}

	private void enableDocs(String repositoryName) {
		try {
			RepositoryModel model = GitBlit.self().getRepositoryModel(repositoryName);
			model.useDocs = true;
			GitBlit.self().updateRepositoryModel(model.name, model, false);
		} catch (GitBlitException g) {
			g.printStackTrace();
		}
	}

	private void showRemoteBranches(String repositoryName) {
		try {
			RepositoryModel model = GitBlit.self().getRepositoryModel(repositoryName);
			model.showRemoteBranches = true;
			GitBlit.self().updateRepositoryModel(model.name, model, false);
		} catch (GitBlitException g) {
			g.printStackTrace();
		}
	}
}
