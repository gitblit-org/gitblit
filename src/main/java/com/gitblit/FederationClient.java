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
package com.gitblit;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.gitblit.manager.FederationManager;
import com.gitblit.manager.GitblitManager;
import com.gitblit.manager.IGitblit;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.RepositoryManager;
import com.gitblit.manager.RuntimeManager;
import com.gitblit.manager.UserManager;
import com.gitblit.models.FederationModel;
import com.gitblit.models.Mailing;
import com.gitblit.service.FederationPullService;
import com.gitblit.utils.FederationUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.XssFilter;
import com.gitblit.utils.XssFilter.AllowXssFilter;

/**
 * Command-line client to pull federated Gitblit repositories.
 *
 * @author James Moger
 *
 */
public class FederationClient {

	public static void main(String[] args) {
		Params params = new Params();
		CmdLineParser parser = new CmdLineParser(params);
		try {
			parser.parseArgument(args);
		} catch (CmdLineException t) {
			usage(parser, t);
		}

		System.out.println("Gitblit Federation Client v" + Constants.getVersion() + " (" + Constants.getBuildDate() + ")");

		// command-line specified base folder
		File baseFolder = new File(System.getProperty("user.dir"));
		if (!StringUtils.isEmpty(params.baseFolder)) {
			baseFolder = new File(params.baseFolder);
		}

		File regFile = com.gitblit.utils.FileUtils.resolveParameter(Constants.baseFolder$, baseFolder, params.registrationsFile);
		FileSettings settings = new FileSettings(regFile.getAbsolutePath());
		List<FederationModel> registrations = new ArrayList<FederationModel>();
		if (StringUtils.isEmpty(params.url)) {
			registrations.addAll(FederationUtils.getFederationRegistrations(settings));
		} else {
			if (StringUtils.isEmpty(params.token)) {
				System.out.println("Must specify --token parameter!");
				System.exit(0);
			}
			FederationModel model = new FederationModel("Gitblit");
			model.url = params.url;
			model.token = params.token;
			model.mirror = params.mirror;
			model.bare = params.bare;
			model.folder = "";
			registrations.add(model);
		}
		if (registrations.size() == 0) {
			System.out.println("No Federation Registrations!  Nothing to do.");
			System.exit(0);
		}

		// command-line specified repositories folder
		if (!StringUtils.isEmpty(params.repositoriesFolder)) {
			settings.overrideSetting(Keys.git.repositoriesFolder, new File(
					params.repositoriesFolder).getAbsolutePath());
		}

		// configure the Gitblit singleton for minimal, non-server operation
		XssFilter xssFilter = new AllowXssFilter();
		RuntimeManager runtime = new RuntimeManager(settings, xssFilter, baseFolder).start();
		NoopNotificationManager notifications = new NoopNotificationManager().start();
		UserManager users = new UserManager(runtime, null).start();
		RepositoryManager repositories = new RepositoryManager(runtime, null, users, null).start();
		FederationManager federation = new FederationManager(runtime, notifications, repositories).start();
		IGitblit gitblit = new GitblitManager(null, null, runtime, null, notifications, users, null, repositories, null, federation, null);

		FederationPullService puller = new FederationPullService(gitblit, federation.getFederationRegistrations()) {
			@Override
			public void reschedule(FederationModel registration) {
				// NOOP
			}
		};
		puller.run();

		System.out.println("Finished.");
		System.exit(0);
	}

	private static void usage(CmdLineParser parser, CmdLineException t) {
		System.out.println(Constants.getGitBlitVersion());
		System.out.println();
		if (t != null) {
			System.out.println(t.getMessage());
			System.out.println();
		}

		if (parser != null) {
			parser.printUsage(System.out);
		}
		System.exit(0);
	}

	/**
	 * Parameters class for FederationClient.
	 */
	private static class Params {

		@Option(name = "--registrations", usage = "Gitblit Federation Registrations File", metaVar = "FILE")
		public String registrationsFile = "${baseFolder}/federation.properties";

		@Option(name = "--url", usage = "URL of Gitblit instance to mirror from", metaVar = "URL")
		public String url;

		@Option(name = "--mirror", usage = "Mirror repositories")
		public boolean mirror;

		@Option(name = "--bare", usage = "Create bare repositories")
		public boolean bare;

		@Option(name = "--token", usage = "Federation Token", metaVar = "TOKEN")
		public String token;

		@Option(name = "--baseFolder", usage = "Base folder for received data", metaVar = "PATH")
		public String baseFolder;

		@Option(name = "--repositoriesFolder", usage = "Destination folder for cloned repositories", metaVar = "PATH")
		public String repositoriesFolder;

	}

	private static class NoopNotificationManager implements INotificationManager {

		@Override
		public NoopNotificationManager start() {
			return this;
		}

		@Override
		public NoopNotificationManager stop() {
			return this;
		}

		@Override
		public boolean isSendingMail() {
			return false;
		}

		@Override
		public void sendMailToAdministrators(String subject, String message) {
		}

		@Override
		public void sendMail(String subject, String message, Collection<String> toAddresses) {
		}

		@Override
		public void sendHtmlMail(String subject, String message, Collection<String> toAddresses) {
		}

		@Override
		public void send(Mailing mailing) {
		}
	}
}
