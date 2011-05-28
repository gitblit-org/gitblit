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
package com.gitblit.models;

import java.io.Serializable;
import java.util.Date;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;

public class RefModel implements Serializable, Comparable<RefModel> {

	private static final long serialVersionUID = 1L;
	public final String displayName;
	public final RevCommit commit;
	public transient Ref ref;

	public RefModel(String displayName, Ref ref, RevCommit commit) {
		this.displayName = displayName;
		this.ref = ref;
		this.commit = commit;
	}

	public Date getDate() {
		return new Date(commit.getCommitTime() * 1000L);
	}

	public String getName() {
		return ref.getName();
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
	public int hashCode() {
		return getCommitId().hashCode() + getName().hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof RefModel) {
			RefModel other = (RefModel) o;
			return getName().equals(other.getName());
		}
		return super.equals(o);
	}

	@Override
	public int compareTo(RefModel o) {
		return getDate().compareTo(o.getDate());
	}
}