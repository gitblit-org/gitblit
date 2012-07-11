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
package com.gitblit.utils;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.IssueModel;
import com.gitblit.models.IssueModel.Attachment;
import com.gitblit.models.IssueModel.Change;
import com.gitblit.models.IssueModel.Field;
import com.gitblit.models.IssueModel.Status;
import com.gitblit.models.RefModel;
import com.gitblit.utils.JsonUtils.ExcludeField;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Utility class for reading Gitblit issues.
 * 
 * @author James Moger
 * 
 */
public class IssueUtils {

	public static interface IssueFilter {
		public abstract boolean accept(IssueModel issue);
	}

	public static final String GB_ISSUES = "refs/heads/gb-issues";

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
	 * Returns a RefModel for the gb-issues branch in the repository. If the
	 * branch can not be found, null is returned.
	 * 
	 * @param repository
	 * @return a refmodel for the gb-issues branch or null
	 */
	public static RefModel getIssuesBranch(Repository repository) {
		return JGitUtils.getBranch(repository, "gb-issues");
	}

	/**
	 * Returns all the issues in the repository. Querying issues from the
	 * repository requires deserializing all changes for all issues. This is an
	 * expensive process and not recommended. Issues should be indexed by Lucene
	 * and queries should be executed against that index.
	 * 
	 * @param repository
	 * @param filter
	 *            optional issue filter to only return matching results
	 * @return a list of issues
	 */
	public static List<IssueModel> getIssues(Repository repository, IssueFilter filter) {
		List<IssueModel> list = new ArrayList<IssueModel>();
		RefModel issuesBranch = getIssuesBranch(repository);
		if (issuesBranch == null) {
			return list;
		}

		// Collect the set of all issue paths
		Set<String> issuePaths = new HashSet<String>();
		final TreeWalk tw = new TreeWalk(repository);
		try {
			RevCommit head = JGitUtils.getCommit(repository, GB_ISSUES);
			tw.addTree(head.getTree());
			tw.setRecursive(false);
			while (tw.next()) {
				if (tw.getDepth() < 2 && tw.isSubtree()) {
					tw.enterSubtree();
					if (tw.getDepth() == 2) {
						issuePaths.add(tw.getPathString());
					}
				}
			}
		} catch (IOException e) {
			error(e, repository, "{0} failed to query issues");
		} finally {
			tw.release();
		}

		// Build each issue and optionally filter out unwanted issues

		for (String issuePath : issuePaths) {
			RevWalk rw = new RevWalk(repository);
			try {
				RevCommit start = rw.parseCommit(repository.resolve(GB_ISSUES));
				rw.markStart(start);
			} catch (Exception e) {
				error(e, repository, "Failed to find {1} in {0}", GB_ISSUES);
			}
			TreeFilter treeFilter = AndTreeFilter.create(
					PathFilterGroup.createFromStrings(issuePath), TreeFilter.ANY_DIFF);
			rw.setTreeFilter(treeFilter);
			Iterator<RevCommit> revlog = rw.iterator();

			List<RevCommit> commits = new ArrayList<RevCommit>();
			while (revlog.hasNext()) {
				commits.add(revlog.next());
			}

			// release the revwalk
			rw.release();

			if (commits.size() == 0) {
				LOGGER.warn("Failed to find changes for issue " + issuePath);
				continue;
			}

			// sort by commit order, first commit first
			Collections.reverse(commits);

			StringBuilder sb = new StringBuilder("[");
			boolean first = true;
			for (RevCommit commit : commits) {
				if (!first) {
					sb.append(',');
				}
				String message = commit.getFullMessage();
				// commit message is formatted: C ISSUEID\n\nJSON
				// C is an single char commit code
				// ISSUEID is an SHA-1 hash
				String json = message.substring(43);
				sb.append(json);
				first = false;
			}
			sb.append(']');

			// Deserialize the JSON array as a Collection<Change>, this seems
			// slightly faster than deserializing each change by itself.
			Collection<Change> changes = JsonUtils.fromJsonString(sb.toString(),
					new TypeToken<Collection<Change>>() {
					}.getType());

			// create an issue object form the changes
			IssueModel issue = buildIssue(changes, true);

			// add the issue, conditionally, to the list
			if (filter == null) {
				list.add(issue);
			} else {
				if (filter.accept(issue)) {
					list.add(issue);
				}
			}
		}

		// sort the issues by creation
		Collections.sort(list);
		return list;
	}

