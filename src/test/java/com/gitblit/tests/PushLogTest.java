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

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

import com.gitblit.models.RefLogEntry;
import com.gitblit.utils.RefLogUtils;

public class PushLogTest extends GitblitUnitTest {

	@Test
	public void testPushLog() throws IOException {
		String name = "~james/helloworld.git";
		File gitDir = FileKey.resolve(new File(GitBlitSuite.REPOSITORIES, name), FS.DETECTED);
		Repository repository = new FileRepositoryBuilder().setGitDir(gitDir).build();
		List<RefLogEntry> pushes = RefLogUtils.getRefLog(name, repository);
		GitBlitSuite.close(repository);
	}
}