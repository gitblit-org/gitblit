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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.gitblit.FileSettings;
import com.gitblit.GitBlitException;
import com.gitblit.GitBlitServer;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.servlet.GitblitContext;
import com.gitblit.tests.GitBlitTestConfig;

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
@SuiteClasses({ SyndicationUtilsTest.class,
	GitBlitTest.class, FederationTests.class, RpcTests.class, GitServletTest.class, GitDaemonTest.class,
	SshDaemonTest.class, GroovyScriptTest.class, RepositoryModelTest.class,	
	SshKeysDispatcherTest.class, 
	FilestoreManagerTest.class, FilestoreServletTest.class, TicketReferenceTest.class })
public class GitBlitSuite {

	public static final File BASEFOLDER = GitBlitTestConfig.BASEFOLDER;

	public static final File REPOSITORIES = GitBlitTestConfig.REPOSITORIES;

	public static final File SETTINGS = GitBlitTestConfig.SETTINGS;

	public static final File USERSCONF = GitBlitTestConfig.USERSCONF;

	public static final FileSettings helloworldSettings = GitBlitTestConfig.helloworldSettings;

	static int port = 8280;
	static int gitPort = 8300;
	static int shutdownPort = 8281;
	static int sshPort = 39418;

	public static String url = "http://localhost:" + port;
	public static String gitServletUrl = "http://localhost:" + port + "/git";
	public static String gitDaemonUrl = "git://localhost:" + gitPort;
	public static String sshDaemonUrl = "ssh://admin@localhost:" + sshPort;
	public static String account = "admin";
	public static String password = "admin";

	private static AtomicBoolean started = new AtomicBoolean(false);

	public static boolean startGitblit() throws Exception {
		if (started.get()) {
			// already started
			return false;
		}

		GitServletTest.deleteWorkingFolders();

		// Start a Gitblit instance
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			@Override
			public void run() {
				GitBlitServer.main(
						"--httpPort", "" + port,
						"--httpsPort", "0",
						"--shutdownPort", "" + shutdownPort,
						"--gitPort", "" + gitPort,
						"--sshPort", "" + sshPort,
						"--repositoriesFolder", REPOSITORIES.getAbsolutePath(),
						"--userService", USERSCONF.getAbsolutePath(),
						"--settings", SETTINGS.getAbsolutePath(),
						"--baseFolder", "data");
			}
		});

		// Wait a few seconds for it to be running
		Thread.sleep(5000);

		started.set(true);
		return true;
	}

	public static void stopGitblit() throws Exception {
		// Stop Gitblit
		GitBlitServer.main("--stop", "--shutdownPort", "" + shutdownPort);

		// Wait a few seconds for it to be running
		Thread.sleep(5000);
	}

	public static void deleteRefChecksFolder() throws IOException {
		File refChecks = new File(REPOSITORIES, "refchecks");
		if (refChecks.exists()) {
			FileUtils.delete(refChecks, FileUtils.RECURSIVE | FileUtils.RETRY);
		}
	}

	@BeforeClass
	public static void setUp() throws Exception {
		//"refchecks" folder is used in GitServletTest;
		//need be deleted before Gitblit server instance is started
		deleteRefChecksFolder();
		startGitblit();

		GitBlitTestConfig.setUpGitRepositories();

		showRemoteBranches("ticgit.git");
		automaticallyTagBranchTips("ticgit.git");
		showRemoteBranches("test/jgit.git");
		automaticallyTagBranchTips("test/jgit.git");
	}

	@AfterClass
	public static void tearDown() throws Exception {
		stopGitblit();
	}

	private static void showRemoteBranches(String repositoryName) {
		try {
			IRepositoryManager repositoryManager = GitblitContext.getManager(IRepositoryManager.class);
			RepositoryModel model = repositoryManager.getRepositoryModel(repositoryName);
			model.showRemoteBranches = true;
			repositoryManager.updateRepositoryModel(model.name, model, false);
		} catch (GitBlitException g) {
			g.printStackTrace();
		}
	}

	private static void automaticallyTagBranchTips(String repositoryName) {
		try {
			IRepositoryManager repositoryManager = GitblitContext.getManager(IRepositoryManager.class);
			RepositoryModel model = repositoryManager.getRepositoryModel(repositoryName);
			model.useIncrementalPushTags = true;
			repositoryManager.updateRepositoryModel(model.name, model, false);
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