	/**
	 * Retrieves the specified issue from the repository with all changes
	 * applied to build the effective issue.
	 * 
	 * @param repository
	 * @param issueId
	 * @return an issue, if it exists, otherwise null
	 */
	public static IssueModel getIssue(Repository repository, String issueId) {
		return getIssue(repository, issueId, true);
	}

	/**
	 * Retrieves the specified issue from the repository.
	 * 
	 * @param repository
	 * @param issueId
	 * @param effective
	 *            if true, the effective issue is built by processing comment
	 *            changes, deletions, etc. if false, the raw issue is built
	 *            without consideration for comment changes, deletions, etc.
	 * @return an issue, if it exists, otherwise null
	 */
	public static IssueModel getIssue(Repository repository, String issueId, boolean effective) {
		RefModel issuesBranch = getIssuesBranch(repository);
		if (issuesBranch == null) {
			return null;
		}

		if (StringUtils.isEmpty(issueId)) {
			return null;
		}

		String issuePath = getIssuePath(issueId);

		// Collect all changes as JSON array from commit messages
		List<RevCommit> commits = JGitUtils.getRevLog(repository, GB_ISSUES, issuePath, 0, -1);

		// sort by commit order, first commit first
		Collections.reverse(commits);

		StringBuilder sb = new StringBuilder("[");
		boolean first = true;
		for (RevCommit commit : commits) {
			if (!first) {
				sb.append(',');
			}
			String message = commit.getFullMessage();
			// commit message is formatted: C ISSUEID\n\nJSON
			// C is an single char commit code
			// ISSUEID is an SHA-1 hash
			String json = message.substring(43);
			sb.append(json);
			first = false;
		}
		sb.append(']');

		// Deserialize the JSON array as a Collection<Change>, this seems
		// slightly faster than deserializing each change by itself.
		Collection<Change> changes = JsonUtils.fromJsonString(sb.toString(),
				new TypeToken<Collection<Change>>() {
				}.getType());

		// create an issue object and apply the changes to it
		IssueModel issue = buildIssue(changes, effective);
		return issue;
	}

	/**
	 * Builds an issue from a set of changes.
	 * 
	 * @param changes
	 * @param effective
	 *            if true, the effective issue is built which accounts for
	 *            comment changes, comment deletions, etc. if false, the raw
	 *            issue is built.
	 * @return an issue
	 */
	private static IssueModel buildIssue(Collection<Change> changes, boolean effective) {
		IssueModel issue;
		if (effective) {
			List<Change> effectiveChanges = new ArrayList<Change>();
			Map<String, Change> comments = new HashMap<String, Change>();
			for (Change change : changes) {
				if (change.comment != null) {
					if (comments.containsKey(change.comment.id)) {
						Change original = comments.get(change.comment.id);
						Change clone = DeepCopier.copy(original);
						clone.comment.text = change.comment.text;
						clone.comment.deleted = change.comment.deleted;
						int idx = effectiveChanges.indexOf(original);
						effectiveChanges.remove(original);
						effectiveChanges.add(idx, clone);
						comments.put(clone.comment.id, clone);
					} else {
						effectiveChanges.add(change);
						comments.put(change.comment.id, change);
					}
				} else {
					effectiveChanges.add(change);
				}
			}

			// effective issue
			issue = new IssueModel();
			for (Change change : effectiveChanges) {
				issue.applyChange(change);
			}
		} else {
			// raw issue
			issue = new IssueModel();
			for (Change change : changes) {
				issue.applyChange(change);
			}
		}
		return issue;
	}

