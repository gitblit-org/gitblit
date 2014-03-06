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

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.FileMode;

/**
 * PathModel is a serializable model class that represents a file or a folder,
 * including all its metadata and associated commit id.
 *
 * @author James Moger
 *
 */
public class PathModel implements Serializable, Comparable<PathModel> {

	private static final long serialVersionUID = 1L;

	public final String name;
	public final String path;
	public final long size;
	public final int mode;
	public final String objectId;
	public final String commitId;
	public boolean isParentPath;

	public PathModel(String name, String path, long size, int mode, String objectId, String commitId) {
		this.name = name;
		this.path = path;
		this.size = size;
		this.mode = mode;
		this.objectId = objectId;
		this.commitId = commitId;
	}

	public boolean isSymlink() {
		return FileMode.SYMLINK.equals(mode);
	}

	public boolean isSubmodule() {
		return FileMode.GITLINK.equals(mode);
	}

	public boolean isTree() {
		return FileMode.TREE.equals(mode);
	}

	public boolean isFile() {
		return FileMode.REGULAR_FILE.equals(mode)
				|| FileMode.EXECUTABLE_FILE.equals(mode)
				|| (FileMode.MISSING.equals(mode) && !isSymlink() && !isSubmodule() && !isTree());
	}

	@Override
	public int hashCode() {
		return commitId.hashCode() + path.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof PathModel) {
			PathModel other = (PathModel) o;
			return this.path.equals(other.path);
		}
		return super.equals(o);
	}

	@Override
	public int compareTo(PathModel o) {
		boolean isTree = isTree();
		boolean otherTree = o.isTree();
		if (isTree && otherTree) {
			return path.compareTo(o.path);
		} else if (!isTree && !otherTree) {
			if (isSubmodule() && o.isSubmodule()) {
				return path.compareTo(o.path);
			} else if (isSubmodule()) {
				return -1;
			} else if (o.isSubmodule()) {
				return 1;
			}
			return path.compareTo(o.path);
		} else if (isTree && !otherTree) {
			return -1;
		}
		return 1;
	}

	/**
	 * PathChangeModel is a serializable class that represents a file changed in
	 * a commit.
	 *
	 * @author James Moger
	 *
	 */
	public static class PathChangeModel extends PathModel {

		private static final long serialVersionUID = 1L;

		public ChangeType changeType;

		public int insertions;

		public int deletions;

		public PathChangeModel(String name, String path, long size, int mode, String objectId,
				String commitId, ChangeType type) {
			super(name, path, size, mode, objectId, commitId);
			this.changeType = type;
		}

		public void update(char op) {
			switch (op) {
			case '+':
				insertions++;
				break;
			case '-':
				deletions++;
				break;
			default:
				break;
			}
		}

		@Override
		public int hashCode() {
			return super.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			return super.equals(o);
		}

		public static PathChangeModel from(DiffEntry diff, String commitId) {
			PathChangeModel pcm;
			if (diff.getChangeType().equals(ChangeType.DELETE)) {
				pcm = new PathChangeModel(diff.getOldPath(), diff.getOldPath(), 0, diff
						.getNewMode().getBits(), diff.getOldId().name(), commitId, diff
						.getChangeType());
			} else if (diff.getChangeType().equals(ChangeType.RENAME)) {
				pcm = new PathChangeModel(diff.getOldPath(), diff.getNewPath(), 0, diff
						.getNewMode().getBits(), diff.getNewId().name(), commitId, diff
						.getChangeType());
			} else {
				pcm = new PathChangeModel(diff.getNewPath(), diff.getNewPath(), 0, diff
						.getNewMode().getBits(), diff.getNewId().name(), commitId, diff
						.getChangeType());
			}
			return pcm;
		}
	}
}
