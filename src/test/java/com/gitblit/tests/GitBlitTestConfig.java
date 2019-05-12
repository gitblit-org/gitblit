/*
 * Copyright 2019 Tue Ton
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FS;

import com.gitblit.FileSettings;
import com.gitblit.utils.JGitUtils;

public class GitBlitTestConfig {

	public static final File BASEFOLDER = new File("data");

	public static final File REPOSITORIES = new File("data/git");

	public static final File SETTINGS = new File("src/test/config/test-gitblit.properties");

	public static final File USERSCONF = new File("src/test/config/test-users.conf");

	private static final File AMBITION_REPO_SOURCE = new File("src/test/data/ambition.git");

	private static final File JGIT_REPO_SOURCE = new File("src/test/data/jgit.git");

	private static final File TICGIT_REPO_SOURCE = new File("src/test/data/ticgit.git");

	private static final File GITECTIVE_REPO_SOURCE = new File("src/test/data/gitective.git");

	private static final File HELLOWORLD_REPO_SOURCE = new File("src/test/data/hello-world.git");

	private static final File HELLOWORLD_REPO_PROPERTIES = new File("src/test/data/hello-world.properties");

	public static final FileSettings helloworldSettings = new FileSettings(HELLOWORLD_REPO_PROPERTIES.getAbsolutePath());

	public static void setUpGitRepositories() throws Exception {
		if (REPOSITORIES.exists() || REPOSITORIES.mkdirs()) {
			if (!HELLOWORLD_REPO_SOURCE.exists()) {
				unzipRepository(HELLOWORLD_REPO_SOURCE.getPath() + ".zip", HELLOWORLD_REPO_SOURCE.getParentFile());
			}
			if (!TICGIT_REPO_SOURCE.exists()) {
				unzipRepository(TICGIT_REPO_SOURCE.getPath() + ".zip", TICGIT_REPO_SOURCE.getParentFile());
			}
			if (!JGIT_REPO_SOURCE.exists()) {
				unzipRepository(JGIT_REPO_SOURCE.getPath() + ".zip", JGIT_REPO_SOURCE.getParentFile());
			}
			if (!AMBITION_REPO_SOURCE.exists()) {
				unzipRepository(AMBITION_REPO_SOURCE.getPath() + ".zip", AMBITION_REPO_SOURCE.getParentFile());
			}
			if (!GITECTIVE_REPO_SOURCE.exists()) {
				unzipRepository(GITECTIVE_REPO_SOURCE.getPath() + ".zip", GITECTIVE_REPO_SOURCE.getParentFile());
			}
			cloneOrFetch("helloworld.git", HELLOWORLD_REPO_SOURCE.getAbsolutePath());
			cloneOrFetch("ticgit.git", TICGIT_REPO_SOURCE.getAbsolutePath());
			cloneOrFetch("test/jgit.git", JGIT_REPO_SOURCE.getAbsolutePath());
			cloneOrFetch("test/helloworld.git", HELLOWORLD_REPO_SOURCE.getAbsolutePath());
			cloneOrFetch("test/ambition.git", AMBITION_REPO_SOURCE.getAbsolutePath());
			cloneOrFetch("test/gitective.git", GITECTIVE_REPO_SOURCE.getAbsolutePath());
		}
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
