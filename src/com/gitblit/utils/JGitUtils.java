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
package com.gitblit.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.OrTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.GitNote;
import com.gitblit.models.PathModel;
import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.models.RefModel;

public class JGitUtils {

	static final Logger LOGGER = LoggerFactory.getLogger(JGitUtils.class);

	/**
	 * Returns the displayable name of the person in the form "Real Name <email
	 * address>".  If the email address is empty, just "Real Name" is returned.
	 * 
	 * @param person
	 * @return "Real Name <email address>" or "Real Name"
	 */
	public static String getDisplayName(PersonIdent person) {
		if (StringUtils.isEmpty(person.getEmailAddress())) {
			return person.getName();
		}
		final StringBuilder r = new StringBuilder();
		r.append(person.getName());
		r.append(" <");
		r.append(person.getEmailAddress());
		r.append('>');
		return r.toString().trim();
	}

	/**
	 * Clone or Fetch a repository. If the local repository does not exist,
	 * clone is called. If the repository does exist, fetch is called. By
	 * default the clone/fetch retrieves the remote heads, tags, and notes.
	 * 
	 * @param repositoriesFolder
	 * @param name
	 * @param fromUrl
	 * @return FetchResult
	 * @throws Exception
	 */
	public static FetchResult cloneRepository(File repositoriesFolder, String name, String fromUrl)
			throws Exception {
		FetchResult result = null;
		if (!name.toLowerCase().endsWith(Constants.DOT_GIT_EXT)) {
			name += Constants.DOT_GIT_EXT;
		}
		File folder = new File(repositoriesFolder, name);
		if (folder.exists()) {
			File gitDir = FileKey.resolve(new File(repositoriesFolder, name), FS.DETECTED);
			FileRepository repository = new FileRepository(gitDir);
			result = fetchRepository(repository);
			repository.close();
		} else {
			CloneCommand clone = new CloneCommand();
			clone.setBare(true);
			clone.setCloneAllBranches(true);
			clone.setURI(fromUrl);
			clone.setDirectory(folder);
			clone.call();
			// Now we have to fetch because CloneCommand doesn't fetch
			// refs/notes nor does it allow manual RefSpec.
			File gitDir = FileKey.resolve(new File(repositoriesFolder, name), FS.DETECTED);
			FileRepository repository = new FileRepository(gitDir);
			result = fetchRepository(repository);
			repository.close();
		}
		return result;
	}

