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
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefRename;
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

import com.gitblit.Constants;
import com.gitblit.models.DailyLogEntry;
import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.models.RefLogEntry;
import com.gitblit.models.RefModel;
import com.gitblit.models.RepositoryCommit;
import com.gitblit.models.UserModel;

/**
 * Utility class for maintaining a reflog within a git repository on an
 * orphan branch.
 *
 * @author James Moger
 *
 */
public class RefLogUtils {

	private static final String GB_REFLOG = "refs/meta/gitblit/reflog";

	private static final Logger LOGGER = LoggerFactory.getLogger(RefLogUtils.class);

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
	 * Returns true if the repository has a reflog branch.
	 *
	 * @param repository
	 * @return true if the repository has a reflog branch
	 */
	public static boolean hasRefLogBranch(Repository repository) {
		try {
			return repository.getRef(GB_REFLOG) != null;
		} catch(Exception e) {
			LOGGER.error("failed to determine hasRefLogBranch", e);
		}
		return false;
	}

	/**
	 * Returns a RefModel for the reflog branch in the repository. If the
	 * branch can not be found, null is returned.
	 *
	 * @param repository
	 * @return a refmodel for the reflog branch or null
	 */
	public static RefModel getRefLogBranch(Repository repository) {
		List<RefModel> refs = JGitUtils.getRefs(repository, "refs/");
		Ref oldRef = null;
		for (RefModel ref : refs) {
			if (ref.reference.getName().equals(GB_REFLOG)) {
				return ref;
			} else if (ref.reference.getName().equals("refs/gitblit/reflog")) {
				oldRef = ref.reference;
			} else if (ref.reference.getName().equals("refs/gitblit/pushes")) {
				oldRef = ref.reference;
			}
		}
		if (oldRef != null) {
			// rename old ref to refs/meta/gitblit/reflog
			RefRename cmd;
			try {
				cmd = repository.renameRef(oldRef.getName(), GB_REFLOG);
				cmd.setRefLogIdent(new PersonIdent("Gitblit", "gitblit@localhost"));
				cmd.setRefLogMessage("renamed " + oldRef.getName() + " => " + GB_REFLOG);
				Result res = cmd.rename();
				switch (res) {
				case RENAMED:
					LOGGER.info(repository.getDirectory() + " " + cmd.getRefLogMessage());
					return getRefLogBranch(repository);
				default:
					LOGGER.error("failed to rename " + oldRef.getName() + " => " + GB_REFLOG + " (" + res.name() + ")");
				}
			} catch (IOException e) {
				LOGGER.error("failed to rename reflog", e);
			}
		}
		return null;
	}

	private static UserModel newUserModelFrom(PersonIdent ident) {
		String name = ident.getName();
		String username;
		String displayname;
		if (name.indexOf('/') > -1) {
			int slash = name.indexOf('/');
			displayname = name.substring(0, slash);
			username = name.substring(slash + 1);
		} else {
			displayname = name;
			username = ident.getEmailAddress();
		}

		UserModel user = new UserModel(username);
		user.displayName = displayname;
		user.emailAddress = ident.getEmailAddress();
		return user;
	}

	/**
	 * Logs a ref deletion.
	 *
	 * @param user
	 * @param repository
	 * @param ref
	 * @return true, if the update was successful
	 */
	public static boolean deleteRef(UserModel user, Repository repository, Ref ref) {
		try {
			if (ref == null) {
				return false;
			}
			RefModel reflogBranch = getRefLogBranch(repository);
			if (reflogBranch == null) {
				return false;
			}

			List<RevCommit> log = JGitUtils.getRevLog(repository, reflogBranch.getName(), ref.getName(), 0, 1);
			if (log.isEmpty()) {
				// this ref is not in the reflog branch
				return false;
			}
			ReceiveCommand cmd = new ReceiveCommand(ref.getObjectId(), ObjectId.zeroId(), ref.getName());
			return updateRefLog(user, repository, Arrays.asList(cmd));
		} catch (Throwable t) {
			error(t, repository, "Failed to commit reflog entry to {0}");
		}
		return false;
	}

