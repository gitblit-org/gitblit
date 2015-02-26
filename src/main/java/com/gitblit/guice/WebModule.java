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
package com.gitblit.guice;

import java.util.HashMap;
import java.util.Map;

import com.gitblit.Constants;
import com.gitblit.servlet.BranchGraphServlet;
import com.gitblit.servlet.DownloadZipFilter;
import com.gitblit.servlet.DownloadZipServlet;
import com.gitblit.servlet.EnforceAuthenticationFilter;
import com.gitblit.servlet.FederationServlet;
import com.gitblit.servlet.GitFilter;
import com.gitblit.servlet.GitServlet;
import com.gitblit.servlet.LogoServlet;
import com.gitblit.servlet.PagesFilter;
import com.gitblit.servlet.PagesServlet;
import com.gitblit.servlet.ProxyFilter;
import com.gitblit.servlet.PtServlet;
import com.gitblit.servlet.RawFilter;
import com.gitblit.servlet.RawServlet;
import com.gitblit.servlet.RobotsTxtServlet;
import com.gitblit.servlet.RpcFilter;
import com.gitblit.servlet.RpcServlet;
import com.gitblit.servlet.SparkleShareInviteServlet;
import com.gitblit.servlet.SyndicationFilter;
import com.gitblit.servlet.SyndicationServlet;
import com.gitblit.wicket.GitblitWicketFilter;
import com.google.common.base.Joiner;
import com.google.inject.servlet.ServletModule;

/**
 * Defines all the web servlets & filters.
 *
 * @author James Moger
 *
 */
public class WebModule extends ServletModule {

	final static String ALL = "/*";

	@Override
	protected void configureServlets() {
		// servlets
		serve(fuzzy(Constants.R_PATH), fuzzy(Constants.GIT_PATH)).with(GitServlet.class);
		serve(fuzzy(Constants.RAW_PATH)).with(RawServlet.class);
		serve(fuzzy(Constants.PAGES)).with(PagesServlet.class);
		serve(fuzzy(Constants.RPC_PATH)).with(RpcServlet.class);
		serve(fuzzy(Constants.ZIP_PATH)).with(DownloadZipServlet.class);
		serve(fuzzy(Constants.SYNDICATION_PATH)).with(SyndicationServlet.class);

		serve(fuzzy(Constants.FEDERATION_PATH)).with(FederationServlet.class);
		serve(fuzzy(Constants.SPARKLESHARE_INVITE_PATH)).with(SparkleShareInviteServlet.class);
		serve(fuzzy(Constants.BRANCH_GRAPH_PATH)).with(BranchGraphServlet.class);
		serve(Constants.PT_PATH).with(PtServlet.class);
		serve("/robots.txt").with(RobotsTxtServlet.class);
		serve("/logo.png").with(LogoServlet.class);

		// global filters
		filter(ALL).through(ProxyFilter.class);
		filter(ALL).through(EnforceAuthenticationFilter.class);

		// security filters
		filter(fuzzy(Constants.R_PATH), fuzzy(Constants.GIT_PATH)).through(GitFilter.class);
		filter(fuzzy(Constants.RAW_PATH)).through(RawFilter.class);
		filter(fuzzy(Constants.PAGES)).through(PagesFilter.class);
		filter(fuzzy(Constants.RPC_PATH)).through(RpcFilter.class);
		filter(fuzzy(Constants.ZIP_PATH)).through(DownloadZipFilter.class);
		filter(fuzzy(Constants.SYNDICATION_PATH)).through(SyndicationFilter.class);

		// Wicket
		String toIgnore = Joiner.on(",").join(Constants.R_PATH, Constants.GIT_PATH, Constants.RAW_PATH,
				Constants.PAGES, Constants.RPC_PATH, Constants.ZIP_PATH, Constants.SYNDICATION_PATH,
				Constants.FEDERATION_PATH, Constants.SPARKLESHARE_INVITE_PATH, Constants.BRANCH_GRAPH_PATH,
				Constants.PT_PATH, "/robots.txt", "/logo.png");

		Map<String, String> params = new HashMap<String, String>();
		params.put(GitblitWicketFilter.FILTER_MAPPING_PARAM, ALL);
		params.put(GitblitWicketFilter.IGNORE_PATHS_PARAM, toIgnore);
		filter(ALL).through(GitblitWicketFilter.class, params);
	}

	private String fuzzy(String path) {
		if (path.endsWith(ALL)) {
			return path;
		} else if (path.endsWith("/")) {
			return path + "*";
		}
		return path + ALL;
	}
}
