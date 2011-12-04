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
package com.gitblit.tests;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import junit.framework.TestCase;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.FederationProposalResult;
import com.gitblit.Constants.FederationRequest;
import com.gitblit.Constants.FederationToken;
import com.gitblit.GitBlitServer;
import com.gitblit.models.FederationProposal;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.FederationUtils;
import com.gitblit.utils.JsonUtils;

public class FederationTests extends TestCase {

	int port = 8180;

	int shutdownPort = 8181;

	@Override
	protected void setUp() throws Exception {
		// Start a Gitblit instance
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				GitBlitServer.main("--httpPort", "" + port, "--httpsPort", "0", "--shutdownPort",
						"" + shutdownPort, "--repositoriesFolder",
						"\"" + GitBlitSuite.REPOSITORIES.getAbsolutePath() + "\"", "--userService",
						"distrib/users.conf");
			}
		});

		// Wait a few seconds for it to be running
		Thread.sleep(2500);
	}

	@Override
	protected void tearDown() throws Exception {
		// Stop Gitblit
		GitBlitServer.main("--stop", "--shutdownPort", "" + shutdownPort);

		// Wait a few seconds for it to be running
		Thread.sleep(2500);
	}

	public void testProposal() throws Exception {
		// create dummy repository data
		Map<String, RepositoryModel> repositories = new HashMap<String, RepositoryModel>();
		for (int i = 0; i < 5; i++) {
			RepositoryModel model = new RepositoryModel();
			model.accessRestriction = AccessRestrictionType.VIEW;
			model.description = "cloneable repository " + i;
			model.lastChange = new Date();
			model.owner = "adminuser";
			model.name = "repo" + i + ".git";
			model.size = "5 MB";
			model.hasCommits = true;
			repositories.put(model.name, model);
		}

		FederationProposal proposal = new FederationProposal("http://testurl", FederationToken.ALL,
				"testtoken", repositories);

		// propose federation
		assertEquals("proposal refused",
				FederationUtils.propose("http://localhost:" + port, proposal),
				FederationProposalResult.NO_PROPOSALS);
	}

	public void testPullRepositories() throws Exception {
		try {
			String url = FederationUtils.asLink("http://localhost:" + port, "testtoken",
					FederationRequest.PULL_REPOSITORIES);
			String json = JsonUtils.retrieveJsonString(url, null, null);
		} catch (IOException e) {
			if (!e.getMessage().contains("403")) {
				throw e;
			}
		}
	}
}
