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
package com.gitblit.utils;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
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
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.models.PushLogEntry;
import com.gitblit.models.RefModel;
import com.gitblit.models.UserModel;

/**
 * Utility class for maintaining a pushlog within a git repository on an
 * orphan branch.
 * 
 * @author James Moger
 *
 */
public class PushLogUtils {
	
	public static final String GB_PUSHES = "refs/gitblit/pushes";

	static final Logger LOGGER = LoggerFactory.getLogger(PushLogUtils.class);

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
	 * Returns a RefModel for the gb-pushes branch in the repository. If the
	 * branch can not be found, null is returned.
	 * 
	 * @param repository
	 * @return a refmodel for the gb-pushes branch or null
	 */
	public static RefModel getPushLogBranch(Repository repository) {
		List<RefModel> refs = JGitUtils.getRefs(repository, com.gitblit.Constants.R_GITBLIT);
		for (RefModel ref : refs) {
			if (ref.reference.getName().equals(GB_PUSHES)) {
				return ref;
			}
		}
		return null;
	}
	
	/**
	 * Updates a push log.
	 * 
	 * @param user
	 * @param repository
	 * @param commands
	 * @return true, if the update was successful
	 */
	public static boolean updatePushLog(UserModel user, Repository repository,
			Collection<ReceiveCommand> commands) {
		RefModel pushlogBranch = getPushLogBranch(repository);
		if (pushlogBranch == null) {
			JGitUtils.createOrphanBranch(repository, GB_PUSHES, null);
		}
		
		boolean success = false;
		String message = "push";
		
		try {
			ObjectId headId = repository.resolve(GB_PUSHES + "^{commit}");
			ObjectInserter odi = repository.newObjectInserter();
			try {
				// Create the in-memory index of the push log entry
				DirCache index = createIndex(repository, headId, commands);
				ObjectId indexTreeId = index.writeTree(odi);

				PersonIdent ident = new PersonIdent(user.getDisplayName(), 
						user.emailAddress == null ? user.username:user.emailAddress);

				// Create a commit object
				CommitBuilder commit = new CommitBuilder();
				commit.setAuthor(ident);
				commit.setCommitter(ident);
				commit.setEncoding(Constants.CHARACTER_ENCODING);
				commit.setMessage(message);
				commit.setParentId(headId);
				commit.setTreeId(indexTreeId);

				// Insert the commit into the repository
				ObjectId commitId = odi.insert(commit);
				odi.flush();

				RevWalk revWalk = new RevWalk(repository);
				try {
					RevCommit revCommit = revWalk.parseCommit(commitId);
					RefUpdate ru = repository.updateRef(GB_PUSHES);
					ru.setNewObjectId(commitId);
					ru.setExpectedOldObjectId(headId);
					ru.setRefLogMessage("commit: " + revCommit.getShortMessage(), false);
					Result rc = ru.forceUpdate();
					switch (rc) {
					case NEW:
					case FORCED:
					case FAST_FORWARD:
						success = true;
						break;
					case REJECTED:
					case LOCK_FAILURE:
						throw new ConcurrentRefUpdateException(JGitText.get().couldNotLockHEAD,
								ru.getRef(), rc);
					default:
						throw new JGitInternalException(MessageFormat.format(
								JGitText.get().updatingRefFailed, GB_PUSHES, commitId.toString(),
								rc));
					}
				} finally {
					revWalk.release();
				}
			} finally {
				odi.release();
			}
		} catch (Throwable t) {
			error(t, repository, "Failed to commit pushlog entry to {0}");
		}
		return success;
	}
	
