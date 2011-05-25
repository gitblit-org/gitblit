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
package com.gitblit.wicket.models;

import java.io.Serializable;
import java.util.Date;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.utils.JGitUtils;

public class RefModel implements Serializable, Comparable<RefModel> {

	private static final long serialVersionUID = 1L;
	final String displayName;
	transient Ref ref;
	final RevCommit commit;

	public RefModel(String displayName, Ref ref, RevCommit commit) {
		this.displayName = displayName;
		this.ref = ref;
		this.commit = commit;
	}

	public Date getDate() {
		return JGitUtils.getCommitDate(commit);
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getName() {
		return ref.getName();
	}

	public RevCommit getCommit() {
		return commit;
	}

	public ObjectId getCommitId() {
		return commit.getId();
	}

	public String getShortLog() {
		return commit.getShortMessage();
	}

	public ObjectId getObjectId() {
		return ref.getObjectId();
	}

	public boolean isAnnotatedTag() {
		// ref.isPeeled() ??
		return !getCommitId().equals(getObjectId());
	}

	@Override
	public int compareTo(RefModel o) {
		return getDate().compareTo(o.getDate());
	}
}