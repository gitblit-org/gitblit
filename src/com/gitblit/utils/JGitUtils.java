package com.gitblit.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.wicket.models.Metric;
import com.gitblit.wicket.models.PathModel;
import com.gitblit.wicket.models.RefModel;

public class JGitUtils {

	/** Prefix for notes refs */
	public static final String R_NOTES = "refs/notes/";

	/** Standard notes ref */
	public static final String R_NOTES_COMMITS = R_NOTES + "commits";

	private final static Logger LOGGER = LoggerFactory.getLogger(JGitUtils.class);

	public static List<String> getRepositoryList(File repositoriesFolder, boolean exportAll, boolean readNested) {
		List<String> list = new ArrayList<String>();
		list.addAll(getNestedRepositories(repositoriesFolder, repositoriesFolder, exportAll, readNested));
		Collections.sort(list);
		return list;
	}

	public static List<String> getNestedRepositories(File repositoriesFolder, File folder, boolean exportAll, boolean readNested) {
		String basefile = repositoriesFolder.getAbsolutePath();
		List<String> list = new ArrayList<String>();
		for (File file : folder.listFiles()) {
			if (file.isDirectory() && !file.getName().equalsIgnoreCase(Constants.DOT_GIT)) {
				// if this is a git repository add it to the list
				File gitFolder = new File(file, Constants.DOT_GIT);
				boolean isGitRepository = gitFolder.exists() && gitFolder.isDirectory();
				boolean exportRepository = isGitRepository && (exportAll || new File(gitFolder, "git-daemon-export-ok").exists());

				if (exportRepository) {
					// determine repository name relative to repositories folder
					String filename = file.getAbsolutePath();
					String repo = filename.substring(basefile.length()).replace('\\', '/');
					if (repo.charAt(0) == '/') {
						repo = repo.substring(1);
					}
					list.add(repo);
				}

				// look for nested repositories
				if (readNested) {
					list.addAll(getNestedRepositories(repositoriesFolder, file, exportAll, readNested));
				}
			}
		}
		return list;
	}

	public static Date getLastChange(Repository r) {
		RevCommit commit = getCommit(r, Constants.HEAD);
		return getCommitDate(commit);
	}

	public static RevCommit getCommit(Repository r, String commitId) {
		RevCommit commit = null;
		try {
			ObjectId objectId = r.resolve(commitId);
			RevWalk walk = new RevWalk(r);
			RevCommit rev = walk.parseCommit(objectId);
			commit = rev;
			walk.dispose();
		} catch (Throwable t) {
			LOGGER.error("Failed to determine last change", t);
		}
		return commit;
	}

	public static Map<ObjectId, List<String>> getAllRefs(Repository r) {
		Map<ObjectId, List<String>> refs = new HashMap<ObjectId, List<String>>();
		Map<AnyObjectId, Set<Ref>> allRefs = r.getAllRefsByPeeledObjectId();
		for (AnyObjectId id : allRefs.keySet()) {
			List<String> list = new ArrayList<String>();
			for (Ref setRef : allRefs.get(id)) {
				String name = setRef.getName();
				list.add(name);
			}
			refs.put(id.toObjectId(), list);
		}
		return refs;
	}

	public static Map<ObjectId, List<String>> getRefs(Repository r, String baseRef) {
		Map<ObjectId, List<String>> refs = new HashMap<ObjectId, List<String>>();
		Map<AnyObjectId, Set<Ref>> allRefs = r.getAllRefsByPeeledObjectId();
		for (AnyObjectId id : allRefs.keySet()) {
			List<String> list = new ArrayList<String>();
			for (Ref setRef : allRefs.get(id)) {
				String name = setRef.getName();
				if (name.startsWith(baseRef)) {
					list.add(name);
				}
			}
			refs.put(id.toObjectId(), list);
		}
		return refs;
	}

