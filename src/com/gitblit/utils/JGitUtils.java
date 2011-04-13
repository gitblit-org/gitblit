package com.gitblit.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
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
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.wicket.models.Metric;
import com.gitblit.wicket.models.PathModel;
import com.gitblit.wicket.models.RefModel;
import com.gitblit.wicket.models.TicGitTicket;
import com.gitblit.wicket.models.TicGitTicket.Comment;

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

	public static RevCommit getCommit(Repository r, String objectId) {
		RevCommit commit = null;
		try {
			if (objectId == null || objectId.trim().length() == 0) {
				objectId = Constants.HEAD;
			}
			ObjectId object = r.resolve(objectId);
			RevWalk walk = new RevWalk(r);
			RevCommit rev = walk.parseCommit(object);
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

	public static List<PathModel> getFilesInPath(Repository r, String basePath, String objectId) {
		RevCommit commit = getCommit(r, objectId);
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

	public static List<PathModel> getFilesInCommit(Repository r, String commitId) {
		RevCommit commit = getCommit(r, commitId);
		return getFilesInCommit(r, commit);
	}

	public static List<PathModel> getFilesInCommit(Repository r, RevCommit commit) {
		List<PathModel> list = new ArrayList<PathModel>();
		try {
			final RevWalk rw = new RevWalk(r);
			RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
			RevTree parentTree = parent.getTree();
			RevTree commitTree = commit.getTree();

			final TreeWalk walk = new TreeWalk(r);
			walk.reset();
			walk.setRecursive(true);
			walk.addTree(parentTree);
			walk.addTree(commitTree);
			walk.setFilter(TreeFilter.ANY_DIFF);

			RawTextComparator cmp = RawTextComparator.DEFAULT;
			DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
			df.setRepository(r);
			df.setDiffComparator(cmp);
			df.setDetectRenames(true);
			List<DiffEntry> diffs = df.scan(parentTree, commitTree);
			for (DiffEntry diff : diffs) {
				list.add(new PathModel(diff.getNewPath(), diff.getNewPath(), 0, diff.getNewMode().getBits(), commit.getId().getName()));
			}
		} catch (Throwable t) {
			LOGGER.error("failed to determine files in commit!", t);
		}
		return list;
	}

	public static String getCommitDiff(Repository r, RevCommit commit, boolean outputHtml) {
		return getCommitDiff(r, commit, null, outputHtml);
	}

	public static String getCommitDiff(Repository r, RevCommit commit, String path, boolean outputHtml) {
		try {
			final RevWalk rw = new RevWalk(r);
			RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
			RevTree parentTree = parent.getTree();
			RevTree commitTree = commit.getTree();

			final TreeWalk walk = new TreeWalk(r);
			walk.reset();
			walk.setRecursive(true);
			walk.addTree(parentTree);
			walk.addTree(commitTree);
			walk.setFilter(TreeFilter.ANY_DIFF);

			final ByteArrayOutputStream os = new ByteArrayOutputStream();
			RawTextComparator cmp = RawTextComparator.DEFAULT;
			DiffFormatter df;
			if (outputHtml) {
				df = new HtmlDiffFormatter(os);
			} else {
				df = new DiffFormatter(os);
			}
			df.setRepository(r);
			df.setDiffComparator(cmp);
			df.setDetectRenames(true);
			List<DiffEntry> diffs = df.scan(parentTree, commitTree);
			if (path != null && path.length() > 0) {
				for (DiffEntry diff : diffs) {
					if (diff.getNewPath().equalsIgnoreCase(path)) {
						df.format(diff);
						break;
					}
				}
			} else {
				df.format(diffs);
			}
			String diff;
			if (outputHtml) {
				// workaround for complex private methods in DiffFormatter
				diff = ((HtmlDiffFormatter) df).getHtml();
			} else {
				diff = os.toString();
			}
			df.flush();
			return diff;
		} catch (Throwable t) {
			LOGGER.error("failed to generate commit diff!", t);
		}
		return null;
	}
	
	public static String getCommitPatch(Repository r, RevCommit commit) {
		return getCommitPatch(r, commit);
	}
	
	public static String getCommitPatch(Repository r, RevCommit commit, String path) {
		try {
			final RevWalk rw = new RevWalk(r);
			RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
			RevTree parentTree = parent.getTree();
			RevTree commitTree = commit.getTree();

			final TreeWalk walk = new TreeWalk(r);
			walk.reset();
			walk.setRecursive(true);
			walk.addTree(parentTree);
			walk.addTree(commitTree);
			walk.setFilter(TreeFilter.ANY_DIFF);

			final ByteArrayOutputStream os = new ByteArrayOutputStream();
			RawTextComparator cmp = RawTextComparator.DEFAULT;
			PatchFormatter df = new PatchFormatter(os);
			df.setRepository(r);
			df.setDiffComparator(cmp);
			df.setDetectRenames(true);
			List<DiffEntry> diffs = df.scan(parentTree, commitTree);
			if (path != null && path.length() > 0) {
				for (DiffEntry diff : diffs) {
					if (diff.getNewPath().equalsIgnoreCase(path)) {
						df.format(diff);
						break;
					}
				}
			} else {
				df.format(diffs);
			}
			String diff = df.getPatch(commit);
			df.flush();
			return diff;
		} catch (Throwable t) {
			LOGGER.error("failed to generate commit diff!", t);
		}
		return null;
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
			LOGGER.error("Failed to retrieve blob size", t);
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
		return getRevLog(r, Constants.HEAD, 0, maxCount);
	}

	public static List<RevCommit> getRevLog(Repository r, String objectId, int offset, int maxCount) {
		List<RevCommit> list = new ArrayList<RevCommit>();
		try {
			if (objectId == null || objectId.trim().length() == 0) {
				objectId = Constants.HEAD;
			}
			RevWalk walk = new RevWalk(r);
			ObjectId object = r.resolve(objectId);
			walk.markStart(walk.parseCommit(object));
			Iterable<RevCommit> revlog = walk;
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
			walk.dispose();
		} catch (Throwable t) {
			LOGGER.error("Failed to determine last change", t);
		}
		return list;
	}

	public static List<RefModel> getTags(Repository r, int maxCount) {
		return getRefs(r, Constants.R_TAGS, maxCount);
	}

	public static List<RefModel> getLocalBranches(Repository r, int maxCount) {
		return getRefs(r, Constants.R_HEADS, maxCount);
	}

	public static List<RefModel> getRemoteBranches(Repository r, int maxCount) {
		return getRefs(r, Constants.R_REMOTES, maxCount);
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
		final List<RefModel> tags = getTags(r, -1);
		final Map<String, Metric> map = new HashMap<String, Metric>();
		try {
			DateFormat df = new SimpleDateFormat("yyyy-MM");
			RevWalk walk = new RevWalk(r);
			ObjectId object = r.resolve(Constants.HEAD);
			walk.markStart(walk.parseCommit(object));
			Iterable<RevCommit> revlog = walk;
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
		for (String key : keys) {
			metrics.add(map.get(key));
		}
		return metrics;
	}

	public static RefModel getTicGitBranch(Repository r) {
		RefModel ticgitBranch = null;
		try {
			// search for ticgit branch in local heads
			for (RefModel ref : getLocalBranches(r, -1)) {
				if (ref.getDisplayName().endsWith("ticgit") || ref.getDisplayName().endsWith("ticgit-ng")) {
					ticgitBranch = ref;
					break;
				}
			}

			// search for ticgit branch in remote heads
			if (ticgitBranch == null) {
				for (RefModel ref : getRemoteBranches(r, -1)) {
					if (ref.getDisplayName().endsWith("ticgit") || ref.getDisplayName().endsWith("ticgit-ng")) {
						ticgitBranch = ref;
						break;
					}
				}
			}
		} catch (Throwable t) {
			LOGGER.error("Failed to find ticgit branch!", t);
		}
		return ticgitBranch;
	}

	public static List<TicGitTicket> getTicGitTickets(Repository r) {
		RefModel ticgitBranch = getTicGitBranch(r);
		List<PathModel> paths = getFilesInPath(r, null, ticgitBranch.getCommit());
		List<TicGitTicket> tickets = new ArrayList<TicGitTicket>();
		for (PathModel ticketFolder : paths) {
			if (ticketFolder.isTree()) {
				try {
					TicGitTicket t = new TicGitTicket(ticketFolder.name);
					readTicketContents(r, ticgitBranch, t);
					tickets.add(t);
				} catch (Throwable t) {
					LOGGER.error("Failed to get a ticgit ticket!", t);
				}
			}
		}
		Collections.sort(tickets);
		Collections.reverse(tickets);
		return tickets;
	}

	public static TicGitTicket getTicGitTicket(Repository r, String ticketFolder) {
		RefModel ticgitBranch = getTicGitBranch(r);
		if (ticgitBranch != null) {
			try {
				TicGitTicket ticket = new TicGitTicket(ticketFolder);
				readTicketContents(r, ticgitBranch, ticket);
				return ticket;
			} catch (Throwable t) {
				LOGGER.error("Failed to get ticgit ticket " + ticketFolder, t);
			}
		}
		return null;
	}

	private static void readTicketContents(Repository r, RefModel ticgitBranch, TicGitTicket ticket) {
		List<PathModel> ticketFiles = getFilesInPath(r, ticket.name, ticgitBranch.getCommit());
		for (PathModel file : ticketFiles) {
			String content = getRawContentAsString(r, ticgitBranch.getCommit(), file.path).trim();
			if (file.name.equals("TICKET_ID")) {
				ticket.id = content;
			} else if (file.name.equals("TITLE")) {
				ticket.title = content;
			} else {
				String[] chunks = file.name.split("_");
				if (chunks[0].equals("ASSIGNED")) {
					ticket.handler = content;
				} else if (chunks[0].equals("COMMENT")) {
					try {
						Comment c = new Comment(file.name, content);
						ticket.comments.add(c);
					} catch (ParseException e) {
						e.printStackTrace();
					}
				} else if (chunks[0].equals("TAG")) {
					if (content.startsWith("TAG_")) {
						ticket.tags.add(content.substring(4));
					} else {
						ticket.tags.add(content);
					}
				} else if (chunks[0].equals("STATE")) {
					ticket.state = content;
				}
			}
		}
		Collections.sort(ticket.comments);
	}

	public static String getTicGitContent(Repository r, String filePath) {
		RefModel ticgitBranch = getTicGitBranch(r);
		if (ticgitBranch != null) {
			return getRawContentAsString(r, ticgitBranch.getCommit(), filePath);
		}
		return "";
	}
}
