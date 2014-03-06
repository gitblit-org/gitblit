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
import java.util.Date;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * Model class to simulate a push for presentation in the push log news feed
 * for a repository that does not have a Gitblit push log.  Commits are grouped
 * by date and may be additionally split by ref.
 *
 * @author James Moger
 */
public class DailyLogEntry extends RefLogEntry implements Serializable {

	private static final long serialVersionUID = 1L;

	public DailyLogEntry(String repository, Date date) {
		super(repository, date, new UserModel("digest"));
	}

	public DailyLogEntry(String repository, Date date, UserModel user) {
		super(repository, date, user);
	}

	@Override
	public PersonIdent getCommitterIdent() {
		if (getAuthorCount() == 1) {
			return getCommits().get(0).getCommitterIdent();
		}

		return super.getCommitterIdent();
	}

	@Override
	public PersonIdent getAuthorIdent() {
		if (getAuthorCount() == 1) {
			return getCommits().get(0).getAuthorIdent();
		}

		return super.getAuthorIdent();
	}

	/**
	 * Tracks the change type for the specified ref.
	 *
	 * @param ref
	 * @param type
	 * @param oldId
	 * @param newId
	 */
	@Override
	public void updateRef(String ref, ReceiveCommand.Type type, String oldId, String newId) {
		// daily digests are filled from most recent to oldest
		String preservedNewId = getNewId(ref);
		if (preservedNewId == null) {
			// no preserved new id, this is newest commit
			// for this ref
			preservedNewId = newId;
		}
		refUpdates.put(ref, type);
		refIdChanges.put(ref, oldId + "-" + preservedNewId);
	}

}