	/**
	 * Retrieves the specified attachment from an issue.
	 * 
	 * @param repository
	 * @param issueId
	 * @param filename
	 * @return an attachment, if found, null otherwise
	 */
	public static Attachment getIssueAttachment(Repository repository, String issueId,
			String filename) {
		RefModel issuesBranch = getIssuesBranch(repository);
		if (issuesBranch == null) {
			return null;
		}

		if (StringUtils.isEmpty(issueId)) {
			return null;
		}

		// deserialize the issue model so that we have the attachment metadata
		IssueModel issue = getIssue(repository, issueId, true);
		Attachment attachment = issue.getAttachment(filename);

		// attachment not found
		if (attachment == null) {
			return null;
		}

		// retrieve the attachment content
		String issuePath = getIssuePath(issueId);
		RevTree tree = JGitUtils.getCommit(repository, GB_ISSUES).getTree();
		byte[] content = JGitUtils
				.getByteContent(repository, tree, issuePath + "/" + attachment.id);
		attachment.content = content;
		attachment.size = content.length;
		return attachment;
	}

	/**
	 * Creates an issue in the gb-issues branch of the repository. The branch is
	 * automatically created if it does not already exist. Your change must
	 * include an author, summary, and description, at a minimum. If your change
	 * does not have those minimum requirements a RuntimeException will be
	 * thrown.
	 * 
	 * @param repository
	 * @param change
	 * @return true if successful
	 */
	public static IssueModel createIssue(Repository repository, Change change) {
		RefModel issuesBranch = getIssuesBranch(repository);
		if (issuesBranch == null) {
			JGitUtils.createOrphanBranch(repository, "gb-issues", null);
		}

		if (StringUtils.isEmpty(change.author)) {
			throw new RuntimeException("Must specify a change author!");
		}
		if (!change.hasField(Field.Summary)) {
			throw new RuntimeException("Must specify a summary!");
		}
		if (!change.hasField(Field.Description)) {
			throw new RuntimeException("Must specify a description!");
		}

		change.setField(Field.Reporter, change.author);

		String issueId = StringUtils.getSHA1(change.created.toString() + change.author
				+ change.getString(Field.Summary) + change.getField(Field.Description));
		change.setField(Field.Id, issueId);
		change.code = '+';

		boolean success = commit(repository, issueId, change);
		if (success) {
			return getIssue(repository, issueId, false);
		}
		return null;
	}

	/**
	 * Updates an issue in the gb-issues branch of the repository.
	 * 
	 * @param repository
	 * @param issueId
	 * @param change
	 * @return true if successful
	 */
	public static boolean updateIssue(Repository repository, String issueId, Change change) {
		boolean success = false;
		RefModel issuesBranch = getIssuesBranch(repository);

		if (issuesBranch == null) {
			throw new RuntimeException("gb-issues branch does not exist!");
		}

		if (change == null) {
			throw new RuntimeException("change can not be null!");
		}

		if (StringUtils.isEmpty(change.author)) {
			throw new RuntimeException("must specify a change author!");
		}

		// determine update code
		// default update code is '=' for a general change
		change.code = '=';
		if (change.hasField(Field.Status)) {
			Status status = Status.fromObject(change.getField(Field.Status));
			if (status.isClosed()) {
				// someone closed the issue
				change.code = 'x';
			}
		}
		success = commit(repository, issueId, change);
		return success;
	}

