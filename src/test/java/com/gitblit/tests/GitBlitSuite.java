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
import java.lang.reflect.Field;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.util.FS;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.gitblit.GitBlit;
import com.gitblit.GitBlitException;
import com.gitblit.GitBlitServer;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.JGitUtils;

/**
 * The GitBlitSuite uses test-gitblit.properties and test-users.conf. The suite
 * is fairly comprehensive for all lower-level functionality. Wicket pages are
 * currently not unit-tested.
 * 
 * This suite starts a Gitblit server instance within the same JVM instance as
 * the unit tests. This allows the unit tests to access the GitBlit static
 * singleton while also being able to communicate with the instance via tcp/ip
 * for testing rpc requests, federation requests, and git servlet operations.
 * 
 * @author James Moger
 * 
 */
@RunWith(Suite.class)
@SuiteClasses({ ArrayUtilsTest.class, FileUtilsTest.class, TimeUtilsTest.class,
		StringUtilsTest.class, Base64Test.class, JsonUtilsTest.class, ByteFormatTest.class,
		ObjectCacheTest.class, PermissionsTest.class, UserServiceTest.class, LdapUserServiceTest.class,
		MarkdownUtilsTest.class, JGitUtilsTest.class, SyndicationUtilsTest.class,
		DiffUtilsTest.class, MetricUtilsTest.class, TicgitUtilsTest.class, X509UtilsTest.class,
		GitBlitTest.class, FederationTests.class, RpcTests.class, GitServletTest.class, GitDaemonTest.class,
		GroovyScriptTest.class, LuceneExecutorTest.class, IssuesTest.class, RepositoryModelTest.class,
		FanoutServiceTest.class })
public class GitBlitSuite {

	public static final File REPOSITORIES = new File("data/git");

	static int port = 8280;
	static int gitPort = 8300;
	static int shutdownPort = 8281;

	public static String url = "http://localhost:" + port;
	public static String gitServletUrl = "http://localhost:" + port + "/git";
	public static String gitDaemonUrl = "git://localhost:" + gitPort;
	public static String account = "admin";
	public static String password = "admin";

	private static AtomicBoolean started = new AtomicBoolean(false);

	public static Repository getHelloworldRepository() throws Exception {
		return new FileRepository(new File(REPOSITORIES, "helloworld.git"));
	}

	public static Repository getTicgitRepository() throws Exception {
		return new FileRepository(new File(REPOSITORIES, "ticgit.git"));
	}

	public static Repository getJGitRepository() throws Exception {
		return new FileRepository(new File(REPOSITORIES, "test/jgit.git"));
	}

	public static Repository getAmbitionRepository() throws Exception {
		return new FileRepository(new File(REPOSITORIES, "test/ambition.git"));
	}

	public static Repository getTheoreticalPhysicsRepository() throws Exception {
		return new FileRepository(new File(REPOSITORIES, "test/theoretical-physics.git"));
	}

	public static Repository getIssuesTestRepository() throws Exception {
		JGitUtils.createRepository(REPOSITORIES, "gb-issues.git").close();
		return new FileRepository(new File(REPOSITORIES, "gb-issues.git"));
	}
	
	public static Repository getGitectiveRepository() throws Exception {
		return new FileRepository(new File(REPOSITORIES, "test/gitective.git"));
	}

