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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.FederationProposalResult;
import com.gitblit.Constants.FederationRequest;
import com.gitblit.Constants.FederationToken;
import com.gitblit.models.FederationProposal;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.FederationUtils;
import com.gitblit.utils.JsonUtils;

public class FederationTests {

	String url = GitBlitSuite.url;
	String account = GitBlitSuite.account;
	String password = GitBlitSuite.password;

	private static final AtomicBoolean started = new AtomicBoolean(false);

	@BeforeClass
	public static void startGitblit() throws Exception {
		started.set(GitBlitSuite.startGitblit());
	}

	@AfterClass
	public static void stopGitblit() throws Exception {
		if (started.get()) {
			GitBlitSuite.stopGitblit();
		}
	}

	@Test
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
		assertEquals("proposal refused", FederationUtils.propose(url, proposal),
				FederationProposalResult.NO_PROPOSALS);
	}

	@Test
	public void testPullRepositories() throws Exception {
		try {
			String requrl = FederationUtils.asLink(url, "d7cc58921a80b37e0329a4dae2f9af38bf61ef5c",
					FederationRequest.PULL_REPOSITORIES);
			String json = JsonUtils.retrieveJsonString(requrl, null, null);
		} catch (IOException e) {
			if (!e.getMessage().contains("403")) {
				throw e;
			}
		}
	}
}
