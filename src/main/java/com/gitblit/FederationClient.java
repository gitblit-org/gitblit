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
import java.util.List;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.gitblit.models.FederationModel;
import com.gitblit.utils.FederationUtils;
import com.gitblit.utils.StringUtils;

/**
 * Command-line client to pull federated Gitblit repositories.
 * 
 * @author James Moger
 * 
 */
public class FederationClient {

	public static void main(String[] args) {
		Params params = new Params();
		JCommander jc = new JCommander(params);
		try {
			jc.parse(args);
		} catch (ParameterException t) {
			usage(jc, t);
		}

		IStoredSettings settings = new FileSettings(params.registrationsFile);
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
			model.frequency = params.frequency;
			model.folder = "";
			registrations.add(model);
		}
		if (registrations.size() == 0) {
			System.out.println("No Federation Registrations!  Nothing to do.");
			System.exit(0);
		}
		
		System.out.println("Gitblit Federation Client v" + Constants.getVersion() + " (" + Constants.getBuildDate() + ")");

		// command-line specified repositories folder
		if (!StringUtils.isEmpty(params.repositoriesFolder)) {
			settings.overrideSetting(Keys.git.repositoriesFolder, new File(
					params.repositoriesFolder).getAbsolutePath());
		}

		// configure the Gitblit singleton for minimal, non-server operation
		GitBlit.self().configureContext(settings, null, false);
		FederationPullExecutor executor = new FederationPullExecutor(registrations, params.isDaemon);
		executor.run();
		if (!params.isDaemon) {
			System.out.println("Finished.");
			System.exit(0);
		}
	}

	private static void usage(JCommander jc, ParameterException t) {
		System.out.println(Constants.getGitBlitVersion());
		System.out.println();
		if (t != null) {
			System.out.println(t.getMessage());
			System.out.println();
		}

		if (jc != null) {
			jc.usage();
		}
		System.exit(0);
	}

	/**
	 * JCommander Parameters class for FederationClient.
	 */
	@Parameters(separators = " ")
	private static class Params {

		@Parameter(names = { "--registrations" }, description = "Gitblit Federation Registrations File", required = false)
		public String registrationsFile = "federation.properties";

		@Parameter(names = { "--daemon" }, description = "Runs in daemon mode to schedule and pull repositories", required = false)
		public boolean isDaemon;

		@Parameter(names = { "--url" }, description = "URL of Gitblit instance to mirror from", required = false)
		public String url;

		@Parameter(names = { "--mirror" }, description = "Mirror repositories", required = false)
		public boolean mirror;

		@Parameter(names = { "--bare" }, description = "Create bare repositories", required = false)
		public boolean bare;

		@Parameter(names = { "--token" }, description = "Federation Token", required = false)
		public String token;

		@Parameter(names = { "--frequency" }, description = "Period to wait between pull attempts (requires --daemon)", required = false)
		public String frequency = "60 mins";

		@Parameter(names = { "--repositoriesFolder" }, description = "Destination folder for cloned repositories", required = false)
		public String repositoriesFolder;

	}
}
