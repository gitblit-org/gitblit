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

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * AnnotatedLine is a serializable model class that represents a the most recent
 * author, date, and commit id of a line in a source file.
 *
 * @author James Moger
 *
 */
public class AnnotatedLine implements Serializable {

	private static final long serialVersionUID = 1L;

	public final String commitId;
	public final String author;
	public final Date when;
	public final int lineNumber;
	public final String data;

	public AnnotatedLine(RevCommit commit, int lineNumber, String data) {
		if (commit == null) {
			this.commitId = ObjectId.zeroId().getName();
			this.author = "?";
			this.when = new Date(0);
		} else {
			this.commitId = commit.getName();
			if(StringUtils.isAnyBlank(commit.getAuthorIdent().getName())) {
			    this.author = commit.getAuthorIdent().getEmailAddress();
			}else {
			    this.author = commit.getAuthorIdent().getName();
			}
			this.when = commit.getAuthorIdent().getWhen();
		}
		this.lineNumber = lineNumber;
		this.data = data;
	}
}