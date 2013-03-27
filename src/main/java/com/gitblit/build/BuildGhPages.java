/*
 * Copyright 2012 gitblit.com.
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
package com.gitblit.build;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.gitblit.models.RefModel;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;

/**
 * Creates or updates a gh-pages branch with the specified content.
 * 
 * @author James Moger
 * 
 */
public class BuildGhPages {

	public static void main(String[] args) {
		Params params = new Params();
		JCommander jc = new JCommander(params);
		try {
			jc.parse(args);
		} catch (ParameterException t) {
			System.err.println(t.getMessage());
			jc.usage();
		}

		File source = new File(params.sourceFolder);
		String ghpages = "refs/heads/gh-pages";
		try {			
			File gitDir = FileKey.resolve(new File(params.repositoryFolder), FS.DETECTED);
			Repository repository = new FileRepository(gitDir);

			RefModel issuesBranch = JGitUtils.getPagesBranch(repository);
			if (issuesBranch == null) {
				JGitUtils.createOrphanBranch(repository, "gh-pages", null);
			}

			System.out.println("Updating gh-pages branch...");
			ObjectId headId = repository.resolve(ghpages + "^{commit}");
			ObjectInserter odi = repository.newObjectInserter();
			try {
				// Create the in-memory index of the new/updated issue.
				DirCache index = createIndex(repository, headId, source, params.obliterate);
				ObjectId indexTreeId = index.writeTree(odi);

				// Create a commit object
				PersonIdent author = new PersonIdent("Gitblit", "gitblit@localhost");
				CommitBuilder commit = new CommitBuilder();
				commit.setAuthor(author);
				commit.setCommitter(author);
				commit.setEncoding(Constants.CHARACTER_ENCODING);
				commit.setMessage("updated pages");
				commit.setParentId(headId);
				commit.setTreeId(indexTreeId);

				// Insert the commit into the repository
				ObjectId commitId = odi.insert(commit);
				odi.flush();

				RevWalk revWalk = new RevWalk(repository);
				try {
					RevCommit revCommit = revWalk.parseCommit(commitId);
					RefUpdate ru = repository.updateRef(ghpages);
					ru.setNewObjectId(commitId);
					ru.setExpectedOldObjectId(headId);
					ru.setRefLogMessage("commit: " + revCommit.getShortMessage(), false);
					Result rc = ru.forceUpdate();
					switch (rc) {
					case NEW:
					case FORCED:
					case FAST_FORWARD:
						break;
					case REJECTED:
					case LOCK_FAILURE:
						throw new ConcurrentRefUpdateException(JGitText.get().couldNotLockHEAD,
								ru.getRef(), rc);
					default:
						throw new JGitInternalException(MessageFormat.format(
								JGitText.get().updatingRefFailed, ghpages, commitId.toString(), rc));
					}
				} finally {
					revWalk.release();
				}
			} finally {
				odi.release();
			}
			System.out.println("gh-pages updated.");
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	/**
	 * Creates an in-memory index of the issue change.
	 * 
	 * @param repo
	 * @param headId
	 * @param sourceFolder
	 * @param obliterate
	 *            if true the source folder tree is used as the new tree for
	 *            gh-pages and non-existent files are considered deleted
	 * @return an in-memory index
	 * @throws IOException
	 */
	private static DirCache createIndex(Repository repo, ObjectId headId, File sourceFolder,
			boolean obliterate) throws IOException {

		DirCache inCoreIndex = DirCache.newInCore();
		DirCacheBuilder dcBuilder = inCoreIndex.builder();
		ObjectInserter inserter = repo.newObjectInserter();

		try {
			// Add all files to the temporary index
			Set<String> ignorePaths = new TreeSet<String>();
			List<File> files = listFiles(sourceFolder);
			for (File file : files) {
				// create an index entry for the file
				final DirCacheEntry dcEntry = new DirCacheEntry(StringUtils.getRelativePath(
						sourceFolder.getPath(), file.getPath()));
				dcEntry.setLength(file.length());
				dcEntry.setLastModified(file.lastModified());
				dcEntry.setFileMode(FileMode.REGULAR_FILE);

				// add this entry to the ignore paths set
				ignorePaths.add(dcEntry.getPathString());

				// insert object
				InputStream inputStream = new FileInputStream(file);
				try {
					dcEntry.setObjectId(inserter.insert(Constants.OBJ_BLOB, file.length(),
							inputStream));
				} finally {
					inputStream.close();
				}

				// add to temporary in-core index
				dcBuilder.add(dcEntry);
			}

			if (!obliterate) {
				// Traverse HEAD to add all other paths
				TreeWalk treeWalk = new TreeWalk(repo);
				int hIdx = -1;
				if (headId != null)
					hIdx = treeWalk.addTree(new RevWalk(repo).parseTree(headId));
				treeWalk.setRecursive(true);

				while (treeWalk.next()) {
					String path = treeWalk.getPathString();
					CanonicalTreeParser hTree = null;
					if (hIdx != -1)
						hTree = treeWalk.getTree(hIdx, CanonicalTreeParser.class);
					if (!ignorePaths.contains(path)) {
						// add entries from HEAD for all other paths
						if (hTree != null) {
							// create a new DirCacheEntry with data retrieved
							// from
							// HEAD
							final DirCacheEntry dcEntry = new DirCacheEntry(path);
							dcEntry.setObjectId(hTree.getEntryObjectId());
							dcEntry.setFileMode(hTree.getEntryFileMode());

							// add to temporary in-core index
							dcBuilder.add(dcEntry);
						}
					}
				}

				// release the treewalk
				treeWalk.release();
			}
			
			// finish temporary in-core index used for this commit
			dcBuilder.finish();
		} finally {
			inserter.release();
		}
		return inCoreIndex;
	}

	private static List<File> listFiles(File folder) {
		List<File> files = new ArrayList<File>();
		for (File file : folder.listFiles()) {
			if (file.isDirectory()) {
				files.addAll(listFiles(file));
			} else {
				files.add(file);
			}
		}
		return files;
	}

	/**
	 * JCommander Parameters class for BuildGhPages.
	 */
	@Parameters(separators = " ")
	private static class Params {

		@Parameter(names = { "--sourceFolder" }, description = "Source folder for pages", required = true)
		public String sourceFolder;

		@Parameter(names = { "--repository" }, description = "Repository folder", required = true)
		public String repositoryFolder;

		@Parameter(names = { "--obliterate" }, description = "Replace gh-pages tree with only the content in your sourcefolder")
		public boolean obliterate;

	}
}
