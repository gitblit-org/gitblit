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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.gitblit.instance.GitblitInstanceIdTest;
import com.gitblit.utils.TimeUtilsTest;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
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
		UserModelTest.class, UserChoiceTest.class,
		ObjectCacheTest.class, PermissionsTest.class, UserServiceTest.class, LdapAuthenticationTest.class,
		MarkdownUtilsTest.class, JGitUtilsTest.class, SyndicationUtilsTest.class,
		DiffUtilsTest.class, MetricUtilsTest.class, X509UtilsTest.class,
		GitBlitTest.class, FederationTests.class, RpcTests.class, GitServletTest.class, GitDaemonTest.class,
		SshDaemonTest.class, GroovyScriptTest.class, LuceneExecutorTest.class, RepositoryModelTest.class,
		FanoutServiceTest.class, Issue0259Test.class, Issue0271Test.class, HtpasswdAuthenticationTest.class,
		ModelUtilsTest.class, JnaUtilsTest.class, LdapSyncServiceTest.class, FileTicketServiceTest.class,
		BranchTicketServiceTest.class, RedisTicketServiceTest.class, AuthenticationManagerTest.class,
		SshKeysDispatcherTest.class, UITicketTest.class, PathUtilsTest.class, SshKerberosAuthenticationTest.class,
		GravatarTest.class, FilestoreManagerTest.class, FilestoreServletTest.class, TicketReferenceTest.class,
		GitblitInstanceIdTest.class })
public class GitBlitSuite {

	public static final File BASEFOLDER = new File("data");

	public static final File REPOSITORIES = new File("data/git");

	public static final File SETTINGS = new File("src/test/config/test-gitblit.properties");

	public static final File USERSCONF = new File("src/test/config/test-users.conf");

	private static final File AMBITION_REPO_SOURCE = new File("src/test/data/ambition.git");

	private static final File TICGIT_REPO_SOURCE = new File("src/test/data/ticgit.git");

	private static final File GITECTIVE_REPO_SOURCE = new File("src/test/data/gitective.git");

	private static final File HELLOWORLD_REPO_SOURCE = new File("src/test/data/hello-world.git");

	private static final File HELLOWORLD_REPO_PROPERTIES = new File("src/test/data/hello-world.properties");

	public static final FileSettings helloworldSettings = new FileSettings(HELLOWORLD_REPO_PROPERTIES.getAbsolutePath());

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

	public static Repository getHelloworldRepository() {
		return getRepository("helloworld.git");
	}

	public static Repository getTicgitRepository() {
		return getRepository("ticgit.git");
	}

	public static Repository getJGitRepository() {
		return getRepository("test/jgit.git");
	}

	public static Repository getAmbitionRepository() {
		return getRepository("test/ambition.git");
	}

	public static Repository getGitectiveRepository() {
		return getRepository("test/gitective.git");
	}

	public static Repository getTicketsTestRepository() {
		JGitUtils.createRepository(REPOSITORIES, "gb-tickets.git").close();
		return getRepository("gb-tickets.git");
	}

	private static Repository getRepository(String name) {
		try {
			File gitDir = FileKey.resolve(new File(REPOSITORIES, name), FS.DETECTED);
			Repository repository = new FileRepositoryBuilder().setGitDir(gitDir).build();
			return repository;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

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
						"--repositoriesFolder", GitBlitSuite.REPOSITORIES.getAbsolutePath(),
						"--userService", GitBlitSuite.USERSCONF.getAbsolutePath(),
						"--settings", GitBlitSuite.SETTINGS.getAbsolutePath(),
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
		File refChecks = new File(GitBlitSuite.REPOSITORIES, "refchecks");
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

		if (REPOSITORIES.exists() || REPOSITORIES.mkdirs()) {
			if (!HELLOWORLD_REPO_SOURCE.exists()) {
				unzipRepository(HELLOWORLD_REPO_SOURCE.getPath() + ".zip", HELLOWORLD_REPO_SOURCE.getParentFile());
			}
			if (!TICGIT_REPO_SOURCE.exists()) {
				unzipRepository(TICGIT_REPO_SOURCE.getPath() + ".zip", TICGIT_REPO_SOURCE.getParentFile());
			}
			if (!AMBITION_REPO_SOURCE.exists()) {
				unzipRepository(AMBITION_REPO_SOURCE.getPath() + ".zip", AMBITION_REPO_SOURCE.getParentFile());
			}
			if (!GITECTIVE_REPO_SOURCE.exists()) {
				unzipRepository(GITECTIVE_REPO_SOURCE.getPath() + ".zip", GITECTIVE_REPO_SOURCE.getParentFile());
			}
			cloneOrFetch("helloworld.git", HELLOWORLD_REPO_SOURCE.getAbsolutePath());
			cloneOrFetch("ticgit.git", TICGIT_REPO_SOURCE.getAbsolutePath());
			cloneOrFetch("test/jgit.git", "https://github.com/eclipse/jgit.git");
			cloneOrFetch("test/helloworld.git", HELLOWORLD_REPO_SOURCE.getAbsolutePath());
			cloneOrFetch("test/ambition.git", AMBITION_REPO_SOURCE.getAbsolutePath());
			cloneOrFetch("test/gitective.git", GITECTIVE_REPO_SOURCE.getAbsolutePath());

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

	private static void unzipRepository(String zippedRepo, File destDir) throws IOException {
		System.out.print("Unzipping " + zippedRepo + "... ");
		if (!destDir.exists()) {
			destDir.mkdir();
		}
		byte[] buffer = new byte[1024];
		ZipInputStream zis = new ZipInputStream(new FileInputStream(zippedRepo));
		ZipEntry zipEntry = zis.getNextEntry();
		while (zipEntry != null) {
			File newFile = newFile(destDir, zipEntry);
			if (zipEntry.isDirectory()) {
				newFile.mkdirs();
			}
			else {
				FileOutputStream fos = new FileOutputStream(newFile);
				int len;
				while ((len = zis.read(buffer)) > 0) {
					fos.write(buffer, 0, len);
				}
				fos.close();
			}
			zipEntry = zis.getNextEntry();
		}
		zis.closeEntry();
		zis.close();		
		System.out.println("done.");
	}

	private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
		File destFile = new File(destinationDir, zipEntry.getName());

		String destDirPath = destinationDir.getCanonicalPath();
		String destFilePath = destFile.getCanonicalPath();
		//guards against writing files to the file system outside of the target folder
		//to prevent Zip Slip exploit
		if (!destFilePath.startsWith(destDirPath + File.separator)) {
			throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
		}

		return destFile;
	}

}
