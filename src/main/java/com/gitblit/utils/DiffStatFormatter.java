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
package com.gitblit.utils;

import java.io.IOException;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.io.NullOutputStream;

import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.utils.DiffUtils.DiffStat;

/**
 * Calculates a DiffStat.
 *
 * @author James Moger
 *
 */
public class DiffStatFormatter extends DiffFormatter {

	private final DiffStat diffStat;

	private PathChangeModel path;

	public DiffStatFormatter(String commitId, Repository repository) {
		super(NullOutputStream.INSTANCE);
		diffStat = new DiffStat(commitId, repository);
	}

	@Override
	public void format(DiffEntry entry) throws IOException {
		path = diffStat.addPath(entry);
		super.format(entry);
	}

	@Override
	protected void writeLine(final char prefix, final RawText text, final int cur)
			throws IOException {
		path.update(prefix);
	}

	public DiffStat getDiffStat() {
		return diffStat;
	}
}