	/**
	 * Creates an in-memory index of the push log entry.
	 * 
	 * @param repo
	 * @param headId
	 * @param commands
	 * @return an in-memory index
	 * @throws IOException
	 */
	private static DirCache createIndex(Repository repo, ObjectId headId, 
			Collection<ReceiveCommand> commands) throws IOException {

		DirCache inCoreIndex = DirCache.newInCore();
		DirCacheBuilder dcBuilder = inCoreIndex.builder();
		ObjectInserter inserter = repo.newObjectInserter();

		long now = System.currentTimeMillis();
		Set<String> ignorePaths = new TreeSet<String>();
		try {
			// add receive commands to the temporary index
			for (ReceiveCommand command : commands) {
				// use the ref names as the path names
				String path = command.getRefName();
				ignorePaths.add(path);

				StringBuilder change = new StringBuilder();
				change.append(command.getType().name()).append(' ');
				switch (command.getType()) {
				case CREATE:
					change.append(ObjectId.zeroId().getName());
					change.append(' ');
					change.append(command.getNewId().getName());
					break;
				case UPDATE:
				case UPDATE_NONFASTFORWARD:
					change.append(command.getOldId().getName());
					change.append(' ');
					change.append(command.getNewId().getName());
					break;
				case DELETE:
					change = null;
					break;
				}
				if (change == null) {
					// ref deleted
					continue;
				}
				String content = change.toString();
				
				// create an index entry for this attachment
				final DirCacheEntry dcEntry = new DirCacheEntry(path);
				dcEntry.setLength(content.length());
				dcEntry.setLastModified(now);
				dcEntry.setFileMode(FileMode.REGULAR_FILE);

				// insert object
				dcEntry.setObjectId(inserter.insert(Constants.OBJ_BLOB, content.getBytes("UTF-8")));

				// add to temporary in-core index
				dcBuilder.add(dcEntry);
			}

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
						// create a new DirCacheEntry with data retrieved from
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

			// finish temporary in-core index used for this commit
			dcBuilder.finish();
		} finally {
			inserter.release();
		}
		return inCoreIndex;
	}

	public static List<PushLogEntry> getPushLog(String repositoryName, Repository repository) {
		return getPushLog(repositoryName, repository, null, -1);
	}

	public static List<PushLogEntry> getPushLog(String repositoryName, Repository repository, int maxCount) {
		return getPushLog(repositoryName, repository, null, maxCount);
	}

	public static List<PushLogEntry> getPushLog(String repositoryName, Repository repository, Date minimumDate) {
		return getPushLog(repositoryName, repository, minimumDate, -1);
	}
	
	public static List<PushLogEntry> getPushLog(String repositoryName, Repository repository, Date minimumDate, int maxCount) {
		List<PushLogEntry> list = new ArrayList<PushLogEntry>();
		RefModel ref = getPushLogBranch(repository);
		if (ref == null) {
			return list;
		}
		List<RevCommit> pushes;
		if (minimumDate == null) {
			pushes = JGitUtils.getRevLog(repository, GB_PUSHES, 0, maxCount);
		} else {
			pushes = JGitUtils.getRevLog(repository, GB_PUSHES, minimumDate);
		}
		for (RevCommit push : pushes) {
			if (push.getAuthorIdent().getName().equalsIgnoreCase("gitblit")) {
				// skip gitblit/internal commits
				continue;
			}
			Date date = push.getAuthorIdent().getWhen();
			UserModel user = new UserModel(push.getAuthorIdent().getEmailAddress());
			user.displayName = push.getAuthorIdent().getName();
			PushLogEntry log = new PushLogEntry(repositoryName, date, user);
			list.add(log);
			List<PathChangeModel> changedRefs = JGitUtils.getFilesInCommit(repository, push);
			for (PathChangeModel change : changedRefs) {
				switch (change.changeType) {
				case DELETE:
					break;
				case ADD:
				case MODIFY:
					String content = JGitUtils.getStringContent(repository, push.getTree(), change.path);
					String [] fields = content.split(" ");
					String oldId = fields[1];
					String newId = fields[2];
					List<RevCommit> pushedCommits = JGitUtils.getRevLog(repository, oldId, newId);
					for (RevCommit pushedCommit : pushedCommits) {
						log.addCommit(change.path, pushedCommit);
					}
					break;
				default:
					break;
				}
			}
		}
		Collections.sort(list);
		return list;
	}
}
