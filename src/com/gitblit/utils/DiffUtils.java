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
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.AnnotatedLine;

/**
 * DiffUtils is a class of utility methods related to diff, patch, and blame.
 * 
 * The diff methods support pluggable diff output types like Gitblit, Gitweb,
 * and Plain.
 * 
 * @author James Moger
 * 
 */
public class DiffUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(DiffUtils.class);

	/**
	 * Enumeration for the diff output types.
	 */
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

	/**
	 * Returns the complete diff of the specified commit compared to its primary
	 * parent.
	 * 
	 * @param repository
	 * @param commit
	 * @param outputType
	 * @return the diff as a string
	 */
	public static String getCommitDiff(Repository repository, RevCommit commit,
			DiffOutputType outputType) {
		return getDiff(repository, null, commit, null, outputType);
	}

	/**
	 * Returns the diff for the specified file or folder from the specified
	 * commit compared to its primary parent.
	 * 
	 * @param repository
	 * @param commit
	 * @param path
	 * @param outputType
	 * @return the diff as a string
	 */
	public static String getDiff(Repository repository, RevCommit commit, String path,
			DiffOutputType outputType) {
		return getDiff(repository, null, commit, path, outputType);
	}

	/**
	 * Returns the complete diff between the two specified commits.
	 * 
	 * @param repository
	 * @param baseCommit
	 * @param commit
	 * @param outputType
	 * @return the diff as a string
	 */
	public static String getDiff(Repository repository, RevCommit baseCommit, RevCommit commit,
			DiffOutputType outputType) {
		return getDiff(repository, baseCommit, commit, null, outputType);
	}

	/**
	 * Returns the diff between two commits for the specified file.
	 * 
	 * @param repository
	 * @param baseCommit
	 *            if base commit is null the diff is to the primary parent of
	 *            the commit.
	 * @param commit
	 * @param path
	 *            if the path is specified, the diff is restricted to that file
	 *            or folder. if unspecified, the diff is for the entire commit.
	 * @param outputType
	 * @return the diff as a string
	 */
	public static String getDiff(Repository repository, RevCommit baseCommit, RevCommit commit,
			String path, DiffOutputType outputType) {
		String diff = null;
		try {
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
			df.setRepository(repository);
			df.setDiffComparator(cmp);
			df.setDetectRenames(true);

			RevTree commitTree = commit.getTree();
			RevTree baseTree;
			if (baseCommit == null) {
				if (commit.getParentCount() > 0) {
					final RevWalk rw = new RevWalk(repository);
					RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
					rw.dispose();
					baseTree = parent.getTree();
				} else {
					// FIXME initial commit. no parent?!
					baseTree = commitTree;
				}
			} else {
				baseTree = baseCommit.getTree();
			}

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

	/**
	 * Returns the diff between the two commits for the specified file or folder
	 * formatted as a patch.
	 * 
	 * @param repository
	 * @param baseCommit
	 *            if base commit is unspecified, the patch is generated against
	 *            the primary parent of the specified commit.
	 * @param commit
	 * @param path
	 *            if path is specified, the patch is generated only for the
	 *            specified file or folder. if unspecified, the patch is
	 *            generated for the entire diff between the two commits.
	 * @return patch as a string
	 */
	public static String getCommitPatch(Repository repository, RevCommit baseCommit,
			RevCommit commit, String path) {
		String diff = null;
		try {
			final ByteArrayOutputStream os = new ByteArrayOutputStream();
			RawTextComparator cmp = RawTextComparator.DEFAULT;
			PatchFormatter df = new PatchFormatter(os);
			df.setRepository(repository);
			df.setDiffComparator(cmp);
			df.setDetectRenames(true);

			RevTree commitTree = commit.getTree();
			RevTree baseTree;
			if (baseCommit == null) {
				if (commit.getParentCount() > 0) {
					final RevWalk rw = new RevWalk(repository);
					RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
					baseTree = parent.getTree();
				} else {
					// FIXME initial commit. no parent?!
					baseTree = commitTree;
				}
			} else {
				baseTree = baseCommit.getTree();
			}

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

	/**
	 * Returns the list of lines in the specified source file annotated with the
	 * source commit metadata.
	 * 
	 * @param repository
	 * @param blobPath
	 * @param objectId
	 * @return list of annotated lines
	 */
	public static List<AnnotatedLine> blame(Repository repository, String blobPath, String objectId) {
		List<AnnotatedLine> lines = new ArrayList<AnnotatedLine>();
		try {
			ObjectId object;
			if (StringUtils.isEmpty(objectId)) {
				object = JGitUtils.getDefaultBranch(repository);
			} else {
				object = repository.resolve(objectId);
			}
			BlameCommand blameCommand = new BlameCommand(repository);
			blameCommand.setFilePath(blobPath);
			blameCommand.setStartCommit(object);
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
