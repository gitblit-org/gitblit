/*
 * Copyright 2012 gitblit.com.
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
package com.gitblit;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FS;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.gitblit.models.RefModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;

/**
 * Utility class to add an indexBranch setting to matching repositories.
 *
 * @author James Moger
 *
 */
public class AddIndexedBranch {

	public static void main(String... args) {
		Params params = new Params();
		CmdLineParser parser = new CmdLineParser(params);
		try {
			parser.parseArgument(args);
		} catch (CmdLineException t) {
			System.err.println(t.getMessage());
			parser.printUsage(System.out);
			return;
		}

		// create a lowercase set of excluded repositories
		Set<String> exclusions = new TreeSet<String>();
		for (String exclude : params.exclusions) {
			exclusions.add(exclude.toLowerCase());
		}

		// determine available repositories
		File folder = new File(params.folder);
		List<String> repoList = JGitUtils.getRepositoryList(folder, false, true, -1, null);

		int modCount = 0;
		int skipCount = 0;
		for (String repo : repoList) {
			boolean skip = false;
			for (String exclusion : exclusions) {
				if (StringUtils.fuzzyMatch(repo, exclusion)) {
					skip = true;
					break;
				}
			}

			if (skip) {
				System.out.println("skipping " + repo);
				skipCount++;
				continue;
			}


			try {
				// load repository config
				File gitDir = FileKey.resolve(new File(folder, repo), FS.DETECTED);
				Repository repository = new FileRepositoryBuilder().setGitDir(gitDir).build();
				StoredConfig config = repository.getConfig();
				config.load();

				Set<String> indexedBranches = new LinkedHashSet<String>();

				// add all local branches to index
				if (params.addAllLocalBranches) {
					List<RefModel> list = JGitUtils.getLocalBranches(repository, true, -1);
					for (RefModel refModel : list) {
						System.out.println(MessageFormat.format("adding [gitblit] indexBranch={0} for {1}", refModel.getName(), repo));
						indexedBranches.add(refModel.getName());
					}
				}
				else {
					// add only one branch to index ('default' if not specified)
					System.out.println(MessageFormat.format("adding [gitblit] indexBranch={0} for {1}", params.branch, repo));
					indexedBranches.add(params.branch);
				}

				String [] branches = config.getStringList("gitblit", null, "indexBranch");
				if (!ArrayUtils.isEmpty(branches)) {
					for (String branch : branches) {
						indexedBranches.add(branch);
					}
				}
				config.setStringList("gitblit", null, "indexBranch", new ArrayList<String>(indexedBranches));
				config.save();
				modCount++;
			} catch (Exception e) {
				System.err.println(repo);
				e.printStackTrace();
			}
		}

		System.out.println(MessageFormat.format("updated {0} repository configurations, skipped {1}", modCount, skipCount));
	}



	/**
	 * Parameters class for AddIndexedBranch.
	 */
	private static class Params {

		@Option(name =  "--repositoriesFolder", usage = "The root repositories folder ", required = true, metaVar = "PATH")
		public String folder;

		@Option(name = "--branch", usage = "The branch to index", metaVar = "BRANCH")
		public String branch = "default";

		@Option(name = "--skip", usage = "Skip the named repository (simple fizzy matching is supported)")
		public List<String> exclusions = new ArrayList<String>();

		@Option(name = "--all-local-branches", usage = "Add all local branches to index. If specified, the --branch parameter is not considered.")
		public boolean addAllLocalBranches;
	}
}
