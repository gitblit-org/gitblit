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

import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.Constants;

/**
 * A diff formatter that outputs standard patch content.
 * 
 * @author James Moger
 * 
 */
public class PatchFormatter extends DiffFormatter {

	private final OutputStream os;

	private Map<String, PatchTouple> changes = new HashMap<String, PatchTouple>();

	private PatchTouple currentTouple;

	public PatchFormatter(OutputStream os) {
		super(os);
		this.os = os;
	}

	public void format(DiffEntry entry) throws IOException {
		currentTouple = new PatchTouple();
		changes.put(entry.getNewPath(), currentTouple);
		super.format(entry);
	}

	@Override
	protected void writeLine(final char prefix, final RawText text, final int cur)
			throws IOException {
		switch (prefix) {
		case '+':
			currentTouple.insertions++;
			break;
		case '-':
			currentTouple.deletions++;
			break;
		}
		super.writeLine(prefix, text, cur);
	}

	public String getPatch(RevCommit commit) {
		StringBuilder patch = new StringBuilder();
		// hard-code the mon sep 17 2001 date string.
		// I have no idea why that is there. it seems to be a constant.
		patch.append("From " + commit.getName() + " Mon Sep 17 00:00:00 2001" + "\n");
		patch.append("From: " + JGitUtils.getDisplayName(commit.getAuthorIdent()) + "\n");
		patch.append("Date: "
				+ (new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z").format(new Date(commit
						.getCommitTime() * 1000L))) + "\n");
		patch.append("Subject: [PATCH] " + commit.getShortMessage() + "\n");
		patch.append('\n');
		patch.append("---");
		int maxPathLen = 0;
		int files = 0;
		int insertions = 0;
		int deletions = 0;
		for (String path : changes.keySet()) {
			if (path.length() > maxPathLen) {
				maxPathLen = path.length();
			}
			PatchTouple touple = changes.get(path);
			files++;
			insertions += touple.insertions;
			deletions += touple.deletions;
		}
		int columns = 60;
		int total = insertions + deletions;
		int unit = total / columns + (total % columns > 0 ? 1 : 0);
		if (unit == 0) {
			unit = 1;
		}
		for (String path : changes.keySet()) {
			PatchTouple touple = changes.get(path);
			patch.append("\n " + StringUtils.rightPad(path, maxPathLen, ' ') + " | "
					+ StringUtils.leftPad("" + touple.total(), 4, ' ') + " "
					+ touple.relativeScale(unit));
		}
		patch.append(MessageFormat.format(
				"\n {0} files changed, {1} insertions(+), {2} deletions(-)\n\n", files, insertions,
				deletions));
		patch.append(os.toString());
		patch.append("\n--\n");
		patch.append(Constants.getGitBlitVersion());
		return patch.toString();
	}

	/**
	 * Class that represents the number of insertions and deletions from a
	 * chunk.
	 */
	private static class PatchTouple {
		int insertions;
		int deletions;

		int total() {
			return insertions + deletions;
		}

		String relativeScale(int unit) {
			int plus = insertions / unit;
			int minus = deletions / unit;
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < plus; i++) {
				sb.append('+');
			}
			for (int i = 0; i < minus; i++) {
				sb.append('-');
			}
			return sb.toString();
		}
	}
}
