package com.gitblit.wicket.models;

import java.io.Serializable;

import com.gitblit.utils.JGitUtils;


public class PathModel implements Serializable, Comparable<PathModel> {

	private static final long serialVersionUID = 1L;
	
	public final String name;
	public final String path;
	public final long size;
	public final int  mode;
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
		PathModel model = new PathModel("..", parentPath, 0, 0040000, commitId);
		model.isParentPath = true;
		return model;
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
}
