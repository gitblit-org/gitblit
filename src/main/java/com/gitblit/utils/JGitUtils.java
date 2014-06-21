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

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.apache.commons.io.filefilter.TrueFileFilter;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TagCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StopWalkException;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.RecursiveMerger;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.MergeType;
import com.gitblit.GitBlitException;
import com.gitblit.models.GitNote;
import com.gitblit.models.PathModel;
import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.models.RefModel;
import com.gitblit.models.SubmoduleModel;

/**
 * Collection of static methods for retrieving information from a repository.
 *
 * @author James Moger
 *
 */
public class JGitUtils {

	static final Logger LOGGER = LoggerFactory.getLogger(JGitUtils.class);

	/**
	 * Log an error message and exception.
	 *
	 * @param t
	 * @param repository
	 *            if repository is not null it MUST be the {0} parameter in the
	 *            pattern.
	 * @param pattern
	 * @param objects
	 */
	private static void error(Throwable t, Repository repository, String pattern, Object... objects) {
		List<Object> parameters = new ArrayList<Object>();
		if (objects != null && objects.length > 0) {
			for (Object o : objects) {
				parameters.add(o);
			}
		}
		if (repository != null) {
			parameters.add(0, repository.getDirectory().getAbsolutePath());
		}
		LOGGER.error(MessageFormat.format(pattern, parameters.toArray()), t);
	}

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
	 * Encapsulates the result of cloning or pulling from a repository.
	 */
	public static class CloneResult {
		public String name;
		public FetchResult fetchResult;
		public boolean createdRepository;
	}

	/**
	 * Clone or Fetch a repository. If the local repository does not exist,
	 * clone is called. If the repository does exist, fetch is called. By
	 * default the clone/fetch retrieves the remote heads, tags, and notes.
	 *
	 * @param repositoriesFolder
	 * @param name
	 * @param fromUrl
	 * @return CloneResult
	 * @throws Exception
	 */
	public static CloneResult cloneRepository(File repositoriesFolder, String name, String fromUrl)
			throws Exception {
		return cloneRepository(repositoriesFolder, name, fromUrl, true, null);
	}

	/**
	 * Clone or Fetch a repository. If the local repository does not exist,
	 * clone is called. If the repository does exist, fetch is called. By
	 * default the clone/fetch retrieves the remote heads, tags, and notes.
	 *
	 * @param repositoriesFolder
	 * @param name
	 * @param fromUrl
	 * @param bare
	 * @param credentialsProvider
	 * @return CloneResult
	 * @throws Exception
	 */
	public static CloneResult cloneRepository(File repositoriesFolder, String name, String fromUrl,
			boolean bare, CredentialsProvider credentialsProvider) throws Exception {
		CloneResult result = new CloneResult();
		if (bare) {
			// bare repository, ensure .git suffix
			if (!name.toLowerCase().endsWith(Constants.DOT_GIT_EXT)) {
				name += Constants.DOT_GIT_EXT;
			}
		} else {
			// normal repository, strip .git suffix
			if (name.toLowerCase().endsWith(Constants.DOT_GIT_EXT)) {
				name = name.substring(0, name.indexOf(Constants.DOT_GIT_EXT));
			}
		}
		result.name = name;

		File folder = new File(repositoriesFolder, name);
		if (folder.exists()) {
			File gitDir = FileKey.resolve(new File(repositoriesFolder, name), FS.DETECTED);
			Repository repository = new FileRepositoryBuilder().setGitDir(gitDir).build();
			result.fetchResult = fetchRepository(credentialsProvider, repository);
			repository.close();
		} else {
			CloneCommand clone = new CloneCommand();
			clone.setBare(bare);
			clone.setCloneAllBranches(true);
			clone.setURI(fromUrl);
			clone.setDirectory(folder);
			if (credentialsProvider != null) {
				clone.setCredentialsProvider(credentialsProvider);
			}
			Repository repository = clone.call().getRepository();

			// Now we have to fetch because CloneCommand doesn't fetch
			// refs/notes nor does it allow manual RefSpec.
			result.createdRepository = true;
			result.fetchResult = fetchRepository(credentialsProvider, repository);
			repository.close();
		}
		return result;
	}

	/**
	 * Fetch updates from the remote repository. If refSpecs is unspecifed,
	 * remote heads, tags, and notes are retrieved.
	 *
	 * @param credentialsProvider
	 * @param repository
	 * @param refSpecs
	 * @return FetchResult
	 * @throws Exception
	 */
	public static FetchResult fetchRepository(CredentialsProvider credentialsProvider,
			Repository repository, RefSpec... refSpecs) throws Exception {
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
		if (credentialsProvider != null) {
			fetch.setCredentialsProvider(credentialsProvider);
		}
		fetch.setRefSpecs(specs);
		FetchResult fetchRes = fetch.call();
		return fetchRes;
	}

	/**
	 * Creates a bare repository.
	 *
	 * @param repositoriesFolder
	 * @param name
	 * @return Repository
	 */
	public static Repository createRepository(File repositoriesFolder, String name) {
		return createRepository(repositoriesFolder, name, "FALSE");
	}

