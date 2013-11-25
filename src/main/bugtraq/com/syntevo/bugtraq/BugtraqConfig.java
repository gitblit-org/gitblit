/*
 * Copyright (c) 2013 by syntevo GmbH. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  o Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  o Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  o Neither the name of syntevo GmbH nor the names of
 *    its contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.syntevo.bugtraq;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BugtraqConfig {

	// Constants ==============================================================

	private static final String DOT_GIT_BUGTRAQ = ".gitbugtraq";

	private static final String BUGTRAQ = "bugtraq";

	private static final String URL = "url";
	private static final String ENABLED = "enabled";
	private static final String LOG_REGEX = "logRegex";

	// Static =================================================================

	@Nullable
	public static BugtraqConfig read(@NotNull Repository repository) throws IOException, ConfigInvalidException {
		final Config baseConfig = getBaseConfig(repository);
		final Set<String> allNames = new HashSet<String>();
		final Config config = repository.getConfig();
		allNames.addAll(config.getSubsections(BUGTRAQ));
		if (baseConfig != null) {
			allNames.addAll(baseConfig.getSubsections(BUGTRAQ));
		}

		final List<BugtraqEntry> entries = new ArrayList<BugtraqEntry>();
		for (String name : allNames) {
			final String url = getString(name, URL, config, baseConfig);
			final String enabled = getString(name, ENABLED, config, baseConfig);
			if (enabled != null && !"true".equals(enabled)) {
				continue;
			}

			final String logIdRegex = getString(name, LOG_REGEX, config, baseConfig);
			if (url == null || logIdRegex == null) {
				return null;
			}

			final List<String> logIdRegexs = new ArrayList<String>();
			logIdRegexs.add(logIdRegex);

			for (int index = 1; index < Integer.MAX_VALUE; index++) {
				final String logIdRegexN = getString(name, LOG_REGEX + index, config, baseConfig);
				if (logIdRegexN == null) {
					break;
				}

				logIdRegexs.add(logIdRegexN);
			}

			entries.add(new BugtraqEntry(url, logIdRegexs));
		}

		if (entries.isEmpty()) {
			return null;
		}

		return new BugtraqConfig(entries);
	}

	// Fields =================================================================

	@NotNull
	private final List<BugtraqEntry> entries;

	// Setup ==================================================================

	BugtraqConfig(@NotNull List<BugtraqEntry> entries) {
		this.entries = entries;
	}

	// Accessing ==============================================================

	@NotNull
	public List<BugtraqEntry> getEntries() {
		return Collections.unmodifiableList(entries);
	}

	// Utils ==================================================================

	@Nullable
	private static Config getBaseConfig(Repository repository) throws IOException, ConfigInvalidException {
		final Config baseConfig;
		if (repository.isBare()) {
			// read bugtraq config directly from the repository
			String content = null;
			String head = repository.getFullBranch();
			RevWalk rw = new RevWalk(repository);
			TreeWalk tw = new TreeWalk(repository);
			tw.setFilter(PathFilterGroup.createFromStrings(DOT_GIT_BUGTRAQ));
			try {
				ObjectId headId = repository.resolve(head);
				RevCommit commit = rw.parseCommit(headId);
				RevTree tree = commit.getTree();
				tw.reset(tree);
				while (tw.next()) {
					if (tw.isSubtree()) {
						tw.enterSubtree();
						continue;
					}
					ObjectId entid = tw.getObjectId(0);
					FileMode entmode = tw.getFileMode(0);
					if (entmode == FileMode.REGULAR_FILE) {
						RevObject ro = rw.lookupAny(entid, entmode.getObjectType());
						rw.parseBody(ro);
						ByteArrayOutputStream os = new ByteArrayOutputStream();
						ObjectLoader ldr = repository.open(ro.getId(), Constants.OBJ_BLOB);
						byte[] tmp = new byte[4096];
						InputStream in = ldr.openStream();
						int n;
						while ((n = in.read(tmp)) > 0) {
							os.write(tmp, 0, n);
						}
						in.close();
						content = new String(os.toByteArray(), commit.getEncoding());
					}
				}
			} finally {
				rw.dispose();
				tw.release();
			}

			if (content == null) {
				// config not found
				baseConfig = null;
			} else {
				// parse the config
				Config cfg = new Config();
				cfg.fromText(content);
				baseConfig = new StoredConfig(cfg) {
					@Override
					public void save() throws IOException {
					}

					@Override
					public void load() throws IOException, ConfigInvalidException {
					}
				};
			}
		} else {
			// read bugtraq config from work tree
			final File baseFile = new File(repository.getWorkTree(), DOT_GIT_BUGTRAQ);
			if (baseFile.isFile()) {
				FileBasedConfig fileConfig = new FileBasedConfig(baseFile, repository.getFS());
				fileConfig.load();
				baseConfig = fileConfig;
			}
			else {
				baseConfig = null;
			}
		}
		return baseConfig;
	}

	@Nullable
	private static String getString(@NotNull String subsection, @NotNull String key, @NotNull Config config, @Nullable Config baseConfig) {
		final String value = config.getString(BUGTRAQ, subsection, key);
		if (value != null) {
			return trimMaybeNull(value);
		}

		if (baseConfig != null) {
			return trimMaybeNull(baseConfig.getString(BUGTRAQ, subsection, key));
		}

		return value;
	}

	@Nullable
	private static String trimMaybeNull(@Nullable String string) {
		if (string == null) {
			return null;
		}

		string = string.trim();
		if (string.length() == 0) {
			return null;
		}

		return string;
	}
}