	/**
	 * Lookup an entry stored in a tree, failing if not present.
	 * 
	 * @param tree
	 *            the tree to search.
	 * @param path
	 *            the path to find the entry of.
	 * @return the parsed object entry at this path
	 * @throws Exception
	 */
	public static RevObject getRevObject(Repository r, final RevTree tree, final String path) {
		RevObject ro = null;
		RevWalk rw = new RevWalk(r);
		TreeWalk tw = new TreeWalk(r);
		tw.setFilter(PathFilterGroup.createFromStrings(Collections.singleton(path)));
		try {
			tw.reset(tree);
			while (tw.next()) {
				if (tw.isSubtree() && !path.equals(tw.getPathString())) {
					tw.enterSubtree();
					continue;
				}
				ObjectId entid = tw.getObjectId(0);
				FileMode entmode = tw.getFileMode(0);
				ro = rw.lookupAny(entid, entmode.getObjectType());
				rw.parseBody(ro);
			}
		} catch (Throwable t) {
			LOGGER.error("Can't find " + path + " in tree " + tree.name(), t);
		} finally {
			if (rw != null) {
				rw.dispose();
			}
		}
		return ro;
	}

	public static byte[] getRawContent(Repository r, RevBlob blob) {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		try {
			ObjectLoader ldr = r.open(blob.getId(), Constants.OBJ_BLOB);
			byte[] tmp = new byte[1024];
			InputStream in = ldr.openStream();
			int n;
			while ((n = in.read(tmp)) > 0) {
				os.write(tmp, 0, n);
			}
			in.close();
		} catch (Throwable t) {
			LOGGER.error("Failed to read raw content", t);
		}
		return os.toByteArray();
	}

	public static String getRawContentAsString(Repository r, RevBlob blob) {
		return new String(getRawContent(r, blob));
	}

	public static String getRawContentAsString(Repository r, RevCommit commit, String blobPath) {
		RevObject obj = getRevObject(r, commit.getTree(), blobPath);
		return new String(getRawContent(r, (RevBlob) obj));
	}

	public static List<PathModel> getFilesInPath(Repository r, String basePath, String commitId) {
		RevCommit commit = getCommit(r, commitId);
		return getFilesInPath(r, basePath, commit);
	}

	public static List<PathModel> getFilesInPath(Repository r, String basePath, RevCommit commit) {
		List<PathModel> list = new ArrayList<PathModel>();
		final TreeWalk walk = new TreeWalk(r);
		try {
			walk.addTree(commit.getTree());
			if (basePath != null && basePath.length() > 0) {
				PathFilter f = PathFilter.create(basePath);
				walk.setFilter(f);
				walk.setRecursive(false);
				boolean foundFolder = false;
				while (walk.next()) {
					if (!foundFolder && walk.isSubtree()) {
						walk.enterSubtree();
					}
					if (walk.getPathString().equals(basePath)) {
						foundFolder = true;
						continue;
					}
					if (foundFolder) {
						list.add(getPathModel(walk, basePath, commit));
					}
				}
			} else {
				walk.setRecursive(false);
				while (walk.next()) {
					list.add(getPathModel(walk, null, commit));
				}
			}
		} catch (IOException e) {
			LOGGER.error("Failed to get files for commit " + commit.getName(), e);
		} finally {
			walk.release();
		}
		Collections.sort(list);
		return list;
	}

	public static List<PathModel> getCommitChangedPaths(Repository r, String commitId) {
		RevCommit commit = getCommit(r, commitId);
		return getCommitChangedPaths(r, commit);
	}

	public static List<PathModel> getCommitChangedPaths(Repository r, RevCommit commit) {
		List<PathModel> list = new ArrayList<PathModel>();
		final TreeWalk walk = new TreeWalk(r);
		walk.setRecursive(false);
		try {
			walk.addTree(commit.getTree());
			while (walk.next()) {
				list.add(getPathModel(walk, null, commit));
			}

		} catch (IOException e) {
			LOGGER.error("Failed to get files for commit " + commit.getName(), e);
		} finally {
			if (walk != null) {
				walk.release();
			}
		}
		return list;
	}