	/**
	 * Fetch updates from the remote repository. If refSpecs is unspecifed,
	 * remote heads, tags, and notes are retrieved.
	 * 
	 * @param repository
	 * @param refSpecs
	 * @return FetchResult
	 * @throws Exception
	 */
	public static FetchResult fetchRepository(Repository repository, RefSpec... refSpecs)
			throws Exception {
		Git git = new Git(repository);
		FetchCommand fetch = git.fetch();
		List<RefSpec> specs = new ArrayList<RefSpec>();
		if (refSpecs == null || refSpecs.length == 0) {
			specs.add(new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
			specs.add(new RefSpec("+refs/tags/*:refs/tags/*"));
			specs.add(new RefSpec("+refs/notes/*:refs/notes/*"));
		} else {
			specs.addAll(Arrays.asList(refSpecs));
		}
		fetch.setRefSpecs(specs);
		FetchResult result = fetch.call();
		repository.close();
		return result;
	}

	/**
	 * Creates a bare repository.
	 * 
	 * @param repositoriesFolder
	 * @param name
	 * @return Repository
	 */
	public static Repository createRepository(File repositoriesFolder, String name) {
		Git git = Git.init().setDirectory(new File(repositoriesFolder, name)).setBare(true).call();
		return git.getRepository();
	}

	/**
	 * Returns a list of repository names in the specified folder.
	 * 
	 * @param repositoriesFolder
	 * @param exportAll
	 *            if true, all repositories are listed. If false only the
	 *            repositories with a "git-daemon-export-ok" file are included
	 * @param searchSubfolders
	 *            recurse into subfolders to find grouped repositories
	 * @return list of repository names
	 */
	public static List<String> getRepositoryList(File repositoriesFolder, boolean exportAll,
			boolean searchSubfolders) {
		List<String> list = new ArrayList<String>();
		if (repositoriesFolder == null || !repositoriesFolder.exists()) {
			return list;
		}
		list.addAll(getRepositoryList(repositoriesFolder.getAbsolutePath(), repositoriesFolder,
				exportAll, searchSubfolders));
		Collections.sort(list);
		return list;
	}

	/**
	 * Recursive function to find git repositories.
	 * 
	 * @param basePath
	 *            basePath is stripped from the repository name as repositories
	 *            are relative to this path
	 * @param searchFolder
	 * @param exportAll
	 *            if true all repositories are listed. If false only the
	 *            repositories with a "git-daemon-export-ok" file are included
	 * @param searchSubfolders
	 *            recurse into subfolders to find grouped repositories
	 * @return
	 */
	private static List<String> getRepositoryList(String basePath, File searchFolder,
			boolean exportAll, boolean searchSubfolders) {
		List<String> list = new ArrayList<String>();
		for (File file : searchFolder.listFiles()) {
			if (file.isDirectory()) {
				File gitDir = FileKey.resolve(new File(searchFolder, file.getName()), FS.DETECTED);
				if (gitDir != null) {
					boolean exportRepository = exportAll
							|| new File(gitDir, "git-daemon-export-ok").exists();

					if (!exportRepository) {
						continue;
					}
					// determine repository name relative to base path
					String repository = StringUtils.getRelativePath(basePath,
							file.getAbsolutePath());
					list.add(repository);
				} else if (searchSubfolders) {
					// look for repositories in subfolders
					list.addAll(getRepositoryList(basePath, file, exportAll, searchSubfolders));
				}
			}
		}
		return list;
	}

	/**
	 * Returns the first commit on a branch. If the repository does not exist or
	 * is empty, null is returned.
	 * 
	 * @param repository
	 * @param branch
	 *            if unspecified, HEAD is assumed.
	 * @return RevCommit
	 */
	public static RevCommit getFirstCommit(Repository repository, String branch) {
		if (!hasCommits(repository)) {
			return null;
		}
		if (StringUtils.isEmpty(branch)) {
			branch = Constants.HEAD;
		}
		RevCommit commit = null;
		try {
			RevWalk walk = new RevWalk(repository);
			walk.sort(RevSort.REVERSE);
			RevCommit head = walk.parseCommit(repository.resolve(branch));
			walk.markStart(head);
			commit = walk.next();
			walk.dispose();
		} catch (Throwable t) {
			LOGGER.error("Failed to determine first commit", t);
		}
		return commit;
	}

	/**
	 * Returns the date of the first commit on a branch. If the repository does
	 * not exist, Date(0) is returned. If the repository does exist bit is
	 * empty, the last modified date of the repository folder is returned.
	 * 
	 * @param repository
	 * @param branch
	 *            if unspecified, HEAD is assumed.
	 * @return Date of the first commit on a branch
	 */
	public static Date getFirstChange(Repository repository, String branch) {
		RevCommit commit = getFirstCommit(repository, branch);
		if (commit == null) {
			if (repository == null || !repository.getDirectory().exists()) {
				return new Date(0);
			}
			// fresh repository
			return new Date(repository.getDirectory().lastModified());
		}
		return getCommitDate(commit);
	}

	/**
	 * Determine if a repository has any commits. This is determined by checking
	 * for 1 or more heads.
	 * 
	 * @param repository
	 * @return true if the repository has commits
	 */
	public static boolean hasCommits(Repository repository) {
		if (repository != null && repository.getDirectory().exists()) {
			return (new File(repository.getDirectory(), "objects/info").list().length > 0)
					|| (new File(repository.getDirectory(), "objects/pack").list().length > 0);
		}
		return false;
	}

	/**
	 * Returns the date of the most recent commit on a branch. If the repository
	 * does not exist Date(0) is returned. If it does exist but is empty, the
	 * last modified date of the repository folder is returned.
	 * 
	 * @param repository
	 * @param branch
	 *            if unspecified, HEAD is assumed.
	 * @return
	 */
	public static Date getLastChange(Repository repository, String branch) {
		if (!hasCommits(repository)) {
			// null repository
			if (repository == null) {
				return new Date(0);
			}
			// fresh repository
			return new Date(repository.getDirectory().lastModified());
		}
		if (StringUtils.isEmpty(branch)) {
			branch = Constants.HEAD;
		}
		RevCommit commit = getCommit(repository, branch);
		return getCommitDate(commit);
	}

	/**
	 * Retrieves a Java Date from a Git commit.
	 * 
	 * @param commit
	 * @return date of the commit
	 */
	public static Date getCommitDate(RevCommit commit) {
		return new Date(commit.getCommitTime() * 1000L);
	}

	/**
	 * Returns the specified commit from the repository. If the repository does
	 * not exist or is empty, null is returned.
	 * 
	 * @param repository
	 * @param objectId
	 *            if unspecified, HEAD is assumed.
	 * @return RevCommit
	 */
	public static RevCommit getCommit(Repository repository, String objectId) {
		if (!hasCommits(repository)) {
			return null;
		}
		RevCommit commit = null;
		try {
			if (StringUtils.isEmpty(objectId)) {
				objectId = Constants.HEAD;
			}
			ObjectId object = repository.resolve(objectId);
			RevWalk walk = new RevWalk(repository);
			RevCommit rev = walk.parseCommit(object);
			commit = rev;
			walk.dispose();
		} catch (Throwable t) {
			LOGGER.error("Failed to get commit " + objectId, t);
		}
		return commit;
	}

	/**
	 * Retrieves the raw byte content of a file in the specified tree.
	 * 
	 * @param repository
	 * @param tree
	 *            if null, the RevTree from HEAD is assumed.
	 * @param path
	 * @return content as a byte []
	 */
	public static byte[] getByteContent(Repository repository, RevTree tree, final String path) {
		RevWalk rw = new RevWalk(repository);
		TreeWalk tw = new TreeWalk(repository);
		tw.setFilter(PathFilterGroup.createFromStrings(Collections.singleton(path)));
		byte[] content = null;
		try {
			if (tree == null) {
				ObjectId object = repository.resolve(Constants.HEAD);
				RevCommit commit = rw.parseCommit(object);
				tree = commit.getTree();
			}
			tw.reset(tree);
			while (tw.next()) {
				if (tw.isSubtree() && !path.equals(tw.getPathString())) {
					tw.enterSubtree();
					continue;
				}
				ObjectId entid = tw.getObjectId(0);
				FileMode entmode = tw.getFileMode(0);
				RevObject ro = rw.lookupAny(entid, entmode.getObjectType());
				rw.parseBody(ro);
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				ObjectLoader ldr = repository.open(ro.getId(), Constants.OBJ_BLOB);
				byte[] tmp = new byte[4096];
				InputStream in = ldr.openStream();
				int n;
				while ((n = in.read(tmp)) > 0) {
					os.write(tmp, 0, n);
				}
				in.close();
				content = os.toByteArray();
			}
		} catch (Throwable t) {
			LOGGER.error("Can't find " + path + " in tree " + tree.name(), t);
		} finally {
			rw.dispose();
			tw.release();
		}
		return content;
	}

	/**
	 * Returns the UTF-8 string content of a file in the specified tree.
	 * 
	 * @param repository
	 * @param tree
	 *            if null, the RevTree from HEAD is assumed.
	 * @param blobPath
	 * @return UTF-8 string content
	 */
	public static String getStringContent(Repository repository, RevTree tree, String blobPath) {
		byte[] content = getByteContent(repository, tree, blobPath);
		if (content == null) {
			return null;
		}
		return new String(content, Charset.forName(Constants.CHARACTER_ENCODING));
	}

	/**
	 * Gets the raw byte content of the specified blob object.
	 * 
	 * @param repository
	 * @param objectId
	 * @return byte [] blob content
	 */
	public static byte[] getByteContent(Repository repository, String objectId) {
		RevWalk rw = new RevWalk(repository);
		byte[] content = null;
		try {
			RevBlob blob = rw.lookupBlob(ObjectId.fromString(objectId));
			rw.parseBody(blob);
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			ObjectLoader ldr = repository.open(blob.getId(), Constants.OBJ_BLOB);
			byte[] tmp = new byte[4096];
			InputStream in = ldr.openStream();
			int n;
			while ((n = in.read(tmp)) > 0) {
				os.write(tmp, 0, n);
			}
			in.close();
			content = os.toByteArray();
		} catch (Throwable t) {
			LOGGER.error("Can't find blob " + objectId, t);
		} finally {
			rw.dispose();
		}
		return content;
	}

	/**
	 * Gets the UTF-8 string content of the blob specified by objectId.
	 * 
	 * @param repository
	 * @param objectId
	 * @return UTF-8 string content
	 */
	public static String getStringContent(Repository repository, String objectId) {
		byte[] content = getByteContent(repository, objectId);
		if (content == null) {
			return null;
		}
		return new String(content, Charset.forName(Constants.CHARACTER_ENCODING));
	}

	/**
	 * Returns the list of files in the specified folder at the specified
	 * commit. If the repository does not exist or is empty, an empty list is
	 * returned.
	 * 
	 * @param repository
	 * @param path
	 *            if unspecified, root folder is assumed.
	 * @param commit
	 *            if null, HEAD is assumed.
	 * @return list of files in specified path
	 */
	public static List<PathModel> getFilesInPath(Repository repository, String path,
			RevCommit commit) {
		List<PathModel> list = new ArrayList<PathModel>();
		if (!hasCommits(repository)) {
			return list;
		}
		if (commit == null) {
			commit = getCommit(repository, Constants.HEAD);
		}
		final TreeWalk tw = new TreeWalk(repository);
		try {
			tw.addTree(commit.getTree());
			if (!StringUtils.isEmpty(path)) {
				PathFilter f = PathFilter.create(path);
				tw.setFilter(f);
				tw.setRecursive(false);
				boolean foundFolder = false;
				while (tw.next()) {
					if (!foundFolder && tw.isSubtree()) {
						tw.enterSubtree();
					}
					if (tw.getPathString().equals(path)) {
						foundFolder = true;
						continue;
					}
					if (foundFolder) {
						list.add(getPathModel(tw, path, commit));
					}
				}
			} else {
				tw.setRecursive(false);
				while (tw.next()) {
					list.add(getPathModel(tw, null, commit));
				}
			}
		} catch (IOException e) {
			LOGGER.error("Failed to get files for commit " + commit.getName(), e);
		} finally {
			tw.release();
		}
		Collections.sort(list);
		return list;
	}

	/**
	 * Returns the list of files changed in a specified commit. If the
	 * repository does not exist or is empty, an empty list is returned.
	 * 
	 * @param repository
	 * @param commit
	 *            if null, HEAD is assumed.
	 * @return list of files changed in a commit
	 */
	public static List<PathChangeModel> getFilesInCommit(Repository repository, RevCommit commit) {
		List<PathChangeModel> list = new ArrayList<PathChangeModel>();
		if (!hasCommits(repository)) {
			return list;
		}
		RevWalk rw = new RevWalk(repository);
		try {
			if (commit == null) {
				ObjectId object = repository.resolve(Constants.HEAD);
				commit = rw.parseCommit(object);
			}

			if (commit.getParentCount() == 0) {
				TreeWalk tw = new TreeWalk(repository);
				tw.reset();
				tw.setRecursive(true);
				tw.addTree(commit.getTree());
				while (tw.next()) {
					list.add(new PathChangeModel(tw.getPathString(), tw.getPathString(), 0, tw
							.getRawMode(0), commit.getId().getName(), ChangeType.ADD));
				}
				tw.release();
			} else {
				RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
				DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
				df.setRepository(repository);
				df.setDiffComparator(RawTextComparator.DEFAULT);
				df.setDetectRenames(true);
				List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
				for (DiffEntry diff : diffs) {
					if (diff.getChangeType().equals(ChangeType.DELETE)) {
						list.add(new PathChangeModel(diff.getOldPath(), diff.getOldPath(), 0, diff
								.getNewMode().getBits(), commit.getId().getName(), diff
								.getChangeType()));
					} else {
						list.add(new PathChangeModel(diff.getNewPath(), diff.getNewPath(), 0, diff
								.getNewMode().getBits(), commit.getId().getName(), diff
								.getChangeType()));
					}
				}
			}
		} catch (Throwable t) {
			LOGGER.error("failed to determine files in commit!", t);
		} finally {
			rw.dispose();
		}
		return list;
	}

	/**
	 * Returns the list of files in the repository that match one of the
	 * specified extensions. This is a CASE-SENSITIVE search. If the repository
	 * does not exist or is empty, an empty list is returned.
	 * 
	 * @param repository
	 * @param extensions
	 * @return list of files in repository with a matching extension
	 */
	public static List<PathModel> getDocuments(Repository repository, List<String> extensions) {
		List<PathModel> list = new ArrayList<PathModel>();
		if (!hasCommits(repository)) {
			return list;
		}
		RevCommit commit = getCommit(repository, Constants.HEAD);
		final TreeWalk tw = new TreeWalk(repository);
		try {
			tw.addTree(commit.getTree());
			if (extensions != null && extensions.size() > 0) {
				Collection<TreeFilter> suffixFilters = new ArrayList<TreeFilter>();
				for (String extension : extensions) {
					if (extension.charAt(0) == '.') {
						suffixFilters.add(PathSuffixFilter.create("\\" + extension));
					} else {
						// escape the . since this is a regexp filter
						suffixFilters.add(PathSuffixFilter.create("\\." + extension));
					}
				}
				TreeFilter filter = OrTreeFilter.create(suffixFilters);
				tw.setFilter(filter);
				tw.setRecursive(true);
			}
			while (tw.next()) {
				list.add(getPathModel(tw, null, commit));
			}
		} catch (IOException e) {
			LOGGER.error("Failed to get documents for commit " + commit.getName(), e);
		} finally {
			tw.release();
		}
		Collections.sort(list);
		return list;
	}

	/**
	 * Returns a path model of the current file in the treewalk.
	 * 
	 * @param tw
	 * @param basePath
	 * @param commit
	 * @return a path model of the current file in the treewalk
	 */
	private static PathModel getPathModel(TreeWalk tw, String basePath, RevCommit commit) {
		String name;
		long size = 0;
		if (StringUtils.isEmpty(basePath)) {
			name = tw.getPathString();
		} else {
			name = tw.getPathString().substring(basePath.length() + 1);
		}
		try {
			if (!tw.isSubtree()) {
				size = tw.getObjectReader().getObjectSize(tw.getObjectId(0), Constants.OBJ_BLOB);
			}
		} catch (Throwable t) {
			LOGGER.error("Failed to retrieve blob size", t);
		}
		return new PathModel(name, tw.getPathString(), size, tw.getFileMode(0).getBits(),
				commit.getName());
	}

	/**
	 * Returns a permissions representation of the mode bits.
	 * 
	 * @param mode
	 * @return string representation of the mode bits
	 */
	public static String getPermissionsFromMode(int mode) {
		if (FileMode.TREE.equals(mode)) {
			return "drwxr-xr-x";
		} else if (FileMode.REGULAR_FILE.equals(mode)) {
			return "-rw-r--r--";
		} else if (FileMode.EXECUTABLE_FILE.equals(mode)) {
			return "-rwxr-xr-x";
		} else if (FileMode.SYMLINK.equals(mode)) {
			// FIXME symlink permissions
			return "symlink";
		} else if (FileMode.GITLINK.equals(mode)) {
			// FIXME gitlink permissions
			return "gitlink";
		}
		// FIXME missing permissions
		return "missing";
	}

	/**
	 * Returns a list of commits starting from HEAD and working backwards.
	 * 
	 * @param repository
	 * @param maxCount
	 *            if < 0, all commits for the repository are returned.
	 * @return list of commits
	 */
	public static List<RevCommit> getRevLog(Repository repository, int maxCount) {
		return getRevLog(repository, Constants.HEAD, 0, maxCount);
	}

	/**
	 * Returns a list of commits starting from the specified objectId using an
	 * offset and maxCount for paging. This is similar to LIMIT n OFFSET p in
	 * SQL. If the repository does not exist or is empty, an empty list is
	 * returned.
	 * 
	 * @param repository
	 * @param objectId
	 *            if unspecified, HEAD is assumed.
	 * @param offset
	 * @param maxCount
	 *            if < 0, all commits are returned.
	 * @return a paged list of commits
	 */
	public static List<RevCommit> getRevLog(Repository repository, String objectId, int offset,
			int maxCount) {
		return getRevLog(repository, objectId, null, offset, maxCount);
	}

	/**
	 * Returns a list of commits for the repository or a path within the
	 * repository. Caller may specify ending revision with objectId. Caller may
	 * specify offset and maxCount to achieve pagination of results. If the
	 * repository does not exist or is empty, an empty list is returned.
	 * 
	 * @param repository
	 * @param objectId
	 *            if unspecified, HEAD is assumed.
	 * @param path
	 *            if unspecified, commits for repository are returned. If
	 *            specified, commits for the path are returned.
	 * @param offset
	 * @param maxCount
	 *            if < 0, all commits are returned.
	 * @return a paged list of commits
	 */
	public static List<RevCommit> getRevLog(Repository repository, String objectId, String path,
			int offset, int maxCount) {
		List<RevCommit> list = new ArrayList<RevCommit>();
		if (maxCount == 0) {
			return list;
		}
		if (!hasCommits(repository)) {
			return list;
		}
		try {
			if (StringUtils.isEmpty(objectId)) {
				objectId = Constants.HEAD;
			}
			RevWalk rw = new RevWalk(repository);
			ObjectId object = repository.resolve(objectId);
			rw.markStart(rw.parseCommit(object));
			if (!StringUtils.isEmpty(path)) {
				TreeFilter filter = AndTreeFilter.create(
						PathFilterGroup.createFromStrings(Collections.singleton(path)),
						TreeFilter.ANY_DIFF);
				rw.setTreeFilter(filter);
			}
			Iterable<RevCommit> revlog = rw;
			if (offset > 0) {
				int count = 0;
				for (RevCommit rev : revlog) {
					count++;
					if (count > offset) {
						list.add(rev);
						if (maxCount > 0 && list.size() == maxCount) {
							break;
						}
					}
				}
			} else {
				for (RevCommit rev : revlog) {
					list.add(rev);
					if (maxCount > 0 && list.size() == maxCount) {
						break;
					}
				}
			}
			rw.dispose();
		} catch (Throwable t) {
			LOGGER.error("Failed to get revlog", t);
		}
		return list;
	}

	public static enum SearchType {
		AUTHOR, COMMITTER, COMMIT;

		public static SearchType forName(String name) {
			for (SearchType type : values()) {
				if (type.name().equalsIgnoreCase(name)) {
					return type;
				}
			}
			return COMMIT;
		}

		@Override
		public String toString() {
			return name().toLowerCase();
		}
	}

	/**
	 * Search the commit history for a case-insensitive match to the value.
	 * Search results require a specified SearchType of AUTHOR, COMMITTER, or
	 * COMMIT. Results may be paginated using offset and maxCount. If the
	 * repository does not exist or is empty, an empty list is returned.
	 * 
	 * @param repository
	 * @param objectId
	 *            if unspecified, HEAD is assumed.
	 * @param value
	 * @param type
	 *            AUTHOR, COMMITTER, COMMIT
	 * @param offset
	 * @param maxCount
	 *            if < 0, all matches are returned
	 * @return matching list of commits
	 */
	public static List<RevCommit> searchRevlogs(Repository repository, String objectId,
			String value, final SearchType type, int offset, int maxCount) {
		final String lcValue = value.toLowerCase();
		List<RevCommit> list = new ArrayList<RevCommit>();
		if (maxCount == 0) {
			return list;
		}
		if (!hasCommits(repository)) {
			return list;
		}
		try {
			if (StringUtils.isEmpty(objectId)) {
				objectId = Constants.HEAD;
			}
			RevWalk rw = new RevWalk(repository);
			rw.setRevFilter(new RevFilter() {

				@Override
				public RevFilter clone() {
					// FindBugs complains about this method name.
					// This is part of JGit design and unrelated to Cloneable.
					return this;
				}

				@Override
				public boolean include(RevWalk walker, RevCommit commit) throws StopWalkException,
						MissingObjectException, IncorrectObjectTypeException, IOException {
					boolean include = false;
					switch (type) {
					case AUTHOR:
						include = (commit.getAuthorIdent().getName().toLowerCase().indexOf(lcValue) > -1)
								|| (commit.getAuthorIdent().getEmailAddress().toLowerCase()
										.indexOf(lcValue) > -1);
						break;
					case COMMITTER:
						include = (commit.getCommitterIdent().getName().toLowerCase()
								.indexOf(lcValue) > -1)
								|| (commit.getCommitterIdent().getEmailAddress().toLowerCase()
										.indexOf(lcValue) > -1);
						break;
					case COMMIT:
						include = commit.getFullMessage().toLowerCase().indexOf(lcValue) > -1;
						break;
					}
					return include;
				}

			});
			ObjectId object = repository.resolve(objectId);
			rw.markStart(rw.parseCommit(object));
			Iterable<RevCommit> revlog = rw;
			if (offset > 0) {
				int count = 0;
				for (RevCommit rev : revlog) {
					count++;
					if (count > offset) {
						list.add(rev);
						if (maxCount > 0 && list.size() == maxCount) {
							break;
						}
					}
				}
			} else {
				for (RevCommit rev : revlog) {
					list.add(rev);
					if (maxCount > 0 && list.size() == maxCount) {
						break;
					}
				}
			}
			rw.dispose();
		} catch (Throwable t) {
			LOGGER.error("Failed to search revlogs", t);
		}
		return list;
	}

	/**
	 * Returns all refs grouped by their associated object id.
	 * 
	 * @param repository
	 * @return all refs grouped by their referenced object id
	 */
	public static Map<ObjectId, List<RefModel>> getAllRefs(Repository repository) {
		List<RefModel> list = getRefs(repository, org.eclipse.jgit.lib.RefDatabase.ALL, true, -1);
		Map<ObjectId, List<RefModel>> refs = new HashMap<ObjectId, List<RefModel>>();
		for (RefModel ref : list) {
			ObjectId objectid = ref.getReferencedObjectId();
			if (!refs.containsKey(objectid)) {
				refs.put(objectid, new ArrayList<RefModel>());
			}
			refs.get(objectid).add(ref);
		}
		return refs;
	}

	/**
	 * Returns the list of tags in the repository. If repository does not exist
	 * or is empty, an empty list is returned.
	 * 
	 * @param repository
	 * @param fullName
	 *            if true, /refs/tags/yadayadayada is returned. If false,
	 *            yadayadayada is returned.
	 * @param maxCount
	 *            if < 0, all tags are returned
	 * @return list of tags
	 */
	public static List<RefModel> getTags(Repository repository, boolean fullName, int maxCount) {
		return getRefs(repository, Constants.R_TAGS, fullName, maxCount);
	}

	/**
	 * Returns the list of local branches in the repository. If repository does
	 * not exist or is empty, an empty list is returned.
	 * 
	 * @param repository
	 * @param fullName
	 *            if true, /refs/heads/yadayadayada is returned. If false,
	 *            yadayadayada is returned.
	 * @param maxCount
	 *            if < 0, all local branches are returned
	 * @return list of local branches
	 */
	public static List<RefModel> getLocalBranches(Repository repository, boolean fullName,
			int maxCount) {
		return getRefs(repository, Constants.R_HEADS, fullName, maxCount);
	}

	/**
	 * Returns the list of remote branches in the repository. If repository does
	 * not exist or is empty, an empty list is returned.
	 * 
	 * @param repository
	 * @param fullName
	 *            if true, /refs/remotes/yadayadayada is returned. If false,
	 *            yadayadayada is returned.
	 * @param maxCount
	 *            if < 0, all remote branches are returned
	 * @return list of remote branches
	 */
	public static List<RefModel> getRemoteBranches(Repository repository, boolean fullName,
			int maxCount) {
		return getRefs(repository, Constants.R_REMOTES, fullName, maxCount);
	}

	/**
	 * Returns the list of note branches. If repository does not exist or is
	 * empty, an empty list is returned.
	 * 
	 * @param repository
	 * @param fullName
	 *            if true, /refs/notes/yadayadayada is returned. If false,
	 *            yadayadayada is returned.
	 * @param maxCount
	 *            if < 0, all note branches are returned
	 * @return list of note branches
	 */
	public static List<RefModel> getNoteBranches(Repository repository, boolean fullName,
			int maxCount) {
		return getRefs(repository, Constants.R_NOTES, fullName, maxCount);
	}

	/**
	 * Returns a list of references in the repository matching "refs". If the
	 * repository is null or empty, an empty list is returned.
	 * 
	 * @param repository
	 * @param refs
	 *            if unspecified, all refs are returned
	 * @param fullName
	 *            if true, /refs/something/yadayadayada is returned. If false,
	 *            yadayadayada is returned.
	 * @param maxCount
	 *            if < 0, all references are returned
	 * @return list of references
	 */
	private static List<RefModel> getRefs(Repository repository, String refs, boolean fullName,
			int maxCount) {
		List<RefModel> list = new ArrayList<RefModel>();
		if (maxCount == 0) {
			return list;
		}
		if (!hasCommits(repository)) {
			return list;
		}
		try {
			Map<String, Ref> map = repository.getRefDatabase().getRefs(refs);
			RevWalk rw = new RevWalk(repository);
			for (Entry<String, Ref> entry : map.entrySet()) {
				Ref ref = entry.getValue();
				RevObject object = rw.parseAny(ref.getObjectId());
				String name = entry.getKey();
				if (fullName && !StringUtils.isEmpty(refs)) {
					name = refs + name;
				}
				list.add(new RefModel(name, ref, object));
			}
			rw.dispose();
			Collections.sort(list);
			Collections.reverse(list);
			if (maxCount > 0 && list.size() > maxCount) {
				list = new ArrayList<RefModel>(list.subList(0, maxCount));
			}
		} catch (IOException e) {
			LOGGER.error("Failed to retrieve " + refs, e);
		}
		return list;
	}

	/**
	 * Returns the list of notes entered about the commit from the refs/notes
	 * namespace. If the repository does not exist or is empty, an empty list is
	 * returned.
	 * 
	 * @param repository
	 * @param commit
	 * @return list of notes
	 */
	public static List<GitNote> getNotesOnCommit(Repository repository, RevCommit commit) {
		List<GitNote> list = new ArrayList<GitNote>();
		if (!hasCommits(repository)) {
			return list;
		}
		List<RefModel> noteBranches = getNoteBranches(repository, true, -1);
		for (RefModel notesRef : noteBranches) {
			RevTree notesTree = JGitUtils.getCommit(repository, notesRef.getName()).getTree();
			StringBuilder sb = new StringBuilder(commit.getName());
			sb.insert(2, '/');
			String notePath = sb.toString();
			String text = getStringContent(repository, notesTree, notePath);
			if (!StringUtils.isEmpty(text)) {
				List<RevCommit> history = getRevLog(repository, notesRef.getName(), notePath, 0, -1);
				RefModel noteRef = new RefModel(notesRef.displayName, null, history.get(history
						.size() - 1));
				GitNote gitNote = new GitNote(noteRef, text);
				list.add(gitNote);
			}
		}
		return list;
	}

	/**
	 * Returns a StoredConfig object for the repository.
	 * 
	 * @param repository
	 * @return the StoredConfig of the repository
	 */
	public static StoredConfig readConfig(Repository repository) {
		StoredConfig c = repository.getConfig();
		try {
			c.load();
		} catch (ConfigInvalidException cex) {
			LOGGER.error("Repository configuration is invalid!", cex);
		} catch (IOException cex) {
			LOGGER.error("Could not open repository configuration!", cex);
		}
		return c;
	}

	/**
	 * Zips the contents of the tree at the (optionally) specified revision and
	 * the (optionally) specified basepath to the supplied outputstream.
	 * 
	 * @param repository
	 * @param basePath
	 *            if unspecified, entire repository is assumed.
	 * @param objectId
	 *            if unspecified, HEAD is assumed.
	 * @param os
	 * @return true if repository was successfully zipped to supplied output
	 *         stream
	 */
	public static boolean zip(Repository repository, String basePath, String objectId,
			OutputStream os) {
		RevCommit commit = getCommit(repository, objectId);
		if (commit == null) {
			return false;
		}
		boolean success = false;
		RevWalk rw = new RevWalk(repository);
		TreeWalk tw = new TreeWalk(repository);
		try {
			tw.addTree(commit.getTree());
			ZipOutputStream zos = new ZipOutputStream(os);
			zos.setComment("Generated by Gitblit");
			if (!StringUtils.isEmpty(basePath)) {
				PathFilter f = PathFilter.create(basePath);
				tw.setFilter(f);
			}
			tw.setRecursive(true);
			while (tw.next()) {
				ZipEntry entry = new ZipEntry(tw.getPathString());
				entry.setSize(tw.getObjectReader().getObjectSize(tw.getObjectId(0),
						Constants.OBJ_BLOB));
				entry.setComment(commit.getName());
				zos.putNextEntry(entry);

				ObjectId entid = tw.getObjectId(0);
				FileMode entmode = tw.getFileMode(0);
				RevBlob blob = (RevBlob) rw.lookupAny(entid, entmode.getObjectType());
				rw.parseBody(blob);

				ObjectLoader ldr = repository.open(blob.getId(), Constants.OBJ_BLOB);
				byte[] tmp = new byte[4096];
				InputStream in = ldr.openStream();
				int n;
				while ((n = in.read(tmp)) > 0) {
					zos.write(tmp, 0, n);
				}
				in.close();
			}
			zos.finish();
			success = true;
		} catch (IOException e) {
			LOGGER.error("Failed to zip files from commit " + commit.getName(), e);
		} finally {
			tw.release();
			rw.dispose();
		}
		return success;
	}
}
