/*
 * Copyright 2013 gitblit.com.
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

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.JnaUtils;

/**
 *
 * @author Florian Zschocke
 */
public class JnaUtilsTest {

	@Test
	public void testGetgid() {
		if (JnaUtils.isWindows()) {
			try {
				JnaUtils.getFilemode(GitBlitTestConfig.REPOSITORIES);
			} catch(UnsupportedOperationException e) {}
		}
		else {
			int gid = JnaUtils.getgid();
			assertTrue(gid >= 0);
			int egid = JnaUtils.getegid();
			assertTrue(egid >= 0);
			assertTrue("Really? You're running unit tests as root?!", gid > 0);
			System.out.println("gid: " + gid + "  egid: " + egid);
		}
	}


	@Test
	public void testGetFilemode() throws IOException {
		if (JnaUtils.isWindows()) {
			try {
				JnaUtils.getFilemode(GitBlitTestConfig.REPOSITORIES);
			} catch(UnsupportedOperationException e) {}
		}
		else {
			String repositoryName = "NewJnaTestRepository.git";
			Repository repository = JGitUtils.createRepository(GitBlitTestConfig.REPOSITORIES, repositoryName);
			File folder = FileKey.resolve(new File(GitBlitTestConfig.REPOSITORIES, repositoryName), FS.DETECTED);
			assertTrue(folder.exists());

			int mode = JnaUtils.getFilemode(folder);
			assertTrue(mode > 0);
			assertEquals(JnaUtils.S_IFDIR, (mode & JnaUtils.S_IFMT)); // directory
			assertEquals(JnaUtils.S_IRUSR | JnaUtils.S_IWUSR | JnaUtils.S_IXUSR, (mode & JnaUtils.S_IRWXU)); // owner full access

			mode = JnaUtils.getFilemode(folder.getAbsolutePath() + "/config");
			assertTrue(mode > 0);
			assertEquals(JnaUtils.S_IFREG, (mode & JnaUtils.S_IFMT)); // directory
			assertEquals(JnaUtils.S_IRUSR | JnaUtils.S_IWUSR, (mode & JnaUtils.S_IRWXU)); // owner full access

			repository.close();
			RepositoryCache.close(repository);
			FileUtils.deleteDirectory(repository.getDirectory());
			}
	}


	@Test
	public void testSetFilemode() throws IOException {
		if (JnaUtils.isWindows()) {
			try {
				JnaUtils.getFilemode(GitBlitTestConfig.REPOSITORIES);
			} catch(UnsupportedOperationException e) {}
		}
		else {
			String repositoryName = "NewJnaTestRepository.git";
			Repository repository = JGitUtils.createRepository(GitBlitTestConfig.REPOSITORIES, repositoryName);
			File folder = FileKey.resolve(new File(GitBlitTestConfig.REPOSITORIES, repositoryName), FS.DETECTED);
			assertTrue(folder.exists());

			File path = new File(folder, "refs");
			int mode = JnaUtils.getFilemode(path);
			assertTrue(mode > 0);
			assertEquals(JnaUtils.S_IFDIR, (mode & JnaUtils.S_IFMT)); // directory
			assertEquals(JnaUtils.S_IRUSR | JnaUtils.S_IWUSR | JnaUtils.S_IXUSR, (mode & JnaUtils.S_IRWXU)); // owner full access

			mode |= JnaUtils.S_ISGID;
			mode |= JnaUtils.S_IRWXG;
			int ret = JnaUtils.setFilemode(path, mode);
			assertEquals(0, ret);
			mode = JnaUtils.getFilemode(path);
			assertTrue(mode > 0);
			assertEquals(JnaUtils.S_ISGID, (mode & JnaUtils.S_ISGID)); // set-gid-bit set
			assertEquals(JnaUtils.S_IRGRP | JnaUtils.S_IWGRP | JnaUtils.S_IXGRP, (mode & JnaUtils.S_IRWXG)); // group full access

			path = new File(folder, "config");
			mode = JnaUtils.getFilemode(path.getAbsolutePath());
			assertTrue(mode > 0);
			assertEquals(JnaUtils.S_IFREG, (mode & JnaUtils.S_IFMT)); // directory
			assertEquals(JnaUtils.S_IRUSR | JnaUtils.S_IWUSR, (mode & JnaUtils.S_IRWXU)); // owner full access

			mode |= (JnaUtils.S_IRGRP | JnaUtils.S_IWGRP);
			ret = JnaUtils.setFilemode(path.getAbsolutePath(), mode);
			assertEquals(0, ret);
			mode = JnaUtils.getFilemode(path.getAbsolutePath());
			assertTrue(mode > 0);
			assertEquals(JnaUtils.S_IRGRP | JnaUtils.S_IWGRP, (mode & JnaUtils.S_IRWXG)); // group full access

			repository.close();
			RepositoryCache.close(repository);
			FileUtils.deleteDirectory(repository.getDirectory());
		}
	}


	@Test
	public void testGetFilestat() {
		if (JnaUtils.isWindows()) {
			try {
				JnaUtils.getFilemode(GitBlitTestConfig.REPOSITORIES);
			} catch(UnsupportedOperationException e) {}
		}
		else {
			JnaUtils.Filestat stat = JnaUtils.getFilestat(GitBlitTestConfig.REPOSITORIES);
			assertNotNull(stat);
			assertTrue(stat.mode > 0);
			assertTrue(stat.uid > 0);
			assertTrue(stat.gid > 0);
		}
	}


}
