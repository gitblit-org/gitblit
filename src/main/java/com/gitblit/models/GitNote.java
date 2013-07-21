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

/**
 * GitNote is a serializable model class that represents a git note. This class
 * retains an instance of the RefModel which contains the commit in which this
 * git note was created.
 * 
 * @author James Moger
 * 
 */
public class GitNote implements Serializable {

	private static final long serialVersionUID = 1L;

	public final String content;
	public final RefModel notesRef;

	public GitNote(RefModel notesRef, String text) {
		this.notesRef = notesRef;
		this.content = text;
	}
}