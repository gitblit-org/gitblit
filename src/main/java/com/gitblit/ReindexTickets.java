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
package com.gitblit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.RepositoryManager;
import com.gitblit.manager.RuntimeManager;
import com.gitblit.tickets.ITicketService;
import com.gitblit.tickets.RepositoryTicketService;
import com.gitblit.utils.StringUtils;

/**
 * A command-line tool to reindex all tickets in all repositories when the
 * indexes needs to be rebuilt.
 *
 * @author James Moger
 *
 */
public class ReindexTickets {

	public static void main(String... args) {
		ReindexTickets reindex = new ReindexTickets();

		// filter out the baseFolder parameter
		List<String> filtered = new ArrayList<String>();
		String folder = "data";
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.equals("--baseFolder")) {
				if (i + 1 == args.length) {
					System.out.println("Invalid --baseFolder parameter!");
					System.exit(-1);
				} else if (!".".equals(args[i + 1])) {
					folder = args[i + 1];
				}
				i = i + 1;
			} else {
				filtered.add(arg);
			}
		}

		Params.baseFolder = folder;
		Params params = new Params();
		JCommander jc = new JCommander(params);
		try {
			jc.parse(filtered.toArray(new String[filtered.size()]));
			if (params.help) {
				reindex.usage(jc, null);
				return;
			}
		} catch (ParameterException t) {
			reindex.usage(jc, t);
			return;
		}

		// load the settings
		FileSettings settings = params.FILESETTINGS;
		if (!StringUtils.isEmpty(params.settingsfile)) {
			if (new File(params.settingsfile).exists()) {
				settings = new FileSettings(params.settingsfile);
			}
		}

		// reindex tickets
		reindex.reindex(new File(Params.baseFolder), settings);
		System.exit(0);
	}

	/**
	 * Display the command line usage of ReindexTickets.
	 *
	 * @param jc
	 * @param t
	 */
	protected final void usage(JCommander jc, ParameterException t) {
		System.out.println(Constants.BORDER);
		System.out.println(Constants.getGitBlitVersion());
		System.out.println(Constants.BORDER);
		System.out.println();
		if (t != null) {
			System.out.println(t.getMessage());
			System.out.println();
		}
		if (jc != null) {
			jc.usage();
			System.out
					.println("\nExample:\n  java -gitblit.jar com.gitblit.ReindexTickets --baseFolder c:\\gitblit-data");
		}
		System.exit(0);
	}

	/**
	 * Reindex all tickets
	 *
	 * @param settings
	 */
	protected void reindex(File baseFolder, IStoredSettings settings) {
		// disable some services
		settings.overrideSetting(Keys.web.allowLuceneIndexing, false);
		settings.overrideSetting(Keys.git.enableGarbageCollection, false);
		settings.overrideSetting(Keys.git.enableMirroring, false);
		settings.overrideSetting(Keys.tickets.redisUrl, "");
		settings.overrideSetting(Keys.web.activityCacheDays, 0);

		IRuntimeManager runtimeManager = new RuntimeManager(settings, baseFolder).start();
		IRepositoryManager repositoryManager = new RepositoryManager(runtimeManager, null).start();
		ITicketService ticketService = new RepositoryTicketService(runtimeManager, null, null, repositoryManager).start();
		ticketService.reindex();
		ticketService.stop();
		repositoryManager.stop();
		runtimeManager.stop();
	}

	/**
	 * JCommander Parameters.
	 */
	@Parameters(separators = " ")
	public static class Params {

		public static String baseFolder;

		@Parameter(names = { "-h", "--help" }, description = "Show this help")
		public Boolean help = false;

		private final FileSettings FILESETTINGS = new FileSettings(new File(baseFolder, Constants.PROPERTIES_FILE).getAbsolutePath());

		@Parameter(names = { "--repositoriesFolder" }, description = "Git Repositories Folder")
		public String repositoriesFolder = FILESETTINGS.getString(Keys.git.repositoriesFolder, "git");

		@Parameter(names = { "--settings" }, description = "Path to alternative settings")
		public String settingsfile;
	}
}