	/**
	 * Deletes an issue from the repository.
	 * 
	 * @param repository
	 * @param issueId
	 * @return true if successful
	 */
	public static boolean deleteIssue(Repository repository, String issueId, String author) {
		boolean success = false;
		RefModel issuesBranch = getIssuesBranch(repository);

		if (issuesBranch == null) {
			throw new RuntimeException("gb-issues branch does not exist!");
		}

		if (StringUtils.isEmpty(issueId)) {
			throw new RuntimeException("must specify an issue id!");
		}

		String issuePath = getIssuePath(issueId);

		String message = "- " + issueId;
		try {
			ObjectId headId = repository.resolve(GB_ISSUES + "^{commit}");
			ObjectInserter odi = repository.newObjectInserter();
			try {
				// Create the in-memory index of the new/updated issue
				DirCache index = DirCache.newInCore();
				DirCacheBuilder dcBuilder = index.builder();
				// Traverse HEAD to add all other paths
				TreeWalk treeWalk = new TreeWalk(repository);
				int hIdx = -1;
				if (headId != null)
					hIdx = treeWalk.addTree(new RevWalk(repository).parseTree(headId));
				treeWalk.setRecursive(true);
				while (treeWalk.next()) {
					String path = treeWalk.getPathString();
					CanonicalTreeParser hTree = null;
					if (hIdx != -1)
						hTree = treeWalk.getTree(hIdx, CanonicalTreeParser.class);
					if (!path.startsWith(issuePath)) {
						// add entries from HEAD for all other paths
						if (hTree != null) {
							// create a new DirCacheEntry with data retrieved
							// from HEAD
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

				ObjectId indexTreeId = index.writeTree(odi);

				// Create a commit object
				PersonIdent ident = new PersonIdent(author, "gitblit@localhost");
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
					RefUpdate ru = repository.updateRef(GB_ISSUES);
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
								JGitText.get().updatingRefFailed, GB_ISSUES, commitId.toString(),
								rc));
					}
				} finally {
					revWalk.release();
				}
			} finally {
				odi.release();
			}
		} catch (Throwable t) {
			error(t, repository, "Failed to delete issue {1} to {0}", issueId);
		}
		return success;
	}

	/**
	 * Changes the text of an issue comment.
	 * 
	 * @param repository
	 * @param issue
	 * @param change
	 *            the change with the comment to change
	 * @param author
	 *            the author of the revision
	 * @param comment
	 *            the revised comment
	 * @return true, if the change was successful
	 */
	public static boolean changeComment(Repository repository, IssueModel issue, Change change,
			String author, String comment) {
		Change revision = new Change(author);
		revision.comment(comment);
		revision.comment.id = change.comment.id;
		return updateIssue(repository, issue.id, revision);
	}

	/**
	 * Deletes a comment from an issue.
	 * 
	 * @param repository
	 * @param issue
	 * @param change
	 *            the change with the comment to delete
	 * @param author
	 * @return true, if the deletion was successful
	 */
	public static boolean deleteComment(Repository repository, IssueModel issue, Change change,
			String author) {
		Change deletion = new Change(author);
		deletion.comment(change.comment.text);
		deletion.comment.id = change.comment.id;
		deletion.comment.deleted = true;
		return updateIssue(repository, issue.id, deletion);
	}

	/**
	 * Commit a change to the repository. Each issue is composed on changes.
	 * Issues are built from applying the changes in the order they were
	 * committed to the repository. The changes are actually specified in the
	 * commit messages and not in the RevTrees which allows for clean,
	 * distributed merging.
	 * 
	 * @param repository
	 * @param issueId
	 * @param change
	 * @return true, if the change was committed
	 */
	private static boolean commit(Repository repository, String issueId, Change change) {
		boolean success = false;

		try {
			// assign ids to new attachments
			// attachments are stored by an SHA1 id
			if (change.hasAttachments()) {
				for (Attachment attachment : change.attachments) {
					if (!ArrayUtils.isEmpty(attachment.content)) {
						byte[] prefix = (change.created.toString() + change.author).getBytes();
						byte[] bytes = new byte[prefix.length + attachment.content.length];
						System.arraycopy(prefix, 0, bytes, 0, prefix.length);
						System.arraycopy(attachment.content, 0, bytes, prefix.length,
								attachment.content.length);
						attachment.id = "attachment-" + StringUtils.getSHA1(bytes);
					}
				}
			}

			// serialize the change as json
			// exclude any attachment from json serialization
			Gson gson = JsonUtils.gson(new ExcludeField(
					"com.gitblit.models.IssueModel$Attachment.content"));
			String json = gson.toJson(change);

			// include the json change in the commit message
			String issuePath = getIssuePath(issueId);
			String message = change.code + " " + issueId + "\n\n" + json;

			// Create a commit file. This is required for a proper commit and
			// ensures we can retrieve the commit log of the issue path.
			//
			// This file is NOT serialized as part of the Change object.
			switch (change.code) {
			case '+': {
				// New Issue.
				Attachment placeholder = new Attachment("issue");
				placeholder.id = placeholder.name;
				placeholder.content = "DO NOT REMOVE".getBytes(Constants.CHARACTER_ENCODING);
				change.addAttachment(placeholder);
				break;
			}
			default: {
				// Update Issue.
				String changeId = StringUtils.getSHA1(json);
				Attachment placeholder = new Attachment("change-" + changeId);
				placeholder.id = placeholder.name;
				placeholder.content = "REMOVABLE".getBytes(Constants.CHARACTER_ENCODING);
				change.addAttachment(placeholder);
				break;
			}
			}

			ObjectId headId = repository.resolve(GB_ISSUES + "^{commit}");
			ObjectInserter odi = repository.newObjectInserter();
			try {
				// Create the in-memory index of the new/updated issue
				DirCache index = createIndex(repository, headId, issuePath, change);
				ObjectId indexTreeId = index.writeTree(odi);

				// Create a commit object
				PersonIdent ident = new PersonIdent(change.author, "gitblit@localhost");
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
					RefUpdate ru = repository.updateRef(GB_ISSUES);
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
								JGitText.get().updatingRefFailed, GB_ISSUES, commitId.toString(),
								rc));
					}
				} finally {
					revWalk.release();
				}
			} finally {
				odi.release();
			}
		} catch (Throwable t) {
			error(t, repository, "Failed to commit issue {1} to {0}", issueId);
		}
		return success;
	}

	/**
	 * Returns the issue path. This follows the same scheme as Git's object
	 * store path where the first two characters of the hash id are the root
	 * folder with the remaining characters as a subfolder within that folder.
	 * 
	 * @param issueId
	 * @return the root path of the issue content on the gb-issues branch
	 */
	static String getIssuePath(String issueId) {
		return issueId.substring(0, 2) + "/" + issueId.substring(2);
	}

	/**
	 * Creates an in-memory index of the issue change.
	 * 
	 * @param repo
	 * @param headId
	 * @param change
	 * @return an in-memory index
	 * @throws IOException
	 */
	private static DirCache createIndex(Repository repo, ObjectId headId, String issuePath,
			Change change) throws IOException {

		DirCache inCoreIndex = DirCache.newInCore();
		DirCacheBuilder dcBuilder = inCoreIndex.builder();
		ObjectInserter inserter = repo.newObjectInserter();

		Set<String> ignorePaths = new TreeSet<String>();
		try {
			// Add any attachments to the temporary index
			if (change.hasAttachments()) {
				for (Attachment attachment : change.attachments) {
					// build a path name for the attachment and mark as ignored
					String path = issuePath + "/" + attachment.id;
					ignorePaths.add(path);

					// create an index entry for this attachment
					final DirCacheEntry dcEntry = new DirCacheEntry(path);
					dcEntry.setLength(attachment.content.length);
					dcEntry.setLastModified(change.created.getTime());
					dcEntry.setFileMode(FileMode.REGULAR_FILE);

					// insert object
					dcEntry.setObjectId(inserter.insert(Constants.OBJ_BLOB, attachment.content));

					// add to temporary in-core index
					dcBuilder.add(dcEntry);
				}
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
}