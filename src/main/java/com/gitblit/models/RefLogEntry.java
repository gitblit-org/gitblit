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
package com.gitblit.models;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;

import com.gitblit.utils.StringUtils;

/**
 * Model class to represent a push into a repository.
 *
 * @author James Moger
 */
public class RefLogEntry implements Serializable, Comparable<RefLogEntry> {

	private static final long serialVersionUID = 1L;

	public final String repository;

	public final Date date;

	public final UserModel user;

	private final Set<RepositoryCommit> commits;

	protected final Map<String, ReceiveCommand.Type> refUpdates;

	protected final Map<String, String> refIdChanges;

	private int authorCount;

	/**
	 * Constructor for specified duration of push from start date.
	 *
	 * @param repository
	 *            the repository that received the push
	 * @param date
	 *            the date of the push
	 * @param user
	 *            the user who pushed
	 */
	public RefLogEntry(String repository, Date date, UserModel user) {
		this.repository = repository;
		this.date = date;
		this.user = user;
		this.commits = new LinkedHashSet<RepositoryCommit>();
		this.refUpdates = new HashMap<String, ReceiveCommand.Type>();
		this.refIdChanges = new HashMap<String, String>();
		this.authorCount = -1;
	}

	/**
	 * Tracks the change type for the specified ref.
	 *
	 * @param ref
	 * @param type
	 */
	public void updateRef(String ref, ReceiveCommand.Type type) {
		if (!refUpdates.containsKey(ref)) {
			refUpdates.put(ref, type);
		}
	}

	/**
	 * Tracks the change type for the specified ref.
	 *
	 * @param ref
	 * @param type
	 * @param oldId
	 * @param newId
	 */
	public void updateRef(String ref, ReceiveCommand.Type type, String oldId, String newId) {
		if (!refUpdates.containsKey(ref)) {
			refUpdates.put(ref, type);
			refIdChanges.put(ref, oldId + "-" + newId);
		}
	}

	/**
	 * Returns the old id of a ref.
	 *
	 * @param ref
	 * @return the old id
	 */
	public String getOldId(String ref) {
		String change = refIdChanges.get(ref);
		if (StringUtils.isEmpty(change)) {
			return null;
		}
		return change.split("-")[0];
	}

	/**
	 * Returns the new id of a ref
	 *
	 * @param ref
	 * @return the new id
	 */
	public String getNewId(String ref) {
		String change = refIdChanges.get(ref);
		if (StringUtils.isEmpty(change)) {
			return null;
		}
		return change.split("-")[1];
	}

	/**
	 * Returns the change type of the ref change.
	 *
	 * @param ref
	 * @return the change type for the ref
	 */
	public ReceiveCommand.Type getChangeType(String ref) {
		ReceiveCommand.Type type = refUpdates.get(ref);
		return type;
	}

	/**
	 * Adds a commit to the push entry object as long as the commit is not a
	 * duplicate.
	 *
	 * @param branch
	 * @param commit
	 * @return a RepositoryCommit, if one was added. Null if this is duplicate
	 *         commit
	 */
	public RepositoryCommit addCommit(String branch, RevCommit commit) {
		RepositoryCommit commitModel = new RepositoryCommit(repository, branch, commit);
		if (commits.add(commitModel)) {
			authorCount = -1;
			return commitModel;
		}
		return null;
	}

	/**
	 * Adds a commit to the push entry object as long as the commit is not a
	 * duplicate.
	 *
	 * @param branch
	 * @param commit
	 * @return a RepositoryCommit, if one was added. Null if this is duplicate
	 *         commit
	 */
	public RepositoryCommit addCommit(RepositoryCommit commit) {
		if (commits.add(commit)) {
			authorCount = -1;
			return commit;
		}
		return null;
	}

	/**
	 * Adds a a list of repository commits.  This is used to construct discrete
	 * ref push log entries
	 *
	 * @param commits
	 */
	public void addCommits(List<RepositoryCommit> list) {
		commits.addAll(list);
		authorCount = -1;
	}