	public static boolean startGitblit() throws Exception {
		if (started.get()) {
			// already started
			return false;
		}
		
		GitServletTest.deleteWorkingFolders();
		
		// Start a Gitblit instance
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				GitBlitServer.main("--httpPort", "" + port, "--httpsPort", "0", "--shutdownPort",
						"" + shutdownPort, "--gitPort", "" + gitPort, "--repositoriesFolder",
						"\"" + GitBlitSuite.REPOSITORIES.getAbsolutePath() + "\"", "--userService",
						"test-users.conf", "--settings", "test-gitblit.properties",
						"--baseFolder", "data");
			}
		});

		// Wait a few seconds for it to be running
		Thread.sleep(2500);

		started.set(true);
		return true;
	}

	public static void stopGitblit() throws Exception {
		// Stop Gitblit
		GitBlitServer.main("--stop", "--shutdownPort", "" + shutdownPort);

		// Wait a few seconds for it to be running
		Thread.sleep(5000);
	}

	@BeforeClass
	public static void setUp() throws Exception {
		startGitblit();

		if (REPOSITORIES.exists() || REPOSITORIES.mkdirs()) {
			cloneOrFetch("helloworld.git", "https://github.com/git/hello-world.git");
			cloneOrFetch("ticgit.git", "https://github.com/schacon/ticgit.git");
			cloneOrFetch("test/jgit.git", "https://github.com/eclipse/jgit.git");
			cloneOrFetch("test/helloworld.git", "https://github.com/git/hello-world.git");
			cloneOrFetch("test/ambition.git", "https://github.com/defunkt/ambition.git");
			cloneOrFetch("test/theoretical-physics.git", "https://github.com/certik/theoretical-physics.git");
			cloneOrFetch("test/gitective.git", "https://github.com/kevinsawicki/gitective.git");
			
			enableTickets("ticgit.git");
			enableDocs("ticgit.git");
			showRemoteBranches("ticgit.git");
			automaticallyTagBranchTips("ticgit.git");
			showRemoteBranches("test/jgit.git");
			automaticallyTagBranchTips("test/jgit.git");	
		}
	}

	@AfterClass
	public static void tearDown() throws Exception {
		stopGitblit();
	}

	private static void cloneOrFetch(String name, String fromUrl) throws Exception {
		System.out.print("Fetching " + name + "... ");
		try {
			JGitUtils.cloneRepository(REPOSITORIES, name, fromUrl);
		} catch (Throwable t) {
			System.out.println("Error: " + t.getMessage());
		}
		System.out.println("done.");
	}

	private static void enableTickets(String repositoryName) {
		try {
			RepositoryModel model = GitBlit.self().getRepositoryModel(repositoryName);
			model.useTickets = true;
			GitBlit.self().updateRepositoryModel(model.name, model, false);
		} catch (GitBlitException g) {
			g.printStackTrace();
		}
	}

	private static void enableDocs(String repositoryName) {
		try {
			RepositoryModel model = GitBlit.self().getRepositoryModel(repositoryName);
			model.useDocs = true;
			GitBlit.self().updateRepositoryModel(model.name, model, false);
		} catch (GitBlitException g) {
			g.printStackTrace();
		}
	}

	private static void showRemoteBranches(String repositoryName) {
		try {
			RepositoryModel model = GitBlit.self().getRepositoryModel(repositoryName);
			model.showRemoteBranches = true;
			GitBlit.self().updateRepositoryModel(model.name, model, false);
		} catch (GitBlitException g) {
			g.printStackTrace();
		}
	}
	
	private static void automaticallyTagBranchTips(String repositoryName) {
		try {
			RepositoryModel model = GitBlit.self().getRepositoryModel(repositoryName);
			model.useIncrementalPushTags = true;
			GitBlit.self().updateRepositoryModel(model.name, model, false);
		} catch (GitBlitException g) {
			g.printStackTrace();
		}
	}
	
	public static void close(File repository) {
		try {
			File gitDir = FileKey.resolve(repository, FS.detect());
			if (gitDir != null && gitDir.exists()) {
				close(RepositoryCache.open(FileKey.exact(gitDir, FS.detect())));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void close(Git git) {
		close(git.getRepository());
	}
	
	public static void close(Repository r) {
		RepositoryCache.close(r);

		// assume 2 uses in case reflection fails
		int uses = 2;
		try {
			Field useCnt = Repository.class.getDeclaredField("useCnt");
			useCnt.setAccessible(true);
			uses = ((AtomicInteger) useCnt.get(r)).get();
		} catch (Exception e) {
			e.printStackTrace();
		}
		for (int i = 0; i < uses; i++) {
			r.close();
		}
	}
}
