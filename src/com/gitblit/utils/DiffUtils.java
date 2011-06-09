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
package com.gitblit.utils;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.BlameCommand;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.AnnotatedLine;

public class DiffUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(DiffUtils.class);

	public static enum DiffOutputType {
		PLAIN, GITWEB, GITBLIT;

		public static DiffOutputType forName(String name) {
			for (DiffOutputType type : values()) {
				if (type.name().equalsIgnoreCase(name)) {
					return type;
				}
			}
			return null;
		}
	}

	public static String getCommitDiff(Repository r, RevCommit commit, DiffOutputType outputType) {
		return getDiff(r, null, commit, null, outputType);
	}

	public static String getDiff(Repository r, RevCommit commit, String path,
			DiffOutputType outputType) {
		return getDiff(r, null, commit, path, outputType);
	}

	public static String getDiff(Repository r, RevCommit baseCommit, RevCommit commit,
			DiffOutputType outputType) {
		return getDiff(r, baseCommit, commit, null, outputType);
	}

	public static String getDiff(Repository r, RevCommit baseCommit, RevCommit commit, String path,
			DiffOutputType outputType) {
		String diff = null;
		try {
			RevTree baseTree;
			if (baseCommit == null) {
				final RevWalk rw = new RevWalk(r);
				RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
				rw.dispose();
				baseTree = parent.getTree();
			} else {
				baseTree = baseCommit.getTree();
			}

			RevTree commitTree = commit.getTree();

			final TreeWalk walk = new TreeWalk(r);
			walk.reset();
			walk.setRecursive(true);
			walk.addTree(baseTree);
			walk.addTree(commitTree);
			walk.setFilter(TreeFilter.ANY_DIFF);

			final ByteArrayOutputStream os = new ByteArrayOutputStream();
			RawTextComparator cmp = RawTextComparator.DEFAULT;
			DiffFormatter df;
			switch (outputType) {
			case GITWEB:
				df = new GitWebDiffFormatter(os);
				break;
			case GITBLIT:
				df = new GitBlitDiffFormatter(os);
				break;
			case PLAIN:
			default:
				df = new DiffFormatter(os);
				break;
			}
			df.setRepository(r);
			df.setDiffComparator(cmp);
			df.setDetectRenames(true);
			List<DiffEntry> diffEntries = df.scan(baseTree, commitTree);
			if (path != null && path.length() > 0) {
				for (DiffEntry diffEntry : diffEntries) {
					if (diffEntry.getNewPath().equalsIgnoreCase(path)) {
						df.format(diffEntry);
						break;
					}
				}
			} else {
				df.format(diffEntries);
			}
			if (df instanceof GitWebDiffFormatter) {
				// workaround for complex private methods in DiffFormatter
				diff = ((GitWebDiffFormatter) df).getHtml();
			} else {
				diff = os.toString();
			}
			df.flush();
		} catch (Throwable t) {
			LOGGER.error("failed to generate commit diff!", t);
		}
		return diff;
	}

	public static String getCommitPatch(Repository r, RevCommit baseCommit, RevCommit commit,
			String path) {
		String diff = null;
		try {
			RevTree baseTree;
			if (baseCommit == null) {
				final RevWalk rw = new RevWalk(r);
				RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
				baseTree = parent.getTree();
			} else {
				baseTree = baseCommit.getTree();
			}
			RevTree commitTree = commit.getTree();

			final TreeWalk walk = new TreeWalk(r);
			walk.reset();
			walk.setRecursive(true);
			walk.addTree(baseTree);
			walk.addTree(commitTree);
			walk.setFilter(TreeFilter.ANY_DIFF);

			final ByteArrayOutputStream os = new ByteArrayOutputStream();
			RawTextComparator cmp = RawTextComparator.DEFAULT;
			PatchFormatter df = new PatchFormatter(os);
			df.setRepository(r);
			df.setDiffComparator(cmp);
			df.setDetectRenames(true);
			List<DiffEntry> diffEntries = df.scan(baseTree, commitTree);
			if (path != null && path.length() > 0) {
				for (DiffEntry diffEntry : diffEntries) {
					if (diffEntry.getNewPath().equalsIgnoreCase(path)) {
						df.format(diffEntry);
						break;
					}
				}
			} else {
				df.format(diffEntries);
			}
			diff = df.getPatch(commit);
			df.flush();
		} catch (Throwable t) {
			LOGGER.error("failed to generate commit diff!", t);
		}
		return diff;
	}

	public static List<AnnotatedLine> blame(Repository r, String blobPath, String objectId) {
		List<AnnotatedLine> lines = new ArrayList<AnnotatedLine>();
		try {
			if (StringUtils.isEmpty(objectId)) {
				objectId = Constants.HEAD;
			}
			BlameCommand blameCommand = new BlameCommand(r);
			blameCommand.setFilePath(blobPath);
			blameCommand.setStartCommit(r.resolve(objectId));
			BlameResult blameResult = blameCommand.call();
			RawText rawText = blameResult.getResultContents();
			int length = rawText.size();
			for (int i = 0; i < length; i++) {
				RevCommit commit = blameResult.getSourceCommit(i);
				AnnotatedLine line = new AnnotatedLine(commit, i + 1, rawText.getString(i));
				lines.add(line);
			}
		} catch (Throwable t) {
			LOGGER.error("failed to generate blame!", t);
		}
		return lines;
	}
}
