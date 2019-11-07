/*
 * Copyright 2014 gitblit.com.
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.RepositoryManager;
import com.gitblit.manager.RuntimeManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.tickets.BranchTicketService;
import com.gitblit.tickets.FileTicketService;
import com.gitblit.tickets.ITicketService;
import com.gitblit.tickets.RedisTicketService;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.XssFilter;
import com.gitblit.utils.XssFilter.AllowXssFilter;

/**
 * A command-line tool to move all tickets from one ticket service to another.
 *
 * @author James Moger
 *
 */
public class MigrateTickets {

	public static void main(String... args) {
		MigrateTickets migrate = new MigrateTickets();

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
		CmdLineParser parser = new CmdLineParser(params);
		try {
			parser.parseArgument(filtered);
			if (params.help) {
				migrate.usage(parser, null);
				return;
			}
		} catch (CmdLineException t) {
			migrate.usage(parser, t);
			return;
		}

		// load the settings
		FileSettings settings = params.FILESETTINGS;
		if (!StringUtils.isEmpty(params.settingsfile)) {
			if (new File(params.settingsfile).exists()) {
				settings = new FileSettings(params.settingsfile);
			}
		}

		// migrate tickets
		migrate.migrate(new File(Params.baseFolder), settings, params.outputServiceName);
		System.exit(0);
	}

	/**
	 * Display the command line usage of MigrateTickets.
	 *
	 * @param parser
	 * @param t
	 */
	protected final void usage(CmdLineParser parser, CmdLineException t) {
		System.out.println(Constants.BORDER);
		System.out.println(Constants.getGitBlitVersion());
		System.out.println(Constants.BORDER);
		System.out.println();
		if (t != null) {
			System.out.println(t.getMessage());
			System.out.println();
		}
		if (parser != null) {
			parser.printUsage(System.out);
			System.out
					.println("\nExample:\n  java -cp gitblit.jar;\"%CD%/ext/*\" com.gitblit.MigrateTickets com.gitblit.tickets.RedisTicketService --baseFolder c:\\gitblit-data");
		}
		System.exit(0);
	}

	/**
	 * Migrate all tickets
	 *
	 * @param baseFolder
	 * @param settings
	 * @param outputServiceName
	 */
	protected void migrate(File baseFolder, IStoredSettings settings, String outputServiceName) {
		// disable some services
		settings.overrideSetting(Keys.web.allowLuceneIndexing, false);
		settings.overrideSetting(Keys.git.enableGarbageCollection, false);
		settings.overrideSetting(Keys.git.enableMirroring, false);
		settings.overrideSetting(Keys.web.activityCacheDays, 0);
		settings.overrideSetting(ITicketService.SETTING_UPDATE_DIFFSTATS, false);

		XssFilter xssFilter = new AllowXssFilter();
		IRuntimeManager runtimeManager = new RuntimeManager(settings, xssFilter, baseFolder).start();
		IRepositoryManager repositoryManager = new RepositoryManager(runtimeManager, null, null).start();

		String inputServiceName = settings.getString(Keys.tickets.service, BranchTicketService.class.getSimpleName());
		if (StringUtils.isEmpty(inputServiceName)) {
			System.err.println(MessageFormat.format("Please define a ticket service in \"{0}\"", Keys.tickets.service));
			System.exit(1);
		}

		ITicketService inputService = null;
		ITicketService outputService = null;
		try {
			inputService = getService(inputServiceName, runtimeManager, repositoryManager);
			outputService = getService(outputServiceName, runtimeManager, repositoryManager);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		if (!inputService.isReady()) {
			System.err.println(String.format("%s INPUT service is not ready, check config.", inputService.getClass().getSimpleName()));
			System.exit(1);
		}

		if (!outputService.isReady()) {
			System.err.println(String.format("%s OUTPUT service is not ready, check config.", outputService.getClass().getSimpleName()));
			System.exit(1);
		}

		// migrate tickets
		long start = System.nanoTime();
		long totalTickets = 0;
		long totalChanges = 0;
		for (RepositoryModel repository : repositoryManager.getRepositoryModels()) {
			Set<Long> ids = inputService.getIds(repository);
			if (ids == null || ids.isEmpty()) {
				// nothing to migrate
				continue;
			}

			// delete any tickets we may have in the output ticket service
			outputService.deleteAll(repository);

			for (long id : ids) {
				List<Change> journal = inputService.getJournal(repository, id);
				if (journal == null || journal.size() == 0) {
					continue;
				}
				TicketModel ticket = outputService.createTicket(repository, id, journal.get(0));
				if (ticket == null) {
					System.err.println(String.format("Failed to migrate %s #%s", repository.name, id));
					System.exit(1);
				}
				totalTickets++;
				System.out.println(String.format("%s #%s: %s", repository.name, ticket.number, ticket.title));
				for (int i = 1; i < journal.size(); i++) {
					TicketModel updated = outputService.updateTicket(repository, ticket.number, journal.get(i));
					if (updated != null) {
						System.out.println(String.format("   applied change %d", i));
						totalChanges++;
					} else {
						System.err.println(String.format("Failed to apply change %d:\n%s", i, journal.get(i)));
						System.exit(1);
					}
				}
			}
		}

		inputService.stop();
		outputService.stop();

		repositoryManager.stop();
		runtimeManager.stop();

		long end = System.nanoTime();

		System.out.println(String.format("Migrated %d tickets composed of %d journal entries in %d seconds",
				totalTickets, totalTickets + totalChanges, TimeUnit.NANOSECONDS.toSeconds(end - start)));
	}

	protected ITicketService getService(String serviceName, IRuntimeManager runtimeManager, IRepositoryManager repositoryManager) throws Exception {
		ITicketService service = null;
		Class<?> serviceClass = Class.forName(serviceName);
		if (RedisTicketService.class.isAssignableFrom(serviceClass)) {
			// Redis ticket service
			service = new RedisTicketService(runtimeManager, null, null, null, repositoryManager).start();
		} else if (BranchTicketService.class.isAssignableFrom(serviceClass)) {
			// Branch ticket service
			service = new BranchTicketService(runtimeManager, null, null, null, repositoryManager).start();
		} else if (FileTicketService.class.isAssignableFrom(serviceClass)) {
			// File ticket service
			service = new FileTicketService(runtimeManager, null, null, null, repositoryManager).start();
		} else {
			System.err.println("Unknown ticket service " + serviceName);
		}
		return service;
	}

	/**
	 * Parameters.
	 */
	public static class Params {

		public static String baseFolder;

		@Option(name = "--help", aliases = { "-h"}, usage = "Show this help")
		public Boolean help = false;

		private final FileSettings FILESETTINGS = new FileSettings(new File(baseFolder, Constants.PROPERTIES_FILE).getAbsolutePath());

		@Option(name = "--repositoriesFolder", usage = "Git Repositories Folder", metaVar = "PATH")
		public String repositoriesFolder = FILESETTINGS.getString(Keys.git.repositoriesFolder, "git");

		@Option(name = "--settings", usage = "Path to alternative settings", metaVar = "FILE")
		public String settingsfile;

		@Argument(index = 0, required = true, metaVar = "OUTPUTSERVICE", usage = "The destination/output ticket service")
		public String outputServiceName;
	}
}
