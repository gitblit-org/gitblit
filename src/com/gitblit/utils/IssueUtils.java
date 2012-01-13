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
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
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

import com.gitblit.models.IssueModel;
import com.gitblit.models.IssueModel.Attachment;
import com.gitblit.models.IssueModel.Change;
import com.gitblit.models.IssueModel.Field;
import com.gitblit.models.PathModel;
import com.gitblit.models.RefModel;
import com.gitblit.utils.JsonUtils.ExcludeField;
import com.google.gson.Gson;

/**
 * Utility class for reading Gitblit issues.
 * 
 * @author James Moger
 * 
 */
public class IssueUtils {

	public static final String GB_ISSUES = "refs/heads/gb-issues";

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
	 * Returns all the issues in the repository.
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
		List<PathModel> paths = JGitUtils
				.getDocuments(repository, Arrays.asList("json"), GB_ISSUES);
		RevTree tree = JGitUtils.getCommit(repository, GB_ISSUES).getTree();
		for (PathModel path : paths) {
			String json = JGitUtils.getStringContent(repository, tree, path.path);
			IssueModel issue = JsonUtils.fromJsonString(json, IssueModel.class);
			if (filter == null) {
				list.add(issue);
			} else {
				if (filter.accept(issue)) {
					list.add(issue);
				}
			}
		}
		Collections.sort(list);
		return list;
	}

	/**
	 * Retrieves the specified issue from the repository with complete changes
	 * history.
	 * 
	 * @param repository
	 * @param issueId
	 * @return an issue, if it exists, otherwise null
	 */
	public static IssueModel getIssue(Repository repository, String issueId) {
		RefModel issuesBranch = getIssuesBranch(repository);
		if (issuesBranch == null) {
			return null;
		}

		if (StringUtils.isEmpty(issueId)) {
			return null;
		}

		// deserialize the issue model object
		IssueModel issue = null;
		String issuePath = getIssuePath(issueId);
		RevTree tree = JGitUtils.getCommit(repository, GB_ISSUES).getTree();
		String json = JGitUtils.getStringContent(repository, tree, issuePath + "/issue.json");
		issue = JsonUtils.fromJsonString(json, IssueModel.class);
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
		String issuePath = getIssuePath(issueId);
		RevTree tree = JGitUtils.getCommit(repository, GB_ISSUES).getTree();
		String json = JGitUtils.getStringContent(repository, tree, issuePath + "/issue.json");
		IssueModel issue = JsonUtils.fromJsonString(json, IssueModel.class);
		Attachment attachment = issue.getAttachment(filename);

		// attachment not found
		if (attachment == null) {
			return null;
		}

		// retrieve the attachment content
		byte[] content = JGitUtils.getByteContent(repository, tree, issuePath + "/" + filename);
		attachment.content = content;
		attachment.size = content.length;
		return attachment;
	}

	/**
	 * Stores an issue in the gb-issues branch of the repository. The branch is
	 * automatically created if it does not already exist.
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
		change.created = new Date();

		IssueModel issue = new IssueModel();
		issue.created = change.created;
		issue.summary = change.getString(Field.Summary);
		issue.description = change.getString(Field.Description);
		issue.reporter = change.getString(Field.Reporter);

		if (StringUtils.isEmpty(issue.summary)) {
			throw new RuntimeException("Must specify an issue summary!");
		}
		if (StringUtils.isEmpty(change.getString(Field.Description))) {
			throw new RuntimeException("Must specify an issue description!");
		}
		if (StringUtils.isEmpty(change.getString(Field.Reporter))) {
			throw new RuntimeException("Must specify an issue reporter!");
		}

		issue.id = StringUtils.getSHA1(issue.created.toString() + issue.reporter + issue.summary
				+ issue.description);

		String message = createChangelog('+', issue.id, change);
		boolean success = commit(repository, issue, change, message);
		if (success) {
			return issue;
		}
		return null;
	}

	/**
	 * Updates an issue in the gb-issues branch of the repository.
	 * 
	 * @param repository
	 * @param issue
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
			throw new RuntimeException("must specify change.author!");
		}

		IssueModel issue = getIssue(repository, issueId);
		change.created = new Date();

		String message = createChangelog('=', issueId, change);
		success = commit(repository, issue, change, message);
		return success;
	}

	private static String createChangelog(char type, String issueId, Change change) {
		return type + " " + issueId + "\n\n" + toJson(change);
	}

	/**
	 * 
	 * @param repository
	 * @param issue
	 * @param change
	 * @param changelog
	 * @return
	 */
	private static boolean commit(Repository repository, IssueModel issue, Change change,
			String changelog) {
		boolean success = false;
		String issuePath = getIssuePath(issue.id);
		try {
			issue.addChange(change);

			// serialize the issue as json
			String json = toJson(issue);

			// cache the issue "files" in a map
			Map<String, CommitFile> files = new HashMap<String, CommitFile>();
			CommitFile issueFile = new CommitFile(issuePath + "/issue.json", change.created);
			issueFile.content = json.getBytes(Constants.CHARACTER_ENCODING);
			files.put(issueFile.path, issueFile);

			if (change.hasAttachments()) {
				for (Attachment attachment : change.attachments) {
					if (!ArrayUtils.isEmpty(attachment.content)) {
						CommitFile file = new CommitFile(issuePath + "/" + attachment.name,
								change.created);
						file.content = attachment.content;
						files.put(file.path, file);
					}
				}
			}

			ObjectId headId = repository.resolve(GB_ISSUES + "^{commit}");

			ObjectInserter odi = repository.newObjectInserter();
			try {
				// Create the in-memory index of the new/updated issue.
				DirCache index = createIndex(repository, headId, files);
				ObjectId indexTreeId = index.writeTree(odi);

				// Create a commit object
				PersonIdent author = new PersonIdent(issue.reporter, issue.reporter + "@gitblit");
				CommitBuilder commit = new CommitBuilder();
				commit.setAuthor(author);
				commit.setCommitter(author);
				commit.setEncoding(Constants.CHARACTER_ENCODING);
				commit.setMessage(changelog);
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
			t.printStackTrace();
		}
		return success;
	}

	private static String toJson(Object o) {
		try {
			// exclude the attachment content field from json serialization
			Gson gson = JsonUtils.gson(new ExcludeField(
					"com.gitblit.models.IssueModel$Attachment.content"));
			String json = gson.toJson(o);
			return json;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	/**
	 * Returns the issue path. This follows the same scheme as Git's object
	 * store path where the first two characters of the hash id are the root
	 * folder with the remaining characters as a subfolder within that folder.
	 * 
	 * @param issueId
	 * @return the root path of the issue content on the gb-issues branch
	 */
	private static String getIssuePath(String issueId) {
		return issueId.substring(0, 2) + "/" + issueId.substring(2);
	}

	/**
	 * Creates an in-memory index of the issue change.
	 * 
	 * @param repo
	 * @param headId
	 * @param files
	 * @param time
	 * @return an in-memory index
	 * @throws IOException
	 */
	private static DirCache createIndex(Repository repo, ObjectId headId,
			Map<String, CommitFile> files) throws IOException {

		DirCache inCoreIndex = DirCache.newInCore();
		DirCacheBuilder dcBuilder = inCoreIndex.builder();
		ObjectInserter inserter = repo.newObjectInserter();

		try {
			// Add the issue files to the temporary index
			for (CommitFile file : files.values()) {
				// create an index entry for the file
				final DirCacheEntry dcEntry = new DirCacheEntry(file.path);
				dcEntry.setLength(file.content.length);
				dcEntry.setLastModified(file.time);
				dcEntry.setFileMode(FileMode.REGULAR_FILE);

				// insert object
				dcEntry.setObjectId(inserter.insert(Constants.OBJ_BLOB, file.content));

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
				if (!files.containsKey(path)) {
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

	private static class CommitFile {
		String path;
		long time;
		byte[] content;

		CommitFile(String path, Date date) {
			this.path = path;
			this.time = date.getTime();
		}
	}

	public static interface IssueFilter {
		public abstract boolean accept(IssueModel issue);
	}
}
