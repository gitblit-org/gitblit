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

import org.eclipse.jgit.diff.DiffEntry.ChangeType;

import com.gitblit.utils.JGitUtils;

public class PathModel implements Serializable, Comparable<PathModel> {

	private static final long serialVersionUID = 1L;

	public final String name;
	public final String path;
	public final long size;
	public final int mode;
	public final String commitId;
	public boolean isParentPath;

	public PathModel(String name, String path, long size, int mode, String commitId) {
		this.name = name;
		this.path = path;
		this.size = size;
		this.mode = mode;
		this.commitId = commitId;
	}

	public boolean isTree() {
		return JGitUtils.isTreeFromMode(mode);
	}

	public static PathModel getParentPath(String basePath, String commitId) {
		String parentPath = null;
		if (basePath.lastIndexOf('/') > -1) {
			parentPath = basePath.substring(0, basePath.lastIndexOf('/'));
		}
		PathModel model = new PathModel("..", parentPath, 0, 40000, commitId);
		model.isParentPath = true;
		return model;
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
			return path.compareTo(o.path);
		} else if (isTree && !otherTree) {
			return -1;
		}
		return 1;
	}

	public static class PathChangeModel extends PathModel {

		private static final long serialVersionUID = 1L;

		public final ChangeType changeType;

		public PathChangeModel(String name, String path, long size, int mode, String commitId,
				ChangeType type) {
			super(name, path, size, mode, commitId);
			this.changeType = type;
		}

		@Override
		public int hashCode() {
			return super.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			return super.equals(o);
		}
	}
}