	/**
	 * Updates the reflog with the received commands.
	 *
	 * @param user
	 * @param repository
	 * @param commands
	 * @return true, if the update was successful
	 */
	public static boolean updateRefLog(UserModel user, Repository repository,
			Collection<ReceiveCommand> commands) {

		// only track branches and tags
		List<ReceiveCommand> filteredCommands = new ArrayList<ReceiveCommand>();
		for (ReceiveCommand cmd : commands) {
			if (!cmd.getRefName().startsWith(Constants.R_HEADS)
					&& !cmd.getRefName().startsWith(Constants.R_TAGS)) {
				continue;
			}
			filteredCommands.add(cmd);
		}

		if (filteredCommands.isEmpty()) {
			// nothing to log
			return true;
		}

		RefModel reflogBranch = getRefLogBranch(repository);
		if (reflogBranch == null) {
			JGitUtils.createOrphanBranch(repository, GB_REFLOG, null);
		}

		boolean success = false;
		String message = "push";

		try {
			ObjectId headId = repository.resolve(GB_REFLOG + "^{commit}");
			ObjectInserter odi = repository.newObjectInserter();
			try {
				// Create the in-memory index of the reflog log entry
				DirCache index = createIndex(repository, headId, commands);
				ObjectId indexTreeId = index.writeTree(odi);

				PersonIdent ident;
				if (UserModel.ANONYMOUS.equals(user)) {
					// anonymous push
					ident = new PersonIdent(user.username + "/" + user.username, user.username);
				} else {
					// construct real pushing account
					ident =	new PersonIdent(MessageFormat.format("{0}/{1}", user.getDisplayName(), user.username),
						user.emailAddress == null ? user.username : user.emailAddress);
				}

				// Create a commit object
				CommitBuilder commit = new CommitBuilder();
				commit.setAuthor(ident);
				commit.setCommitter(ident);
				commit.setEncoding(Constants.ENCODING);
				commit.setMessage(message);
				commit.setParentId(headId);
				commit.setTreeId(indexTreeId);

				// Insert the commit into the repository
				ObjectId commitId = odi.insert(commit);
				odi.flush();

				RevWalk revWalk = new RevWalk(repository);
				try {
					RevCommit revCommit = revWalk.parseCommit(commitId);
					RefUpdate ru = repository.updateRef(GB_REFLOG);
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
								JGitText.get().updatingRefFailed, GB_REFLOG, commitId.toString(),
								rc));
					}
				} finally {
					revWalk.close();
				}
			} finally {
				odi.close();
			}
		} catch (Throwable t) {
			error(t, repository, "Failed to commit reflog entry to {0}");
		}
		return success;
	}

	/**
	 * Creates an in-memory index of the reflog entry.
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
				dcEntry.setObjectId(inserter.insert(org.eclipse.jgit.lib.Constants.OBJ_BLOB, content.getBytes("UTF-8")));

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
			treeWalk.close();

			// finish temporary in-core index used for this commit
			dcBuilder.finish();
		} finally {
			inserter.close();
		}
		return inCoreIndex;
	}

	public static List<RefLogEntry> getRefLog(String repositoryName, Repository repository) {
		return getRefLog(repositoryName, repository, null, 0, -1);
	}

	public static List<RefLogEntry> getRefLog(String repositoryName, Repository repository, int maxCount) {
		return getRefLog(repositoryName, repository, null, 0, maxCount);
	}

	public static List<RefLogEntry> getRefLog(String repositoryName, Repository repository, int offset, int maxCount) {
		return getRefLog(repositoryName, repository, null, offset, maxCount);
	}

	public static List<RefLogEntry> getRefLog(String repositoryName, Repository repository, Date minimumDate) {
		return getRefLog(repositoryName, repository, minimumDate, 0, -1);
	}

	/**
	 * Returns the list of reflog entries as they were recorded by Gitblit.
	 * Each RefLogEntry may represent multiple ref updates.
	 *
	 * @param repositoryName
	 * @param repository
	 * @param minimumDate
	 * @param offset
	 * @param maxCount
	 * 			if < 0, all entries are returned.
	 * @return a list of reflog entries
	 */
	public static List<RefLogEntry> getRefLog(String repositoryName, Repository repository,
			Date minimumDate, int offset, int maxCount) {
		List<RefLogEntry> list = new ArrayList<RefLogEntry>();
		RefModel ref = getRefLogBranch(repository);
		if (ref == null) {
			return list;
		}
		if (maxCount == 0) {
			return list;
		}

		Map<ObjectId, List<RefModel>> allRefs = JGitUtils.getAllRefs(repository);
		List<RevCommit> pushes;
		if (minimumDate == null) {
			pushes = JGitUtils.getRevLog(repository, GB_REFLOG, offset, maxCount);
		} else {
			pushes = JGitUtils.getRevLog(repository, GB_REFLOG, minimumDate);
		}
		for (RevCommit push : pushes) {
			if (push.getAuthorIdent().getName().equalsIgnoreCase("gitblit")) {
				// skip gitblit/internal commits
				continue;
			}

			UserModel user = newUserModelFrom(push.getAuthorIdent());
			Date date = push.getAuthorIdent().getWhen();

			RefLogEntry log = new RefLogEntry(repositoryName, date, user);

			// only report HEADS and TAGS for now
			List<PathChangeModel> changedRefs = new ArrayList<PathChangeModel>();
			for (PathChangeModel refChange : JGitUtils.getFilesInCommit(repository, push)) {
				if (refChange.path.startsWith(Constants.R_HEADS)
						|| refChange.path.startsWith(Constants.R_TAGS)) {
					changedRefs.add(refChange);
				}
			}
			if (changedRefs.isEmpty()) {
				// skip empty commits
				continue;
			}
			list.add(log);
			for (PathChangeModel change : changedRefs) {
				switch (change.changeType) {
				case DELETE:
					log.updateRef(change.path, ReceiveCommand.Type.DELETE);
					break;
				default:
					String content = JGitUtils.getStringContent(repository, push.getTree(), change.path);
					String [] fields = content.split(" ");
					String oldId = fields[1];
					String newId = fields[2];
					log.updateRef(change.path, ReceiveCommand.Type.valueOf(fields[0]), oldId, newId);
					if (ObjectId.zeroId().getName().equals(newId)) {
						// ref deletion
						continue;
					}
					try {
						List<RevCommit> pushedCommits = JGitUtils.getRevLog(repository, oldId, newId);
						for (RevCommit pushedCommit : pushedCommits) {
							RepositoryCommit repoCommit = log.addCommit(change.path, pushedCommit);
							if (repoCommit != null) {
								repoCommit.setRefs(allRefs.get(pushedCommit.getId()));
							}
						}
					} catch (Exception e) {

					}
				}
			}
		}
		Collections.sort(list);
		return list;
	}

	/**
	 * Returns the list of entries organized by ref (e.g. each ref has it's own
	 * RefLogEntry object).
	 *
	 * @param repositoryName
	 * @param repository
	 * @param maxCount
	 * @return a list of reflog entries separated by ref
	 */
	public static List<RefLogEntry> getLogByRef(String repositoryName, Repository repository, int maxCount) {
		return getLogByRef(repositoryName, repository, 0, maxCount);
	}

	/**
	 * Returns the list of entries organized by ref (e.g. each ref has it's own
	 * RefLogEntry object).
	 *
	 * @param repositoryName
	 * @param repository
	 * @param offset
	 * @param maxCount
	 * @return a list of reflog entries separated by ref
	 */
	public static List<RefLogEntry> getLogByRef(String repositoryName, Repository repository,  int offset,
			int maxCount) {
		// break the reflog into ref entries and then merge them back into a list
		Map<String, List<RefLogEntry>> refMap = new HashMap<String, List<RefLogEntry>>();
        List<RefLogEntry> refLog = getRefLog(repositoryName, repository, offset, maxCount);
		for (RefLogEntry entry : refLog) {
			for (String ref : entry.getChangedRefs()) {
				if (!refMap.containsKey(ref)) {
					refMap.put(ref, new ArrayList<RefLogEntry>());
				}

				// construct new ref-specific ref change entry
				RefLogEntry refChange;
				if (entry instanceof DailyLogEntry) {
					// simulated reflog from commits grouped by date
					refChange = new DailyLogEntry(entry.repository, entry.date);
				} else {
					// real reflog entry
					refChange = new RefLogEntry(entry.repository, entry.date, entry.user);
				}
				refChange.updateRef(ref, entry.getChangeType(ref), entry.getOldId(ref), entry.getNewId(ref));
				refChange.addCommits(entry.getCommits(ref));
				refMap.get(ref).add(refChange);
			}
		}

		// merge individual ref changes into master list
		List<RefLogEntry> mergedRefLog = new ArrayList<RefLogEntry>();
		for (List<RefLogEntry> refPush : refMap.values()) {
			mergedRefLog.addAll(refPush);
		}

		// sort ref log
		Collections.sort(mergedRefLog);

		return mergedRefLog;
	}

	/**
	 * Returns the list of ref changes separated by ref (e.g. each ref has it's own
	 * RefLogEntry object).
	 *
	 * @param repositoryName
	 * @param repository
	 * @param minimumDate
	 * @return a list of ref log entries separated by ref
	 */
	public static List<RefLogEntry> getLogByRef(String repositoryName, Repository repository,  Date minimumDate) {
		// break the reflog into refs and then merge them back into a list
		Map<String, List<RefLogEntry>> refMap = new HashMap<String, List<RefLogEntry>>();
		List<RefLogEntry> entries = getRefLog(repositoryName, repository, minimumDate);
		for (RefLogEntry entry : entries) {
			for (String ref : entry.getChangedRefs()) {
				if (!refMap.containsKey(ref)) {
					refMap.put(ref, new ArrayList<RefLogEntry>());
				}

                // construct new ref-specific log entry
                RefLogEntry refPush = new RefLogEntry(entry.repository, entry.date, entry.user);
                refPush.updateRef(ref, entry.getChangeType(ref), entry.getOldId(ref), entry.getNewId(ref));
				refPush.addCommits(entry.getCommits(ref));
				refMap.get(ref).add(refPush);
			}
		}

		// merge individual ref entries into master list
		List<RefLogEntry> refLog = new ArrayList<RefLogEntry>();
		for (List<RefLogEntry> entry : refMap.values()) {
			refLog.addAll(entry);
		}

		Collections.sort(refLog);

		return refLog;
	}

    /**
     * Returns a commit log grouped by day.
     *
     * @param repositoryName
     * @param repository
     * @param minimumDate
     * @param offset
     * @param maxCount
     * 			if < 0, all entries are returned.
     * @param the timezone to use when aggregating commits by date
     * @return a list of grouped commit log entries
     */
    public static List<DailyLogEntry> getDailyLog(String repositoryName, Repository repository,
                                                 Date minimumDate, int offset, int maxCount,
                                                 TimeZone timezone) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		df.setTimeZone(timezone);

        Map<ObjectId, List<RefModel>> allRefs = JGitUtils.getAllRefs(repository);
        Map<String, DailyLogEntry> tags = new HashMap<String, DailyLogEntry>();
        Map<String, DailyLogEntry> pulls = new HashMap<String, DailyLogEntry>();
        Map<String, DailyLogEntry> dailydigests = new HashMap<String, DailyLogEntry>();
        String linearParent = null;
        for (RefModel local : JGitUtils.getLocalBranches(repository, true, -1)) {
        	if (!local.getDate().after(minimumDate)) {
				// branch not recently updated
        		continue;
        	}
            String branch = local.getName();
            List<RepositoryCommit> commits = CommitCache.instance().getCommits(repositoryName, repository,  branch, minimumDate);
            linearParent = null;
            for (RepositoryCommit commit : commits) {
            	if (linearParent != null) {
            		if (!commit.getName().equals(linearParent)) {
            			// only follow linear branch commits
            			continue;
            		}
            	}
                Date date = commit.getCommitDate();
                String dateStr = df.format(date);
                if (!dailydigests.containsKey(dateStr)) {
                    dailydigests.put(dateStr, new DailyLogEntry(repositoryName, date));
                }
                DailyLogEntry digest = dailydigests.get(dateStr);
                if (commit.getParentCount() == 0) {
                	linearParent = null;
                	digest.updateRef(branch, ReceiveCommand.Type.CREATE);
                } else {
                	linearParent = commit.getParents()[0].getId().getName();
                	digest.updateRef(branch, ReceiveCommand.Type.UPDATE, linearParent, commit.getName());
                }

                RepositoryCommit repoCommit = digest.addCommit(commit);
                if (repoCommit != null) {
                	List<RefModel> matchedRefs = allRefs.get(commit.getId());
                    repoCommit.setRefs(matchedRefs);

                    if (!ArrayUtils.isEmpty(matchedRefs)) {
                        for (RefModel ref : matchedRefs) {
                            if (ref.getName().startsWith(Constants.R_TAGS)) {
                                // treat tags as special events in the log
                                if (!tags.containsKey(dateStr)) {
                        			UserModel tagUser = newUserModelFrom(ref.getAuthorIdent());
                        			Date tagDate = commit.getAuthorIdent().getWhen();
                        			tags.put(dateStr, new DailyLogEntry(repositoryName, tagDate, tagUser));
                                }
                                RefLogEntry tagEntry = tags.get(dateStr);
                                tagEntry.updateRef(ref.getName(), ReceiveCommand.Type.CREATE);
                                RepositoryCommit rc = repoCommit.clone(ref.getName());
                                tagEntry.addCommit(rc);
                            } else if (ref.getName().startsWith(Constants.R_PULL)) {
                                // treat pull requests as special events in the log
                                if (!pulls.containsKey(dateStr)) {
                        			UserModel commitUser = newUserModelFrom(ref.getAuthorIdent());
                        			Date commitDate = commit.getAuthorIdent().getWhen();
                        			pulls.put(dateStr, new DailyLogEntry(repositoryName, commitDate, commitUser));
                                }
                                RefLogEntry pullEntry = pulls.get(dateStr);
                                pullEntry.updateRef(ref.getName(), ReceiveCommand.Type.CREATE);
                                RepositoryCommit rc = repoCommit.clone(ref.getName());
                                pullEntry.addCommit(rc);
                            }
                        }
                    }
                }
            }
        }

        List<DailyLogEntry> list = new ArrayList<DailyLogEntry>(dailydigests.values());
        list.addAll(tags.values());
        //list.addAll(pulls.values());
        Collections.sort(list);
        return list;
    }

    /**
     * Returns the list of commits separated by ref (e.g. each ref has it's own
     * RefLogEntry object for each day).
     *
     * @param repositoryName
     * @param repository
     * @param minimumDate
     * @param the timezone to use when aggregating commits by date
     * @return a list of push log entries separated by ref and date
     */
    public static List<DailyLogEntry> getDailyLogByRef(String repositoryName, Repository repository,
    		Date minimumDate, TimeZone timezone) {
        // break the reflog into ref entries and then merge them back into a list
        Map<String, List<DailyLogEntry>> refMap = new HashMap<String, List<DailyLogEntry>>();
        List<DailyLogEntry> entries = getDailyLog(repositoryName, repository, minimumDate, 0, -1, timezone);
        for (DailyLogEntry entry : entries) {
            for (String ref : entry.getChangedRefs()) {
                if (!refMap.containsKey(ref)) {
                    refMap.put(ref, new ArrayList<DailyLogEntry>());
                }

                // construct new ref-specific log entry
                DailyLogEntry refEntry = new DailyLogEntry(entry.repository, entry.date, entry.user);
                refEntry.updateRef(ref, entry.getChangeType(ref), entry.getOldId(ref), entry.getNewId(ref));
                refEntry.addCommits(entry.getCommits(ref));
                refMap.get(ref).add(refEntry);
            }
        }

        // merge individual ref entries into master list
        List<DailyLogEntry> refLog = new ArrayList<DailyLogEntry>();
        for (List<DailyLogEntry> refEntry : refMap.values()) {
        	for (DailyLogEntry entry : refEntry) {
        		if (entry.getCommitCount() > 0) {
        			refLog.add(entry);
        		}
        	}
        }

        Collections.sort(refLog);

        return refLog;
    }
}