	/**
	 * Creates a bare, shared repository.
	 *
	 * @param repositoriesFolder
	 * @param name
	 * @param shared
	 *          the setting for the --shared option of "git init".
	 * @return Repository
	 */
	public static Repository createRepository(File repositoriesFolder, String name, String shared) {
		try {
			Repository repo = null;
			try {
				Git git = Git.init().setDirectory(new File(repositoriesFolder, name)).setBare(true).call();
				repo = git.getRepository();
			} catch (GitAPIException e) {
				throw new RuntimeException(e);
			}

			GitConfigSharedRepository sharedRepository = new GitConfigSharedRepository(shared);
			if (sharedRepository.isShared()) {
				StoredConfig config = repo.getConfig();
				config.setString("core", null, "sharedRepository", sharedRepository.getValue());
				config.setBoolean("receive", null, "denyNonFastforwards", true);
				config.save();

				if (! JnaUtils.isWindows()) {
					Iterator<File> iter = org.apache.commons.io.FileUtils.iterateFilesAndDirs(repo.getDirectory(),
							TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
					// Adjust permissions on file/directory
					while (iter.hasNext()) {
						adjustSharedPerm(iter.next(), sharedRepository);
					}
				}
			}

			return repo;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private enum GitConfigSharedRepositoryValue
	{
		UMASK("0", 0), FALSE("0", 0), OFF("0", 0), NO("0", 0),
		GROUP("1", 0660), TRUE("1", 0660), ON("1", 0660), YES("1", 0660),
		ALL("2", 0664), WORLD("2", 0664), EVERYBODY("2", 0664),
		Oxxx(null, -1);

		private String configValue;
		private int permValue;
		private GitConfigSharedRepositoryValue(String config, int perm) { configValue = config; permValue = perm; };

		public String getConfigValue() { return configValue; };
		public int getPerm() { return permValue; };

	}

	private static class GitConfigSharedRepository
	{
		private int intValue;
		private GitConfigSharedRepositoryValue enumValue;

		GitConfigSharedRepository(String s) {
			if ( s == null || s.trim().isEmpty() ) {
				enumValue = GitConfigSharedRepositoryValue.GROUP;
			}
			else {
				try {
					// Try one of the string values
					enumValue = GitConfigSharedRepositoryValue.valueOf(s.trim().toUpperCase());
				} catch (IllegalArgumentException  iae) {
					try {
						// Try if this is an octal number
						int i = Integer.parseInt(s, 8);
						if ( (i & 0600) != 0600 ) {
							String msg = String.format("Problem with core.sharedRepository filemode value (0%03o).\nThe owner of files must always have read and write permissions.", i);
							throw new IllegalArgumentException(msg);
						}
						intValue = i & 0666;
						enumValue = GitConfigSharedRepositoryValue.Oxxx;
					} catch (NumberFormatException nfe) {
						throw new IllegalArgumentException("Bad configuration value for 'shared': '" + s + "'");
					}
				}
			}
		}

		String getValue() {
			if ( enumValue == GitConfigSharedRepositoryValue.Oxxx ) {
				if (intValue == 0) return "0";
				return String.format("0%o", intValue);
			}
			return enumValue.getConfigValue();
		}

		int getPerm() {
			if ( enumValue == GitConfigSharedRepositoryValue.Oxxx ) return intValue;
			return enumValue.getPerm();
		}

		boolean isCustom() {
			return enumValue == GitConfigSharedRepositoryValue.Oxxx;
		}

		boolean isShared() {
			return (enumValue.getPerm() > 0) || enumValue == GitConfigSharedRepositoryValue.Oxxx;
		}
	}


	/**
	 * Adjust file permissions of a file/directory for shared repositories
	 *
	 * @param path
	 * 			File that should get its permissions changed.
	 * @param configShared
	 * 			Configuration string value for the shared mode.
	 * @return Upon successful completion, a value of 0 is returned. Otherwise, a value of -1 is returned.
	 */
	public static int adjustSharedPerm(File path, String configShared) {
		return adjustSharedPerm(path, new GitConfigSharedRepository(configShared));
	}


	/**
	 * Adjust file permissions of a file/directory for shared repositories
	 *
	 * @param path
	 * 			File that should get its permissions changed.
	 * @param configShared
	 * 			Configuration setting for the shared mode.
	 * @return Upon successful completion, a value of 0 is returned. Otherwise, a value of -1 is returned.
	 */
	public static int adjustSharedPerm(File path, GitConfigSharedRepository configShared) {
		if (! configShared.isShared()) return 0;
		if (! path.exists()) return -1;

		int perm = configShared.getPerm();
		JnaUtils.Filestat stat = JnaUtils.getFilestat(path);
		if (stat == null) return -1;
		int mode = stat.mode;
		if (mode < 0) return -1;

		// Now, here is the kicker: Under Linux, chmod'ing a sgid file whose guid is different from the process'
		// effective guid will reset the sgid flag of the file. Since there is no way to get the sgid flag back in
		// that case, we decide to rather not touch is and getting the right permissions will have to be achieved
		// in a different way, e.g. by using an appropriate umask for the Gitblit process.
		if (System.getProperty("os.name").toLowerCase().startsWith("linux")) {
			if ( ((mode & (JnaUtils.S_ISGID | JnaUtils.S_ISUID)) != 0)
				&& stat.gid != JnaUtils.getegid() ) {
				LOGGER.debug("Not adjusting permissions to prevent clearing suid/sgid bits for '" + path + "'" );
				return 0;
			}
		}

		// If the owner has no write access, delete it from group and other, too.
		if ((mode & JnaUtils.S_IWUSR) == 0) perm &= ~0222;
		// If the owner has execute access, set it for all blocks that have read access.
		if ((mode & JnaUtils.S_IXUSR) == JnaUtils.S_IXUSR) perm |= (perm & 0444) >> 2;

		if (configShared.isCustom()) {
			// Use the custom value for access permissions.
			mode = (mode & ~0777) | perm;
		}
		else {
			// Just add necessary bits to existing permissions.
			mode |= perm;
		}

		if (path.isDirectory()) {
			mode |= (mode & 0444) >> 2;
			mode |= JnaUtils.S_ISGID;
		}

		return JnaUtils.setFilemode(path, mode);
	}


	/**
	 * Returns a list of repository names in the specified folder.
	 *
	 * @param repositoriesFolder
	 * @param onlyBare
	 *            if true, only bare repositories repositories are listed. If
	 *            false all repositories are included.
	 * @param searchSubfolders
	 *            recurse into subfolders to find grouped repositories
	 * @param depth
	 *            optional recursion depth, -1 = infinite recursion
	 * @param exclusions
	 *            list of regex exclusions for matching to folder names
	 * @return list of repository names
	 */
	public static List<String> getRepositoryList(File repositoriesFolder, boolean onlyBare,
			boolean searchSubfolders, int depth, List<String> exclusions) {
		List<String> list = new ArrayList<String>();
		if (repositoriesFolder == null || !repositoriesFolder.exists()) {
			return list;
		}
		List<Pattern> patterns = new ArrayList<Pattern>();
		if (!ArrayUtils.isEmpty(exclusions)) {
			for (String regex : exclusions) {
				patterns.add(Pattern.compile(regex));
			}
		}
		list.addAll(getRepositoryList(repositoriesFolder.getAbsolutePath(), repositoriesFolder,
				onlyBare, searchSubfolders, depth, patterns));
		StringUtils.sortRepositorynames(list);
		list.remove(".git"); // issue-256
		return list;
	}

	/**
	 * Recursive function to find git repositories.
	 *
	 * @param basePath
	 *            basePath is stripped from the repository name as repositories
	 *            are relative to this path
	 * @param searchFolder
	 * @param onlyBare
	 *            if true only bare repositories will be listed. if false all
	 *            repositories are included.
	 * @param searchSubfolders
	 *            recurse into subfolders to find grouped repositories
	 * @param depth
	 *            recursion depth, -1 = infinite recursion
	 * @param patterns
	 *            list of regex patterns for matching to folder names
	 * @return
	 */
	private static List<String> getRepositoryList(String basePath, File searchFolder,
			boolean onlyBare, boolean searchSubfolders, int depth, List<Pattern> patterns) {
		File baseFile = new File(basePath);
		List<String> list = new ArrayList<String>();
		if (depth == 0) {
			return list;
		}

		int nextDepth = (depth == -1) ? -1 : depth - 1;
		for (File file : searchFolder.listFiles()) {
			if (file.isDirectory()) {
				boolean exclude = false;
				for (Pattern pattern : patterns) {
					String path = FileUtils.getRelativePath(baseFile, file).replace('\\',  '/');
					if (pattern.matcher(path).matches()) {
						LOGGER.debug(MessageFormat.format("excluding {0} because of rule {1}", path, pattern.pattern()));
						exclude = true;
						break;
					}
				}
				if (exclude) {
					// skip to next file
					continue;
				}

				File gitDir = FileKey.resolve(new File(searchFolder, file.getName()), FS.DETECTED);
				if (gitDir != null) {
					if (onlyBare && gitDir.getName().equals(".git")) {
						continue;
					}
					if (gitDir.equals(file) || gitDir.getParentFile().equals(file)) {
						// determine repository name relative to base path
						String repository = FileUtils.getRelativePath(baseFile, file);
						list.add(repository);
					} else if (searchSubfolders && file.canRead()) {
						// look for repositories in subfolders
						list.addAll(getRepositoryList(basePath, file, onlyBare, searchSubfolders,
								nextDepth, patterns));
					}
				} else if (searchSubfolders && file.canRead()) {
					// look for repositories in subfolders
					list.addAll(getRepositoryList(basePath, file, onlyBare, searchSubfolders,
							nextDepth, patterns));
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
		RevCommit commit = null;
		try {
			// resolve branch
			ObjectId branchObject;
			if (StringUtils.isEmpty(branch)) {
				branchObject = getDefaultBranch(repository);
			} else {
				branchObject = repository.resolve(branch);
			}

			RevWalk walk = new RevWalk(repository);
			walk.sort(RevSort.REVERSE);
			RevCommit head = walk.parseCommit(branchObject);
			walk.markStart(head);
			commit = walk.next();
			walk.dispose();
		} catch (Throwable t) {
			error(t, repository, "{0} failed to determine first commit");
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
	 * the for loose and packed objects.
	 *
	 * @param repository
	 * @return true if the repository has commits
	 */
	public static boolean hasCommits(Repository repository) {
		if (repository != null && repository.getDirectory().exists()) {
			return (new File(repository.getDirectory(), "objects").list().length > 2)
					|| (new File(repository.getDirectory(), "objects/pack").list().length > 0);
		}
		return false;
	}

	/**
	 * Encapsulates the result of cloning or pulling from a repository.
	 */
	public static class LastChange {
		public Date when;
		public String who;

		LastChange() {
			when = new Date(0);
		}

		LastChange(long lastModified) {
			this.when = new Date(lastModified);
		}
	}

	/**
	 * Returns the date and author of the most recent commit on a branch. If the
	 * repository does not exist Date(0) is returned. If it does exist but is
	 * empty, the last modified date of the repository folder is returned.
	 *
	 * @param repository
	 * @return a LastChange object
	 */
	public static LastChange getLastChange(Repository repository) {
		if (!hasCommits(repository)) {
			// null repository
			if (repository == null) {
				return new LastChange();
			}
			// fresh repository
			return new LastChange(repository.getDirectory().lastModified());
		}

		List<RefModel> branchModels = getLocalBranches(repository, true, -1);
		if (branchModels.size() > 0) {
			// find most recent branch update
			LastChange lastChange = new LastChange();
			for (RefModel branchModel : branchModels) {
				if (branchModel.getDate().after(lastChange.when)) {
					lastChange.when = branchModel.getDate();
					lastChange.who = branchModel.getAuthorIdent().getName();
				}
			}
			return lastChange;
		}

		// default to the repository folder modification date
		return new LastChange(repository.getDirectory().lastModified());
	}

	/**
	 * Retrieves a Java Date from a Git commit.
	 *
	 * @param commit
	 * @return date of the commit or Date(0) if the commit is null
	 */
	public static Date getCommitDate(RevCommit commit) {
		if (commit == null) {
			return new Date(0);
		}
		return new Date(commit.getCommitTime() * 1000L);
	}

	/**
	 * Retrieves a Java Date from a Git commit.
	 *
	 * @param commit
	 * @return date of the commit or Date(0) if the commit is null
	 */
	public static Date getAuthorDate(RevCommit commit) {
		if (commit == null) {
			return new Date(0);
		}
		return commit.getAuthorIdent().getWhen();
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
		RevWalk walk = null;
		try {
			// resolve object id
			ObjectId branchObject;
			if (StringUtils.isEmpty(objectId) || "HEAD".equalsIgnoreCase(objectId)) {
				branchObject = getDefaultBranch(repository);
			} else {
				branchObject = repository.resolve(objectId);
			}
			if (branchObject == null) {
				return null;
			}
			walk = new RevWalk(repository);
			RevCommit rev = walk.parseCommit(branchObject);
			commit = rev;
		} catch (Throwable t) {
			error(t, repository, "{0} failed to get commit {1}", objectId);
		} finally {
			if (walk != null) {
				walk.dispose();
			}
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
	public static byte[] getByteContent(Repository repository, RevTree tree, final String path, boolean throwError) {
		RevWalk rw = new RevWalk(repository);
		TreeWalk tw = new TreeWalk(repository);
		tw.setFilter(PathFilterGroup.createFromStrings(Collections.singleton(path)));
		byte[] content = null;
		try {
			if (tree == null) {
				ObjectId object = getDefaultBranch(repository);
				if (object == null)
					return null;
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
				if (entmode != FileMode.GITLINK) {
					ObjectLoader ldr = repository.open(entid, Constants.OBJ_BLOB);
					content = ldr.getCachedBytes();
				}
			}
		} catch (Throwable t) {
			if (throwError) {
				error(t, repository, "{0} can't find {1} in tree {2}", path, tree.name());
			}
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
	 * @param charsets optional
	 * @return UTF-8 string content
	 */
	public static String getStringContent(Repository repository, RevTree tree, String blobPath, String... charsets) {
		byte[] content = getByteContent(repository, tree, blobPath, true);
		if (content == null) {
			return null;
		}
		return StringUtils.decodeString(content, charsets);
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
			ObjectLoader ldr = repository.open(blob.getId(), Constants.OBJ_BLOB);
			content = ldr.getCachedBytes();
		} catch (Throwable t) {
			error(t, repository, "{0} can't find blob {1}", objectId);
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
	 * @param charsets optional
	 * @return UTF-8 string content
	 */
	public static String getStringContent(Repository repository, String objectId, String... charsets) {
		byte[] content = getByteContent(repository, objectId);
		if (content == null) {
			return null;
		}
		return StringUtils.decodeString(content, charsets);
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
			commit = getCommit(repository, null);
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
			error(e, repository, "{0} failed to get files for commit {1}", commit.getName());
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
		return getFilesInCommit(repository, commit, true);
	}

	/**
	 * Returns the list of files changed in a specified commit. If the
	 * repository does not exist or is empty, an empty list is returned.
	 *
	 * @param repository
	 * @param commit
	 *            if null, HEAD is assumed.
	 * @param calculateDiffStat
	 *            if true, each PathChangeModel will have insertions/deletions
	 * @return list of files changed in a commit
	 */
	public static List<PathChangeModel> getFilesInCommit(Repository repository, RevCommit commit, boolean calculateDiffStat) {
		List<PathChangeModel> list = new ArrayList<PathChangeModel>();
		if (!hasCommits(repository)) {
			return list;
		}
		RevWalk rw = new RevWalk(repository);
		try {
			if (commit == null) {
				ObjectId object = getDefaultBranch(repository);
				commit = rw.parseCommit(object);
			}

			if (commit.getParentCount() == 0) {
				TreeWalk tw = new TreeWalk(repository);
				tw.reset();
				tw.setRecursive(true);
				tw.addTree(commit.getTree());
				while (tw.next()) {
					list.add(new PathChangeModel(tw.getPathString(), tw.getPathString(), 0, tw
							.getRawMode(0), tw.getObjectId(0).getName(), commit.getId().getName(),
							ChangeType.ADD));
				}
				tw.release();
			} else {
				RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
				DiffStatFormatter df = new DiffStatFormatter(commit.getName());
				df.setRepository(repository);
				df.setDiffComparator(RawTextComparator.DEFAULT);
				df.setDetectRenames(true);
				List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
				for (DiffEntry diff : diffs) {
					// create the path change model
					PathChangeModel pcm = PathChangeModel.from(diff, commit.getName());

					if (calculateDiffStat) {
						// update file diffstats
						df.format(diff);
						PathChangeModel pathStat = df.getDiffStat().getPath(pcm.path);
						if (pathStat != null) {
							pcm.insertions = pathStat.insertions;
							pcm.deletions = pathStat.deletions;
						}
					}
					list.add(pcm);
				}
			}
		} catch (Throwable t) {
			error(t, repository, "{0} failed to determine files in commit!");
		} finally {
			rw.dispose();
		}
		return list;
	}

	/**
	 * Returns the list of files changed in a specified commit. If the
	 * repository does not exist or is empty, an empty list is returned.
	 *
	 * @param repository
	 * @param startCommit
	 *            earliest commit
	 * @param endCommit
	 *            most recent commit. if null, HEAD is assumed.
	 * @return list of files changed in a commit range
	 */
	public static List<PathChangeModel> getFilesInRange(Repository repository, String startCommit, String endCommit) {
		List<PathChangeModel> list = new ArrayList<PathChangeModel>();
		if (!hasCommits(repository)) {
			return list;
		}
		try {
			ObjectId startRange = repository.resolve(startCommit);
			ObjectId endRange = repository.resolve(endCommit);
			RevWalk rw = new RevWalk(repository);
			RevCommit start = rw.parseCommit(startRange);
			RevCommit end = rw.parseCommit(endRange);
			list.addAll(getFilesInRange(repository, start, end));
			rw.release();
		} catch (Throwable t) {
			error(t, repository, "{0} failed to determine files in range {1}..{2}!", startCommit, endCommit);
		}
		return list;
	}

	/**
	 * Returns the list of files changed in a specified commit. If the
	 * repository does not exist or is empty, an empty list is returned.
	 *
	 * @param repository
	 * @param startCommit
	 *            earliest commit
	 * @param endCommit
	 *            most recent commit. if null, HEAD is assumed.
	 * @return list of files changed in a commit range
	 */
	public static List<PathChangeModel> getFilesInRange(Repository repository, RevCommit startCommit, RevCommit endCommit) {
		List<PathChangeModel> list = new ArrayList<PathChangeModel>();
		if (!hasCommits(repository)) {
			return list;
		}
		try {
			DiffFormatter df = new DiffFormatter(null);
			df.setRepository(repository);
			df.setDiffComparator(RawTextComparator.DEFAULT);
			df.setDetectRenames(true);

			List<DiffEntry> diffEntries = df.scan(startCommit.getTree(), endCommit.getTree());
			for (DiffEntry diff : diffEntries) {
				PathChangeModel pcm = PathChangeModel.from(diff,  endCommit.getName());
				list.add(pcm);
			}
			Collections.sort(list);
		} catch (Throwable t) {
			error(t, repository, "{0} failed to determine files in range {1}..{2}!", startCommit, endCommit);
		}
		return list;
	}
	/**
	 * Returns the list of files in the repository on the default branch that
	 * match one of the specified extensions. This is a CASE-SENSITIVE search.
	 * If the repository does not exist or is empty, an empty list is returned.
	 *
	 * @param repository
	 * @param extensions
	 * @return list of files in repository with a matching extension
	 */
	public static List<PathModel> getDocuments(Repository repository, List<String> extensions) {
		return getDocuments(repository, extensions, null);
	}

	/**
	 * Returns the list of files in the repository in the specified commit that
	 * match one of the specified extensions. This is a CASE-SENSITIVE search.
	 * If the repository does not exist or is empty, an empty list is returned.
	 *
	 * @param repository
	 * @param extensions
	 * @param objectId
	 * @return list of files in repository with a matching extension
	 */
	public static List<PathModel> getDocuments(Repository repository, List<String> extensions,
			String objectId) {
		List<PathModel> list = new ArrayList<PathModel>();
		if (!hasCommits(repository)) {
			return list;
		}
		RevCommit commit = getCommit(repository, objectId);
		final TreeWalk tw = new TreeWalk(repository);
		try {
			tw.addTree(commit.getTree());
			if (extensions != null && extensions.size() > 0) {
				List<TreeFilter> suffixFilters = new ArrayList<TreeFilter>();
				for (String extension : extensions) {
					if (extension.charAt(0) == '.') {
						suffixFilters.add(PathSuffixFilter.create(extension));
					} else {
						// escape the . since this is a regexp filter
						suffixFilters.add(PathSuffixFilter.create("." + extension));
					}
				}
				TreeFilter filter;
				if (suffixFilters.size() == 1) {
					filter = suffixFilters.get(0);
				} else {
					filter = OrTreeFilter.create(suffixFilters);
				}
				tw.setFilter(filter);
				tw.setRecursive(true);
			}
			while (tw.next()) {
				list.add(getPathModel(tw, null, commit));
			}
		} catch (IOException e) {
			error(e, repository, "{0} failed to get documents for commit {1}", commit.getName());
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
		ObjectId objectId = tw.getObjectId(0);
		try {
			if (!tw.isSubtree() && (tw.getFileMode(0) != FileMode.GITLINK)) {
				size = tw.getObjectReader().getObjectSize(objectId, Constants.OBJ_BLOB);
			}
		} catch (Throwable t) {
			error(t, null, "failed to retrieve blob size for " + tw.getPathString());
		}
		return new PathModel(name, tw.getPathString(), size, tw.getFileMode(0).getBits(),
				objectId.getName(), commit.getName());
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
			return "symlink";
		} else if (FileMode.GITLINK.equals(mode)) {
			return "submodule";
		}
		return "missing";
	}

	/**
	 * Returns a list of commits since the minimum date starting from the
	 * specified object id.
	 *
	 * @param repository
	 * @param objectId
	 *            if unspecified, HEAD is assumed.
	 * @param minimumDate
	 * @return list of commits
	 */
	public static List<RevCommit> getRevLog(Repository repository, String objectId, Date minimumDate) {
		List<RevCommit> list = new ArrayList<RevCommit>();
		if (!hasCommits(repository)) {
			return list;
		}
		try {
			// resolve branch
			ObjectId branchObject;
			if (StringUtils.isEmpty(objectId)) {
				branchObject = getDefaultBranch(repository);
			} else {
				branchObject = repository.resolve(objectId);
			}

			RevWalk rw = new RevWalk(repository);
			rw.markStart(rw.parseCommit(branchObject));
			rw.setRevFilter(CommitTimeRevFilter.after(minimumDate));
			Iterable<RevCommit> revlog = rw;
			for (RevCommit rev : revlog) {
				list.add(rev);
			}
			rw.dispose();
		} catch (Throwable t) {
			error(t, repository, "{0} failed to get {1} revlog for minimum date {2}", objectId,
					minimumDate);
		}
		return list;
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
		return getRevLog(repository, null, 0, maxCount);
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
			// resolve branch
			ObjectId startRange = null;
			ObjectId endRange;
			if (StringUtils.isEmpty(objectId)) {
				endRange = getDefaultBranch(repository);
			} else {
				if( objectId.contains("..") ) {
					// range expression
					String[] parts = objectId.split("\\.\\.");
					startRange = repository.resolve(parts[0]);
					endRange = repository.resolve(parts[1]);
				} else {
					// objectid
					endRange= repository.resolve(objectId);
				}
			}
			if (endRange == null) {
				return list;
			}

			RevWalk rw = new RevWalk(repository);
			rw.markStart(rw.parseCommit(endRange));
			if (startRange != null) {
				rw.markUninteresting(rw.parseCommit(startRange));
			}
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
			error(t, repository, "{0} failed to get {1} revlog for path {2}", objectId, path);
		}
		return list;
	}

	/**
	 * Returns a list of commits for the repository within the range specified
	 * by startRangeId and endRangeId. If the repository does not exist or is
	 * empty, an empty list is returned.
	 *
	 * @param repository
	 * @param startRangeId
	 *            the first commit (not included in results)
	 * @param endRangeId
	 *            the end commit (included in results)
	 * @return a list of commits
	 */
	public static List<RevCommit> getRevLog(Repository repository, String startRangeId,
			String endRangeId) {
		List<RevCommit> list = new ArrayList<RevCommit>();
		if (!hasCommits(repository)) {
			return list;
		}
		try {
			ObjectId endRange = repository.resolve(endRangeId);
			ObjectId startRange = repository.resolve(startRangeId);

			RevWalk rw = new RevWalk(repository);
			rw.markStart(rw.parseCommit(endRange));
			if (startRange.equals(ObjectId.zeroId())) {
				// maybe this is a tag or an orphan branch
				list.add(rw.parseCommit(endRange));
				rw.dispose();
				return list;
			} else {
				rw.markUninteresting(rw.parseCommit(startRange));
			}

			Iterable<RevCommit> revlog = rw;
			for (RevCommit rev : revlog) {
				list.add(rev);
			}
			rw.dispose();
		} catch (Throwable t) {
			error(t, repository, "{0} failed to get revlog for {1}..{2}", startRangeId, endRangeId);
		}
		return list;
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
			String value, final com.gitblit.Constants.SearchType type, int offset, int maxCount) {
		List<RevCommit> list = new ArrayList<RevCommit>();
		if (StringUtils.isEmpty(value)) {
			return list;
		}
		if (maxCount == 0) {
			return list;
		}
		if (!hasCommits(repository)) {
			return list;
		}
		final String lcValue = value.toLowerCase();
		try {
			// resolve branch
			ObjectId branchObject;
			if (StringUtils.isEmpty(objectId)) {
				branchObject = getDefaultBranch(repository);
			} else {
				branchObject = repository.resolve(objectId);
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
			rw.markStart(rw.parseCommit(branchObject));
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
			error(t, repository, "{0} failed to {1} search revlogs for {2}", type.name(), value);
		}
		return list;
	}

	/**
	 * Returns the default branch to use for a repository. Normally returns
	 * whatever branch HEAD points to, but if HEAD points to nothing it returns
	 * the most recently updated branch.
	 *
	 * @param repository
	 * @return the objectid of a branch
	 * @throws Exception
	 */
	public static ObjectId getDefaultBranch(Repository repository) throws Exception {
		ObjectId object = repository.resolve(Constants.HEAD);
		if (object == null) {
			// no HEAD
			// perhaps non-standard repository, try local branches
			List<RefModel> branchModels = getLocalBranches(repository, true, -1);
			if (branchModels.size() > 0) {
				// use most recently updated branch
				RefModel branch = null;
				Date lastDate = new Date(0);
				for (RefModel branchModel : branchModels) {
					if (branchModel.getDate().after(lastDate)) {
						branch = branchModel;
						lastDate = branch.getDate();
					}
				}
				object = branch.getReferencedObjectId();
			}
		}
		return object;
	}

	/**
	 * Returns the target of the symbolic HEAD reference for a repository.
	 * Normally returns a branch reference name, but when HEAD is detached,
	 * the commit is matched against the known tags. The most recent matching
	 * tag ref name will be returned if it references the HEAD commit. If
	 * no match is found, the SHA1 is returned.
	 *
	 * @param repository
	 * @return the ref name or the SHA1 for a detached HEAD
	 */
	public static String getHEADRef(Repository repository) {
		String target = null;
		try {
			target = repository.getFullBranch();
		} catch (Throwable t) {
			error(t, repository, "{0} failed to get symbolic HEAD target");
		}
		return target;
	}

	/**
	 * Sets the symbolic ref HEAD to the specified target ref. The
	 * HEAD will be detached if the target ref is not a branch.
	 *
	 * @param repository
	 * @param targetRef
	 * @return true if successful
	 */
	public static boolean setHEADtoRef(Repository repository, String targetRef) {
		try {
			 // detach HEAD if target ref is not a branch
			boolean detach = !targetRef.startsWith(Constants.R_HEADS);
			RefUpdate.Result result;
			RefUpdate head = repository.updateRef(Constants.HEAD, detach);
			if (detach) { // Tag
				RevCommit commit = getCommit(repository, targetRef);
				head.setNewObjectId(commit.getId());
				result = head.forceUpdate();
			} else {
				result = head.link(targetRef);
			}
			switch (result) {
			case NEW:
			case FORCED:
			case NO_CHANGE:
			case FAST_FORWARD:
				return true;
			default:
				LOGGER.error(MessageFormat.format("{0} HEAD update to {1} returned result {2}",
						repository.getDirectory().getAbsolutePath(), targetRef, result));
			}
		} catch (Throwable t) {
			error(t, repository, "{0} failed to set HEAD to {1}", targetRef);
		}
		return false;
	}

	/**
	 * Sets the local branch ref to point to the specified commit id.
	 *
	 * @param repository
	 * @param branch
	 * @param commitId
	 * @return true if successful
	 */
	public static boolean setBranchRef(Repository repository, String branch, String commitId) {
		String branchName = branch;
		if (!branchName.startsWith(Constants.R_REFS)) {
			branchName = Constants.R_HEADS + branch;
		}

		try {
			RefUpdate refUpdate = repository.updateRef(branchName, false);
			refUpdate.setNewObjectId(ObjectId.fromString(commitId));
			RefUpdate.Result result = refUpdate.forceUpdate();

			switch (result) {
			case NEW:
			case FORCED:
			case NO_CHANGE:
			case FAST_FORWARD:
				return true;
			default:
				LOGGER.error(MessageFormat.format("{0} {1} update to {2} returned result {3}",
						repository.getDirectory().getAbsolutePath(), branchName, commitId, result));
			}
		} catch (Throwable t) {
			error(t, repository, "{0} failed to set {1} to {2}", branchName, commitId);
		}
		return false;
	}

	/**
	 * Deletes the specified branch ref.
	 *
	 * @param repository
	 * @param branch
	 * @return true if successful
	 */
	public static boolean deleteBranchRef(Repository repository, String branch) {
		String branchName = branch;
		if (!branchName.startsWith(Constants.R_HEADS)) {
			branchName = Constants.R_HEADS + branch;
		}

		try {
			RefUpdate refUpdate = repository.updateRef(branchName, false);
			refUpdate.setForceUpdate(true);
			RefUpdate.Result result = refUpdate.delete();
			switch (result) {
			case NEW:
			case FORCED:
			case NO_CHANGE:
			case FAST_FORWARD:
				return true;
			default:
				LOGGER.error(MessageFormat.format("{0} failed to delete to {1} returned result {2}",
						repository.getDirectory().getAbsolutePath(), branchName, result));
			}
		} catch (Throwable t) {
			error(t, repository, "{0} failed to delete {1}", branchName);
		}
		return false;
	}

	/**
	 * Get the full branch and tag ref names for any potential HEAD targets.
	 *
	 * @param repository
	 * @return a list of ref names
	 */
	public static List<String> getAvailableHeadTargets(Repository repository) {
		List<String> targets = new ArrayList<String>();
		for (RefModel branchModel : JGitUtils.getLocalBranches(repository, true, -1)) {
			targets.add(branchModel.getName());
		}

		for (RefModel tagModel : JGitUtils.getTags(repository, true, -1)) {
			targets.add(tagModel.getName());
		}
		return targets;
	}

	/**
	 * Returns all refs grouped by their associated object id.
	 *
	 * @param repository
	 * @return all refs grouped by their referenced object id
	 */
	public static Map<ObjectId, List<RefModel>> getAllRefs(Repository repository) {
		return getAllRefs(repository, true);
	}

	/**
	 * Returns all refs grouped by their associated object id.
	 *
	 * @param repository
	 * @param includeRemoteRefs
	 * @return all refs grouped by their referenced object id
	 */
	public static Map<ObjectId, List<RefModel>> getAllRefs(Repository repository, boolean includeRemoteRefs) {
		List<RefModel> list = getRefs(repository, org.eclipse.jgit.lib.RefDatabase.ALL, true, -1);
		Map<ObjectId, List<RefModel>> refs = new HashMap<ObjectId, List<RefModel>>();
		for (RefModel ref : list) {
			if (!includeRemoteRefs && ref.getName().startsWith(Constants.R_REMOTES)) {
				continue;
			}
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
	 * Returns the list of tags in the repository. If repository does not exist
	 * or is empty, an empty list is returned.
	 *
	 * @param repository
	 * @param fullName
	 *            if true, /refs/tags/yadayadayada is returned. If false,
	 *            yadayadayada is returned.
	 * @param maxCount
	 *            if < 0, all tags are returned
	 * @param offset
	 *            if maxCount provided sets the starting point of the records to return
	 * @return list of tags
	 */
	public static List<RefModel> getTags(Repository repository, boolean fullName, int maxCount, int offset) {
		return getRefs(repository, Constants.R_TAGS, fullName, maxCount, offset);
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
	 * Returns the list of refs in the specified base ref. If repository does
	 * not exist or is empty, an empty list is returned.
	 *
	 * @param repository
	 * @param fullName
	 *            if true, /refs/yadayadayada is returned. If false,
	 *            yadayadayada is returned.
	 * @return list of refs
	 */
	public static List<RefModel> getRefs(Repository repository, String baseRef) {
		return getRefs(repository, baseRef, true, -1);
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
		return getRefs(repository, refs, fullName, maxCount, 0);
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
	 * @param offset
	 *            if maxCount provided sets the starting point of the records to return
	 * @return list of references
	 */
	private static List<RefModel> getRefs(Repository repository, String refs, boolean fullName,
			int maxCount, int offset) {
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
				if (offset < 0) {
					offset = 0;
				}
				int endIndex = offset + maxCount;
				if (endIndex > list.size()) {
					endIndex = list.size();
				}
				list = new ArrayList<RefModel>(list.subList(offset, endIndex));
			}
		} catch (IOException e) {
			error(e, repository, "{0} failed to retrieve {1}", refs);
		}
		return list;
	}

	/**
	 * Returns a RefModel for the gh-pages branch in the repository. If the
	 * branch can not be found, null is returned.
	 *
	 * @param repository
	 * @return a refmodel for the gh-pages branch or null
	 */
	public static RefModel getPagesBranch(Repository repository) {
		return getBranch(repository, "gh-pages");
	}

	/**
	 * Returns a RefModel for a specific branch name in the repository. If the
	 * branch can not be found, null is returned.
	 *
	 * @param repository
	 * @return a refmodel for the branch or null
	 */
	public static RefModel getBranch(Repository repository, String name) {
		RefModel branch = null;
		try {
			// search for the branch in local heads
			for (RefModel ref : JGitUtils.getLocalBranches(repository, false, -1)) {
				if (ref.reference.getName().endsWith(name)) {
					branch = ref;
					break;
				}
			}

			// search for the branch in remote heads
			if (branch == null) {
				for (RefModel ref : JGitUtils.getRemoteBranches(repository, false, -1)) {
					if (ref.reference.getName().endsWith(name)) {
						branch = ref;
						break;
					}
				}
			}
		} catch (Throwable t) {
			LOGGER.error(MessageFormat.format("Failed to find {0} branch!", name), t);
		}
		return branch;
	}

	/**
	 * Returns the list of submodules for this repository.
	 *
	 * @param repository
	 * @param commit
	 * @return list of submodules
	 */
	public static List<SubmoduleModel> getSubmodules(Repository repository, String commitId) {
		RevCommit commit = getCommit(repository, commitId);
		return getSubmodules(repository, commit.getTree());
	}

	/**
	 * Returns the list of submodules for this repository.
	 *
	 * @param repository
	 * @param commit
	 * @return list of submodules
	 */
	public static List<SubmoduleModel> getSubmodules(Repository repository, RevTree tree) {
		List<SubmoduleModel> list = new ArrayList<SubmoduleModel>();
		byte [] blob = getByteContent(repository, tree, ".gitmodules", false);
		if (blob == null) {
			return list;
		}
		try {
			BlobBasedConfig config = new BlobBasedConfig(repository.getConfig(), blob);
			for (String module : config.getSubsections("submodule")) {
				String path = config.getString("submodule", module, "path");
				String url = config.getString("submodule", module, "url");
				list.add(new SubmoduleModel(module, path, url));
			}
		} catch (ConfigInvalidException e) {
			LOGGER.error("Failed to load .gitmodules file for " + repository.getDirectory(), e);
		}
		return list;
	}

	/**
	 * Returns the submodule definition for the specified path at the specified
	 * commit.  If no module is defined for the path, null is returned.
	 *
	 * @param repository
	 * @param commit
	 * @param path
	 * @return a submodule definition or null if there is no submodule
	 */
	public static SubmoduleModel getSubmoduleModel(Repository repository, String commitId, String path) {
		for (SubmoduleModel model : getSubmodules(repository, commitId)) {
			if (model.path.equals(path)) {
				return model;
			}
		}
		return null;
	}

	public static String getSubmoduleCommitId(Repository repository, String path, RevCommit commit) {
		String commitId = null;
		RevWalk rw = new RevWalk(repository);
		TreeWalk tw = new TreeWalk(repository);
		tw.setFilter(PathFilterGroup.createFromStrings(Collections.singleton(path)));
		try {
			tw.reset(commit.getTree());
			while (tw.next()) {
				if (tw.isSubtree() && !path.equals(tw.getPathString())) {
					tw.enterSubtree();
					continue;
				}
				if (FileMode.GITLINK == tw.getFileMode(0)) {
					commitId = tw.getObjectId(0).getName();
					break;
				}
			}
		} catch (Throwable t) {
			error(t, repository, "{0} can't find {1} in commit {2}", path, commit.name());
		} finally {
			rw.dispose();
			tw.release();
		}
		return commitId;
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
			// flat notes list
			String notePath = commit.getName();
			String text = getStringContent(repository, notesTree, notePath);
			if (!StringUtils.isEmpty(text)) {
				List<RevCommit> history = getRevLog(repository, notesRef.getName(), notePath, 0, -1);
				RefModel noteRef = new RefModel(notesRef.displayName, null, history.get(history
						.size() - 1));
				GitNote gitNote = new GitNote(noteRef, text);
				list.add(gitNote);
				continue;
			}

			// folder structure
			StringBuilder sb = new StringBuilder(commit.getName());
			sb.insert(2, '/');
			notePath = sb.toString();
			text = getStringContent(repository, notesTree, notePath);
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
	 * this method creates an incremental revision number as a tag according to
	 * the amount of already existing tags, which start with a defined prefix.
	 *
	 * @param repository
	 * @param objectId
	 * @param tagger
	 * @param prefix
	 * @param intPattern
	 * @param message
	 * @return true if operation was successful, otherwise false
	 */
	public static boolean createIncrementalRevisionTag(Repository repository,
			String objectId, PersonIdent tagger, String prefix, String intPattern, String message) {
		boolean result = false;
		Iterator<Entry<String, Ref>> iterator = repository.getTags().entrySet().iterator();
		long lastRev = 0;
		while (iterator.hasNext()) {
			Entry<String, Ref> entry = iterator.next();
			if (entry.getKey().startsWith(prefix)) {
				try {
					long val = Long.parseLong(entry.getKey().substring(prefix.length()));
					if (val > lastRev) {
						lastRev = val;
					}
				} catch (Exception e) {
					// this tag is NOT an incremental revision tag
				}
			}
		}
		DecimalFormat df = new DecimalFormat(intPattern);
		result = createTag(repository, objectId, tagger, prefix + df.format((lastRev + 1)), message);
		return result;
	}

	/**
	 * creates a tag in a repository
	 *
	 * @param repository
	 * @param objectId, the ref the tag points towards
	 * @param tagger, the person tagging the object
	 * @param tag, the string label
	 * @param message, the string message
	 * @return boolean, true if operation was successful, otherwise false
	 */
	public static boolean createTag(Repository repository, String objectId, PersonIdent tagger, String tag, String message) {
		try {
			Git gitClient = Git.open(repository.getDirectory());
			TagCommand tagCommand = gitClient.tag();
			tagCommand.setTagger(tagger);
			tagCommand.setMessage(message);
			if (objectId != null) {
				RevObject revObj = getCommit(repository, objectId);
				tagCommand.setObjectId(revObj);
			}
			tagCommand.setName(tag);
			Ref call = tagCommand.call();
			return call != null ? true : false;
		} catch (Exception e) {
			error(e, repository, "Failed to create tag {1} in repository {0}", objectId, tag);
		}
		return false;
	}

	/**
	 * Create an orphaned branch in a repository.
	 *
	 * @param repository
	 * @param branchName
	 * @param author
	 *            if unspecified, Gitblit will be the author of this new branch
	 * @return true if successful
	 */
	public static boolean createOrphanBranch(Repository repository, String branchName,
			PersonIdent author) {
		boolean success = false;
		String message = "Created branch " + branchName;
		if (author == null) {
			author = new PersonIdent("Gitblit", "gitblit@localhost");
		}
		try {
			ObjectInserter odi = repository.newObjectInserter();
			try {
				// Create a blob object to insert into a tree
				ObjectId blobId = odi.insert(Constants.OBJ_BLOB,
						message.getBytes(Constants.CHARACTER_ENCODING));

				// Create a tree object to reference from a commit
				TreeFormatter tree = new TreeFormatter();
				tree.append(".branch", FileMode.REGULAR_FILE, blobId);
				ObjectId treeId = odi.insert(tree);

				// Create a commit object
				CommitBuilder commit = new CommitBuilder();
				commit.setAuthor(author);
				commit.setCommitter(author);
				commit.setEncoding(Constants.CHARACTER_ENCODING);
				commit.setMessage(message);
				commit.setTreeId(treeId);

				// Insert the commit into the repository
				ObjectId commitId = odi.insert(commit);
				odi.flush();

				RevWalk revWalk = new RevWalk(repository);
				try {
					RevCommit revCommit = revWalk.parseCommit(commitId);
					if (!branchName.startsWith("refs/")) {
						branchName = "refs/heads/" + branchName;
					}
					RefUpdate ru = repository.updateRef(branchName);
					ru.setNewObjectId(commitId);
					ru.setRefLogMessage("commit: " + revCommit.getShortMessage(), false);
					Result rc = ru.forceUpdate();
					switch (rc) {
					case NEW:
					case FORCED:
					case FAST_FORWARD:
						success = true;
						break;
					default:
						success = false;
					}
				} finally {
					revWalk.release();
				}
			} finally {
				odi.release();
			}
		} catch (Throwable t) {
			error(t, repository, "Failed to create orphan branch {1} in repository {0}", branchName);
		}
		return success;
	}

	/**
	 * Reads the sparkleshare id, if present, from the repository.
	 *
	 * @param repository
	 * @return an id or null
	 */
	public static String getSparkleshareId(Repository repository) {
		byte[] content = getByteContent(repository, null, ".sparkleshare", false);
		if (content == null) {
			return null;
		}
		return StringUtils.decodeString(content);
	}

	/**
	 * Automatic repair of (some) invalid refspecs.  These are the result of a
	 * bug in JGit cloning where a double forward-slash was injected.  :(
	 *
	 * @param repository
	 * @return true, if the refspecs were repaired
	 */
	public static boolean repairFetchSpecs(Repository repository) {
		StoredConfig rc = repository.getConfig();

		// auto-repair broken fetch ref specs
		for (String name : rc.getSubsections("remote")) {
			int invalidSpecs = 0;
			int repairedSpecs = 0;
			List<String> specs = new ArrayList<String>();
			for (String spec : rc.getStringList("remote", name, "fetch")) {
				try {
					RefSpec rs = new RefSpec(spec);
					// valid spec
					specs.add(spec);
				} catch (IllegalArgumentException e) {
					// invalid spec
					invalidSpecs++;
					if (spec.contains("//")) {
						// auto-repair this known spec bug
						spec = spec.replace("//", "/");
						specs.add(spec);
						repairedSpecs++;
					}
				}
			}

			if (invalidSpecs == repairedSpecs && repairedSpecs > 0) {
				// the fetch specs were automatically repaired
				rc.setStringList("remote", name, "fetch", specs);
				try {
					rc.save();
					rc.load();
					LOGGER.debug("repaired {} invalid fetch refspecs for {}", repairedSpecs, repository.getDirectory());
					return true;
				} catch (Exception e) {
					LOGGER.error(null, e);
				}
			} else if (invalidSpecs > 0) {
				LOGGER.error("mirror executor found {} invalid fetch refspecs for {}", invalidSpecs, repository.getDirectory());
			}
		}
		return false;
	}

	/**
	 * Returns true if the commit identified by commitId is an ancestor or the
	 * the commit identified by tipId.
	 *
	 * @param repository
	 * @param commitId
	 * @param tipId
	 * @return true if there is the commit is an ancestor of the tip
	 */
	public static boolean isMergedInto(Repository repository, String commitId, String tipId) {
		try {
			return isMergedInto(repository, repository.resolve(commitId), repository.resolve(tipId));
		} catch (Exception e) {
			LOGGER.error("Failed to determine isMergedInto", e);
		}
		return false;
	}

	/**
	 * Returns true if the commit identified by commitId is an ancestor or the
	 * the commit identified by tipId.
	 *
	 * @param repository
	 * @param commitId
	 * @param tipId
	 * @return true if there is the commit is an ancestor of the tip
	 */
	public static boolean isMergedInto(Repository repository, ObjectId commitId, ObjectId tipCommitId) {
		// traverse the revlog looking for a commit chain between the endpoints
		RevWalk rw = new RevWalk(repository);
		try {
			// must re-lookup RevCommits to workaround undocumented RevWalk bug
			RevCommit tip = rw.lookupCommit(tipCommitId);
			RevCommit commit = rw.lookupCommit(commitId);
			return rw.isMergedInto(commit, tip);
		} catch (Exception e) {
			LOGGER.error("Failed to determine isMergedInto", e);
		} finally {
			rw.dispose();
		}
		return false;
	}

	/**
	 * Returns the merge base of two commits or null if there is no common
	 * ancestry.
	 *
	 * @param repository
	 * @param commitIdA
	 * @param commitIdB
	 * @return the commit id of the merge base or null if there is no common base
	 */
	public static String getMergeBase(Repository repository, ObjectId commitIdA, ObjectId commitIdB) {
		RevWalk rw = new RevWalk(repository);
		try {
			RevCommit a = rw.lookupCommit(commitIdA);
			RevCommit b = rw.lookupCommit(commitIdB);

			rw.setRevFilter(RevFilter.MERGE_BASE);
			rw.markStart(a);
			rw.markStart(b);
			RevCommit mergeBase = rw.next();
			if (mergeBase == null) {
				return null;
			}
			return mergeBase.getName();
		} catch (Exception e) {
			LOGGER.error("Failed to determine merge base", e);
		} finally {
			rw.dispose();
		}
		return null;
	}

	public static enum MergeStatus {
		NOT_MERGEABLE, FAILED, ALREADY_MERGED, MERGEABLE, MERGED;
	}


	/**
	 * Determines if we can cleanly merge one branch into another.  Returns true
	 * if we can merge without conflict, otherwise returns false.
	 *
	 * @param repository
	 * @param src
	 * @param toBranch
	 * @param mergeType
	 * 				Defines the integration strategy to use for merging.
	 * @return true if we can merge without conflict
	 */
	public static MergeStatus canMerge(Repository repository, String src, String toBranch, MergeType mergeType) {
		IntegrationStrategy strategy = IntegrationStrategyFactory.create(mergeType, repository, src, toBranch);
		return strategy.canMerge();
	}


	public static class MergeResult {
		public final MergeStatus status;
		public final String sha;

		MergeResult(MergeStatus status, String sha) {
			this.status = status;
			this.sha = sha;
		}
	}

	/**
	 * Tries to merge a commit into a branch.  If there are conflicts, the merge
	 * will fail.
	 *
	 * @param repository
	 * @param src
	 * @param toBranch
	 * @param mergeType 
	 * 				Defines the integration strategy to use for merging.
	 * @param committer
	 * @param message
	 * @return the merge result
	 */
	public static MergeResult merge(Repository repository, String src, String toBranch, MergeType mergeType,
			PersonIdent committer, String message) {

		if (!toBranch.startsWith(Constants.R_REFS)) {
			// branch ref doesn't start with ref, assume this is a branch head
			toBranch = Constants.R_HEADS + toBranch;
		}

		IntegrationStrategy strategy = IntegrationStrategyFactory.create(mergeType, repository, src, toBranch);
		MergeResult mergeResult = strategy.merge(committer, message);

		if (mergeResult.status != MergeStatus.MERGED) {
			return mergeResult;
		}

		try {
			// Update the integration branch ref
			RefUpdate mergeRefUpdate = repository.updateRef(toBranch);
			mergeRefUpdate.setNewObjectId(strategy.getMergeCommit());
			mergeRefUpdate.setRefLogMessage(strategy.getRefLogMessage(), false);
			mergeRefUpdate.setExpectedOldObjectId(strategy.branchTip);
			RefUpdate.Result rc = mergeRefUpdate.update();
			switch (rc) {
			case FAST_FORWARD:
				// successful, clean merge
				break;
			default:
				mergeResult = new MergeResult(MergeStatus.FAILED, null);
				throw new GitBlitException(MessageFormat.format("Unexpected result \"{0}\" when {1} in {2}",
						rc.name(), strategy.getOperationMessage(), repository.getDirectory()));
			}
		} catch (IOException e) {
			LOGGER.error("Failed to merge", e);
		}

		return mergeResult;
	}


	private static abstract class IntegrationStrategy {
		Repository repository;
		String src;
		String toBranch;

		RevWalk revWalk;
		RevCommit branchTip;
		RevCommit srcTip;

		RevCommit mergeCommit;
		String refLogMessage;
		String operationMessage;

		RevCommit getMergeCommit() {
			return mergeCommit;
		}

		String getRefLogMessage() {
			return refLogMessage;
		}

		String getOperationMessage() {
			return operationMessage;
		}

		IntegrationStrategy(Repository repository, String src, String toBranch) {
			this.repository = repository;
			this.src = src;
			this.toBranch = toBranch;
		}

		void prepare() throws IOException {
			if (revWalk == null) revWalk = new RevWalk(repository);
			branchTip = revWalk.lookupCommit(repository.resolve(toBranch));
			srcTip = revWalk.lookupCommit(repository.resolve(src));
		}


		abstract MergeStatus _canMerge() throws IOException;


		MergeStatus canMerge() {
			try {
				prepare();
				if (revWalk.isMergedInto(srcTip, branchTip)) {
					// already merged
					return MergeStatus.ALREADY_MERGED;
				}
				// determined by specific integration strategy
				return _canMerge();

			} catch (IOException e) {
				LOGGER.error("Failed to determine canMerge", e);
			} finally {
				if (revWalk != null) {
					revWalk.release();
				}
			}

			return MergeStatus.NOT_MERGEABLE;
		}


		abstract MergeResult _merge(PersonIdent committer, String message) throws IOException;


		MergeResult merge(PersonIdent committer, String message) {
			try {
				prepare();
				if (revWalk.isMergedInto(srcTip, branchTip)) {
					// already merged
					return new MergeResult(MergeStatus.ALREADY_MERGED, null);
				}
				// determined by specific integration strategy
				return _merge(committer, message);

			} catch (IOException e) {
				LOGGER.error("Failed to merge", e);
			} finally {
				if (revWalk != null) {
					revWalk.release();
				}
			}

			return new MergeResult(MergeStatus.FAILED, null);
		}
	}


	private static class FastForwardOnly extends IntegrationStrategy {
		FastForwardOnly(Repository repository, String src, String toBranch) {
			super(repository, src, toBranch);
		}

		@Override
		MergeStatus _canMerge() throws IOException {
			if (revWalk.isMergedInto(branchTip, srcTip)) {
				// fast-forward
				return MergeStatus.MERGEABLE;
			}

			return MergeStatus.NOT_MERGEABLE;
		}

		@Override
		MergeResult _merge(PersonIdent committer, String message) throws IOException {
			if (! revWalk.isMergedInto(branchTip, srcTip)) {
				// is not fast-forward
				return new MergeResult(MergeStatus.FAILED, null);
			}

			mergeCommit = srcTip;
			refLogMessage = "merge " + src + ": Fast-forward";
			MessageFormat.format("fast-forwarding {0} to commit {1}", srcTip.getName(), branchTip.getName());

			return new MergeResult(MergeStatus.MERGED, srcTip.getName());
		}
	}

	private static class MergeIfNecessary extends IntegrationStrategy {
		MergeIfNecessary(Repository repository, String src, String toBranch) {
			super(repository, src, toBranch);
		}

		@Override
		MergeStatus _canMerge() throws IOException {
			if (revWalk.isMergedInto(branchTip, srcTip)) {
				// fast-forward
				return MergeStatus.MERGEABLE;
			}

			RecursiveMerger merger = (RecursiveMerger) MergeStrategy.RECURSIVE.newMerger(repository, true);
			boolean canMerge = merger.merge(branchTip, srcTip);
			if (canMerge) {
				return MergeStatus.MERGEABLE;
			}

			return MergeStatus.NOT_MERGEABLE;
		}

		@Override
		MergeResult _merge(PersonIdent committer, String message) throws IOException {
			if (revWalk.isMergedInto(branchTip, srcTip)) {
				// fast-forward
				mergeCommit = srcTip;
				refLogMessage = "merge " + src + ": Fast-forward";
				MessageFormat.format("fast-forwarding {0} to commit {1}", branchTip.getName(), srcTip.getName());

				return new MergeResult(MergeStatus.MERGED, srcTip.getName());
			}

			RecursiveMerger merger = (RecursiveMerger) MergeStrategy.RECURSIVE.newMerger(repository, true);
			boolean merged = merger.merge(branchTip, srcTip);
			if (merged) {
				// create a merge commit and a reference to track the merge commit
				ObjectId treeId = merger.getResultTreeId();
				ObjectInserter odi = repository.newObjectInserter();
				try {
					// Create a commit object
					CommitBuilder commitBuilder = new CommitBuilder();
					commitBuilder.setCommitter(committer);
					commitBuilder.setAuthor(committer);
					commitBuilder.setEncoding(Constants.CHARSET);
					if (StringUtils.isEmpty(message)) {
						message = MessageFormat.format("merge {0} into {1}", srcTip.getName(), branchTip.getName());
					}
					commitBuilder.setMessage(message);
					commitBuilder.setParentIds(branchTip.getId(), srcTip.getId());
					commitBuilder.setTreeId(treeId);

					// Insert the merge commit into the repository
					ObjectId mergeCommitId = odi.insert(commitBuilder);
					odi.flush();

					mergeCommit = revWalk.parseCommit(mergeCommitId);
					refLogMessage = "commit: " + mergeCommit.getShortMessage();
					MessageFormat.format("merging commit {0} into {1}", srcTip.getName(), branchTip.getName());

					// return the merge commit id
					return new MergeResult(MergeStatus.MERGED, mergeCommitId.getName());
				} finally {
					odi.release();
				}
			}
			return new MergeResult(MergeStatus.FAILED, null);
		}
	}

	private static class MergeAlways extends IntegrationStrategy {
		MergeAlways(Repository repository, String src, String toBranch) {
			super(repository, src, toBranch);
		}

		@Override
		MergeStatus _canMerge() throws IOException {
			RecursiveMerger merger = (RecursiveMerger) MergeStrategy.RECURSIVE.newMerger(repository, true);
			boolean canMerge = merger.merge(branchTip, srcTip);
			if (canMerge) {
				return MergeStatus.MERGEABLE;
			}

			return MergeStatus.NOT_MERGEABLE;
		}

		@Override
		MergeResult _merge(PersonIdent committer, String message) throws IOException {
			RecursiveMerger merger = (RecursiveMerger) MergeStrategy.RECURSIVE.newMerger(repository, true);
			boolean merged = merger.merge(branchTip, srcTip);
			if (merged) {
				// create a merge commit and a reference to track the merge commit
				ObjectId treeId = merger.getResultTreeId();
				ObjectInserter odi = repository.newObjectInserter();
				try {
					// Create a commit object
					CommitBuilder commitBuilder = new CommitBuilder();
					commitBuilder.setCommitter(committer);
					commitBuilder.setAuthor(committer);
					commitBuilder.setEncoding(Constants.CHARSET);
					if (StringUtils.isEmpty(message)) {
						message = MessageFormat.format("merge {0} into {1}", srcTip.getName(), branchTip.getName());
					}
					commitBuilder.setMessage(message);
					commitBuilder.setParentIds(branchTip.getId(), srcTip.getId());
					commitBuilder.setTreeId(treeId);

					// Insert the merge commit into the repository
					ObjectId mergeCommitId = odi.insert(commitBuilder);
					odi.flush();

					mergeCommit = revWalk.parseCommit(mergeCommitId);
					refLogMessage = "commit: " + mergeCommit.getShortMessage();
					MessageFormat.format("merging commit {0} into {1}", srcTip.getName(), branchTip.getName());

					// return the merge commit id
					return new MergeResult(MergeStatus.MERGED, mergeCommitId.getName());
				} finally {
					odi.release();
				}
			}

			return new MergeResult(MergeStatus.FAILED, null);
		}
	}

	private static class IntegrationStrategyFactory {
		static IntegrationStrategy create(MergeType mergeType, Repository repository, String src, String toBranch) {
			switch(mergeType) {
			case FAST_FORWARD_ONLY:
				return new FastForwardOnly(repository, src, toBranch);
			case MERGE_IF_NECESSARY:
				return new MergeIfNecessary(repository, src, toBranch);
			case MERGE_ALWAYS:
				return new MergeAlways(repository, src, toBranch);
			}
			return null;
		}
	}
}
