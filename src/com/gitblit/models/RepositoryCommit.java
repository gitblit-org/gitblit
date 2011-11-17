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
import java.util.List;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Model class to represent a RevCommit, it's source repository, and the branch.
 * This class is used by the activity page.
 * 
 * @author James Moger
 */
public class RepositoryCommit implements Serializable, Comparable<RepositoryCommit> {

	private static final long serialVersionUID = 1L;

	public final String repository;

	public final String branch;

	private final RevCommit commit;

	private List<RefModel> refs;

	public RepositoryCommit(String repository, String branch, RevCommit commit) {
		this.repository = repository;
		this.branch = branch;
		this.commit = commit;
	}

	public void setRefs(List<RefModel> refs) {
		this.refs = refs;
	}

	public List<RefModel> getRefs() {
		return refs;
	}

	public String getName() {
		return commit.getName();
	}

	public String getShortName() {
		return commit.getName().substring(0, 8);
	}

	public String getShortMessage() {
		return commit.getShortMessage();
	}

	public int getParentCount() {
		return commit.getParentCount();
	}

	public PersonIdent getAuthorIdent() {
		return commit.getAuthorIdent();
	}

	@Override
	public int compareTo(RepositoryCommit o) {
		// reverse-chronological order
		if (commit.getCommitTime() > o.commit.getCommitTime()) {
			return -1;
		} else if (commit.getCommitTime() < o.commit.getCommitTime()) {
			return 1;
		}
		return 0;
	}
}