	/**
	 * Returns true if this push contains a non-fastforward ref update.
	 *
	 * @return true if this is a non-fastforward push
	 */
	public boolean isNonFastForward() {
		for (Map.Entry<String, ReceiveCommand.Type> entry : refUpdates.entrySet()) {
			if (ReceiveCommand.Type.UPDATE_NONFASTFORWARD.equals(entry.getValue())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns true if this ref has been rewound.
	 *
	 * @param ref
	 * @return true if this is a non-fastforward ref update
	 */
	public boolean isNonFastForward(String ref) {
		ReceiveCommand.Type type = refUpdates.get(ref);
		if (type == null) {
			return false;
		}
		return ReceiveCommand.Type.UPDATE_NONFASTFORWARD.equals(type);
	}

	/**
	 * Returns true if this ref has been deleted.
	 *
	 * @param ref
	 * @return true if this is a delete ref update
	 */
	public boolean isDelete(String ref) {
		ReceiveCommand.Type type = refUpdates.get(ref);
		if (type == null) {
			return false;
		}
		return ReceiveCommand.Type.DELETE.equals(type);
	}

	/**
	 * Returns the list of refs changed by the push.
	 *
	 * @return a list of refs
	 */
	public List<String> getChangedRefs() {
		return new ArrayList<String>(refUpdates.keySet());
	}

	/**
	 * Returns the list of branches changed by the push.
	 *
	 * @return a list of branches
	 */
	public List<String> getChangedBranches() {
		return getChangedRefs(Constants.R_HEADS);
	}

	/**
	 * Returns the list of tags changed by the push.
	 *
	 * @return a list of tags
	 */
	public List<String> getChangedTags() {
		return getChangedRefs(Constants.R_TAGS);
	}

	/**
	 * Gets the changed refs in the push.
	 *
	 * @param baseRef
	 * @return the changed refs
	 */
	protected List<String> getChangedRefs(String baseRef) {
		Set<String> refs = new HashSet<String>();
		for (String ref : refUpdates.keySet()) {
			if (baseRef == null || ref.startsWith(baseRef)) {
				refs.add(ref);
			}
		}
		List<String> list = new ArrayList<String>(refs);
		Collections.sort(list);
		return list;
	}

	public int getAuthorCount() {
		if (authorCount == -1) {
			Set<String> authors = new HashSet<String>();
			for (RepositoryCommit commit : commits) {
				String name = commit.getAuthorIdent().getName();
				authors.add(name);
			}
			authorCount = authors.size();
		}
		return authorCount;
	}

	/**
	 * The total number of commits in the push.
	 *
	 * @return the number of commits in the push
	 */
	public int getCommitCount() {
		return commits.size();
	}

	/**
	 * Returns all commits in the push.
	 *
	 * @return a list of commits
	 */
	public List<RepositoryCommit> getCommits() {
		List<RepositoryCommit> list = new ArrayList<RepositoryCommit>(commits);
		Collections.sort(list);
		return list;
	}

	/**
	 * Returns all commits that belong to a particular ref
	 *
	 * @param ref
	 * @return a list of commits
	 */
	public List<RepositoryCommit> getCommits(String ref) {
		List<RepositoryCommit> list = new ArrayList<RepositoryCommit>();
		for (RepositoryCommit commit : commits) {
			if (commit.branch.equals(ref)) {
				list.add(commit);
			}
		}
		Collections.sort(list);
		return list;
	}

	public PersonIdent getCommitterIdent() {
		return new PersonIdent(user.getDisplayName(), user.emailAddress == null ? user.username : user.emailAddress);
	}

	public PersonIdent getAuthorIdent() {
		if (getAuthorCount() == 1) {
			return getCommits().get(0).getAuthorIdent();
		}
		return getCommitterIdent();
	}

	@Override
	public int compareTo(RefLogEntry o) {
		// reverse chronological order
		return o.date.compareTo(date);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(MessageFormat.format("{0,date,yyyy-MM-dd HH:mm}: {1} pushed {2,number,0} commit{3} to {4} ",
				date, user.getDisplayName(), commits.size(), commits.size() == 1 ? "":"s", repository));
		for (Map.Entry<String, ReceiveCommand.Type> entry : refUpdates.entrySet()) {
			String ref = entry.getKey();
			ReceiveCommand.Type type = entry.getValue();
			sb.append("\n  ").append(ref).append(' ').append(type.name()).append('\n');
			for (RepositoryCommit commit : getCommits(ref)) {
				sb.append("    ").append(commit.toString()).append('\n');
			}
		}
		return sb.toString();
	}
}