	private static PathModel getPathModel(TreeWalk walk, String basePath, RevCommit commit) {
		String name;
		long size = 0;
		if (basePath == null) {
			name = walk.getPathString();
		} else {
			try {
				name = walk.getPathString().substring(basePath.length() + 1);
			} catch (Throwable t) {
				name = walk.getPathString();
			}
		}
		try {
			if (!walk.isSubtree()) {
				size = walk.getObjectReader().getObjectSize(walk.getObjectId(0), Constants.OBJ_BLOB);
			}
		} catch (Throwable t) {
			LOGGER.error("Failed to retrieve blobl size", t);
		}
		return new PathModel(name, walk.getPathString(), size, walk.getFileMode(0).getBits(), commit.getName());
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
		} else if (FileMode.MISSING.equals(mode)) {
			// FIXME missing permissions
			return "missing";
		}
		return "" + mode;
	}

	public static boolean isTreeFromMode(int mode) {
		return FileMode.TREE.equals(mode);
	}

	public static List<RevCommit> getRevLog(Repository r, int maxCount) {
		List<RevCommit> list = new ArrayList<RevCommit>();
		try {
			Git git = new Git(r);
			Iterable<RevCommit> revlog = git.log().call();
			for (RevCommit rev : revlog) {
				list.add(rev);
				if (maxCount > 0 && list.size() == maxCount) {
					break;
				}
			}
		} catch (Throwable t) {
			LOGGER.error("Failed to determine last change", t);
		}
		return list;
	}

	public static List<RefModel> getTags(Repository r, int maxCount) {
		return getRefs(r, Constants.R_TAGS, maxCount);
	}

	public static List<RefModel> getHeads(Repository r, int maxCount) {
		return getRefs(r, Constants.R_HEADS, maxCount);
	}

	public static List<RefModel> getRefs(Repository r, String refs, int maxCount) {
		List<RefModel> list = new ArrayList<RefModel>();
		try {
			Map<String, Ref> map = r.getRefDatabase().getRefs(refs);
			for (String name : map.keySet()) {
				Ref ref = map.get(name);
				RevCommit commit = getCommit(r, ref.getObjectId().getName());
				list.add(new RefModel(name, ref, commit));
			}
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

	public static Ref getRef(Repository r, String id) {
		try {
			Map<String, Ref> map = r.getRefDatabase().getRefs(id);
			for (String name : map.keySet()) {
				return map.get(name);
			}
		} catch (IOException e) {
			LOGGER.error("Failed to retrieve ref " + id, e);
		}
		return null;
	}

	public static Date getCommitDate(RevCommit commit) {
		return new Date(commit.getCommitTime() * 1000l);
	}

	public static String getDisplayName(PersonIdent person) {
		final StringBuilder r = new StringBuilder();
		r.append(person.getName());
		r.append(" <");
		r.append(person.getEmailAddress());
		r.append(">");
		return r.toString();
	}

	public static String getRepositoryDescription(Repository r) {
		File dir = r.getDirectory();
		if (dir.exists()) {
			File description = new File(dir, "description");
			if (description.exists() && description.length() > 0) {
				RandomAccessFile raf = null;
				try {
					raf = new RandomAccessFile(description, "r");
					byte[] buffer = new byte[(int) description.length()];
					raf.readFully(buffer);
					return new String(buffer);
				} catch (Throwable t) {
				} finally {
					try {
						raf.close();
					} catch (Throwable t) {
					}
				}
			}
		}
		return "";
	}

	public static String getRepositoryOwner(Repository r) {
		StoredConfig c = readConfig(r);
		if (c == null) {
			return "";
		}
		String o = c.getString("gitweb", null, "owner");
		return o == null ? "" : o;
	}

	private static StoredConfig readConfig(Repository r) {
		StoredConfig c = r.getConfig();
		if (c != null) {
			try {
				c.load();
			} catch (ConfigInvalidException cex) {
				LOGGER.error("Repository configuration is invalid!", cex);
			} catch (IOException cex) {
				LOGGER.error("Could not open repository configuration!", cex);
			}
			return c;
		}
		return null;
	}

	public static List<Metric> getDateMetrics(Repository r) {
		final Map<String, Metric> map = new HashMap<String, Metric>();
		try {
			DateFormat df = new SimpleDateFormat("yyyy-MM");
			Git git = new Git(r);
			Iterable<RevCommit> revlog = git.log().call();
			for (RevCommit rev : revlog) {
				Date d = getCommitDate(rev);
				String p = df.format(d);
				if (!map.containsKey(p))
					map.put(p, new Metric(p));
				map.get(p).count++;
			}
		} catch (Throwable t) {
			LOGGER.error("Failed to mine log history for metrics", t);
		}
		List<String> keys = new ArrayList<String>(map.keySet());
		Collections.sort(keys);
		List<Metric> metrics = new ArrayList<Metric>();
		for (String key:keys) {
			metrics.add(map.get(key));
		}		
		return metrics;
	}
}
