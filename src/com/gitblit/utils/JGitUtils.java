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

	public static FetchResult cloneRepository(File repositoriesFolder, String name, String fromUrl) throws Exception {
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

	public static Repository createRepository(File repositoriesFolder, String name) {
		Git git = Git.init().setDirectory(new File(repositoriesFolder, name)).setBare(true).call();
		return git.getRepository();
	}

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

	public static RevCommit getFirstCommit(Repository r, String branch) {
		if (!hasCommits(r)) {
			return null;
		}
		if (StringUtils.isEmpty(branch)) {
			branch = Constants.HEAD;
		}
		RevCommit commit = null;
		try {
			RevWalk walk = new RevWalk(r);
			walk.sort(RevSort.REVERSE);
			RevCommit head = walk.parseCommit(r.resolve(branch));
			walk.markStart(head);
			commit = walk.next();
			walk.dispose();
		} catch (Throwable t) {
			LOGGER.error("Failed to determine first commit", t);
		}
		return commit;
	}

	public static Date getFirstChange(Repository r, String branch) {
		RevCommit commit = getFirstCommit(r, branch);
		if (commit == null) {
			if (r == null || !r.getDirectory().exists()) {
				return new Date(0);
			}
			// fresh repository
			return new Date(r.getDirectory().lastModified());
		}
		return getCommitDate(commit);
	}

	public static boolean hasCommits(Repository r) {
		if (r != null && r.getDirectory().exists()) {
			return new File(r.getDirectory(), Constants.R_HEADS).list().length > 0;
		}
		return false;
	}

	public static Date getLastChange(Repository r) {
		if (!hasCommits(r)) {
			// null repository
			if (r == null) {
				return new Date(0);
			}
			// fresh repository
			return new Date(r.getDirectory().lastModified());
		}
		RevCommit commit = getCommit(r, Constants.HEAD);
		return getCommitDate(commit);
	}

	public static Date getCommitDate(RevCommit commit) {
		return new Date(commit.getCommitTime() * 1000L);
	}

	public static RevCommit getCommit(Repository r, String objectId) {
		if (!hasCommits(r)) {
			return null;
		}
		RevCommit commit = null;
		try {
			if (StringUtils.isEmpty(objectId)) {
				objectId = Constants.HEAD;
			}
			ObjectId object = r.resolve(objectId);
			RevWalk walk = new RevWalk(r);
			RevCommit rev = walk.parseCommit(object);
			commit = rev;
			walk.dispose();
		} catch (Throwable t) {
			LOGGER.error("Failed to get commit " + objectId, t);
		}
		return commit;
	}

	public static Map<ObjectId, List<RefModel>> getAllRefs(Repository r) {
		List<RefModel> list = getRefs(r, org.eclipse.jgit.lib.RefDatabase.ALL, true, -1);
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

	public static byte[] getByteContent(Repository r, RevTree tree, final String path) {
		RevWalk rw = new RevWalk(r);
		TreeWalk tw = new TreeWalk(r);
		tw.setFilter(PathFilterGroup.createFromStrings(Collections.singleton(path)));
		byte[] content = null;
		try {
			if (tree == null) {
				ObjectId object = r.resolve(Constants.HEAD);
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
				ObjectLoader ldr = r.open(ro.getId(), Constants.OBJ_BLOB);
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

	public static String getStringContent(Repository r, RevTree tree, String blobPath) {
		byte[] content = getByteContent(r, tree, blobPath);
		if (content == null) {
			return null;
		}
		return new String(content, Charset.forName(Constants.CHARACTER_ENCODING));
	}

	public static byte[] getByteContent(Repository r, String objectId) {
		RevWalk rw = new RevWalk(r);
		byte[] content = null;
		try {
			RevBlob blob = rw.lookupBlob(ObjectId.fromString(objectId));
			rw.parseBody(blob);
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			ObjectLoader ldr = r.open(blob.getId(), Constants.OBJ_BLOB);
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

	public static String getStringContent(Repository r, String objectId) {
		byte[] content = getByteContent(r, objectId);
		if (content == null) {
			return null;
		}
		return new String(content, Charset.forName(Constants.CHARACTER_ENCODING));
	}

	public static List<PathModel> getFilesInPath(Repository r, String basePath, RevCommit commit) {
		List<PathModel> list = new ArrayList<PathModel>();
		if (!hasCommits(r)) {
			return list;
		}
		if (commit == null) {
			commit = getCommit(r, Constants.HEAD);
		}
		final TreeWalk tw = new TreeWalk(r);
		try {
			tw.addTree(commit.getTree());
			if (!StringUtils.isEmpty(basePath)) {
				PathFilter f = PathFilter.create(basePath);
				tw.setFilter(f);
				tw.setRecursive(false);
				boolean foundFolder = false;
				while (tw.next()) {
					if (!foundFolder && tw.isSubtree()) {
						tw.enterSubtree();
					}
					if (tw.getPathString().equals(basePath)) {
						foundFolder = true;
						continue;
					}
					if (foundFolder) {
						list.add(getPathModel(tw, basePath, commit));
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

	public static List<PathChangeModel> getFilesInCommit(Repository r, RevCommit commit) {
		List<PathChangeModel> list = new ArrayList<PathChangeModel>();
		RevWalk rw = new RevWalk(r);
		TreeWalk tw = new TreeWalk(r);
		try {
			if (commit == null) {
				ObjectId object = r.resolve(Constants.HEAD);
				commit = rw.parseCommit(object);
			}
			RevTree commitTree = commit.getTree();

			tw.reset();
			tw.setRecursive(true);
			if (commit.getParentCount() == 0) {
				tw.addTree(commitTree);
				while (tw.next()) {
					list.add(new PathChangeModel(tw.getPathString(), tw.getPathString(), 0, tw
							.getRawMode(0), commit.getId().getName(), ChangeType.ADD));
				}
			} else {
				RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
				RevTree parentTree = parent.getTree();
				tw.addTree(parentTree);
				tw.addTree(commitTree);
				tw.setFilter(TreeFilter.ANY_DIFF);

				RawTextComparator cmp = RawTextComparator.DEFAULT;
				DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
				df.setRepository(r);
				df.setDiffComparator(cmp);
				df.setDetectRenames(true);
				List<DiffEntry> diffs = df.scan(parentTree, commitTree);
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
			tw.release();
		}
		return list;
	}

	public static List<PathModel> getDocuments(Repository r, List<String> extensions) {
		List<PathModel> list = new ArrayList<PathModel>();
		RevCommit commit = getCommit(r, Constants.HEAD);
		final TreeWalk tw = new TreeWalk(r);
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

	public static List<RevCommit> getRevLog(Repository r, int maxCount) {
		return getRevLog(r, Constants.HEAD, 0, maxCount);
	}

	public static List<RevCommit> getRevLog(Repository r, String objectId, int offset, int maxCount) {
		return getRevLog(r, objectId, null, offset, maxCount);
	}

	public static List<RevCommit> getRevLog(Repository r, String objectId, String path, int offset,
			int maxCount) {
		List<RevCommit> list = new ArrayList<RevCommit>();
		if (!hasCommits(r)) {
			return list;
		}
		try {
			if (StringUtils.isEmpty(objectId)) {
				objectId = Constants.HEAD;
			}
			RevWalk rw = new RevWalk(r);
			ObjectId object = r.resolve(objectId);
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

	public static List<RevCommit> searchRevlogs(Repository r, String objectId, String value,
			final SearchType type, int offset, int maxCount) {
		final String lcValue = value.toLowerCase();
		List<RevCommit> list = new ArrayList<RevCommit>();
		if (!hasCommits(r)) {
			return list;
		}
		try {
			if (StringUtils.isEmpty(objectId)) {
				objectId = Constants.HEAD;
			}
			RevWalk rw = new RevWalk(r);
			rw.setRevFilter(new RevFilter() {

				@Override
				public RevFilter clone() {
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
			ObjectId object = r.resolve(objectId);
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

	public static List<RefModel> getTags(Repository r, boolean fullName, int maxCount) {
		return getRefs(r, Constants.R_TAGS, fullName, maxCount);
	}

	public static List<RefModel> getLocalBranches(Repository r, boolean fullName, int maxCount) {
		return getRefs(r, Constants.R_HEADS, fullName, maxCount);
	}

	public static List<RefModel> getRemoteBranches(Repository r, boolean fullName, int maxCount) {
		return getRefs(r, Constants.R_REMOTES, fullName, maxCount);
	}

	public static List<RefModel> getNotesRefs(Repository r, boolean fullName, int maxCount) {
		return getRefs(r, Constants.R_NOTES, fullName, maxCount);
	}

	private static List<RefModel> getRefs(Repository r, String refs, boolean fullName, int maxCount) {
		List<RefModel> list = new ArrayList<RefModel>();
		try {
			Map<String, Ref> map = r.getRefDatabase().getRefs(refs);
			RevWalk rw = new RevWalk(r);
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

	public static List<GitNote> getNotesOnCommit(Repository repository, RevCommit commit) {
		List<GitNote> list = new ArrayList<GitNote>();
		List<RefModel> notesRefs = getNotesRefs(repository, true, -1);
		for (RefModel notesRef : notesRefs) {
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

	public static StoredConfig readConfig(Repository r) {
		StoredConfig c = r.getConfig();
		try {
			c.load();
		} catch (ConfigInvalidException cex) {
			LOGGER.error("Repository configuration is invalid!", cex);
		} catch (IOException cex) {
			LOGGER.error("Could not open repository configuration!", cex);
		}
		return c;
	}

	public static boolean zip(Repository r, String basePath, String objectId, OutputStream os)
			throws Exception {
		RevCommit commit = getCommit(r, objectId);
		if (commit == null) {
			return false;
		}
		boolean success = false;
		RevWalk rw = new RevWalk(r);
		TreeWalk tw = new TreeWalk(r);
		try {
			tw.addTree(commit.getTree());
			ZipOutputStream zos = new ZipOutputStream(os);
			zos.setComment("Generated by Git:Blit");
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

				ObjectLoader ldr = r.open(blob.getId(), Constants.OBJ_BLOB);
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
