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

import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.util.FS;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
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
		JCommander jc = new JCommander(params);
		try {
			jc.parse(args);
		} catch (ParameterException t) {
			System.err.println(t.getMessage());
			jc.usage();
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

			System.out.println(MessageFormat.format("adding [gitblit] indexBranch={0} for {1}", params.branch, repo));
			try {
				// load repository config
				File gitDir = FileKey.resolve(new File(folder, repo), FS.DETECTED);
				FileRepository repository = new FileRepository(gitDir);
				FileBasedConfig config = repository.getConfig();
				config.load();
				
				Set<String> indexedBranches = new LinkedHashSet<String>();
				indexedBranches.add(Constants.DEFAULT_BRANCH);
				
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
	 * JCommander Parameters class for AddIndexedBranch.
	 */
	@Parameters(separators = " ")
	private static class Params {

		@Parameter(names = { "--repositoriesFolder" }, description = "The root repositories folder ", required = true)
		public String folder;

		@Parameter(names = { "--branch" }, description = "The branch to index", required = true)
		public String branch = "default";

		@Parameter(names = { "--skip" }, description = "Skip the named repository (simple fizzy matching is supported)", required = false)
		public List<String> exclusions = new ArrayList<String>();
	}
}
