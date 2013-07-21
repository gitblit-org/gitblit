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
package com.gitblit.models;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

import com.gitblit.Constants.FederationToken;

/**
 * Represents a proposal from a Gitblit instance to pull its repositories.
 */
public class FederationProposal implements Serializable {

	private static final long serialVersionUID = 1L;

	public Date received;

	public String name;

	public String url;

	public FederationToken tokenType;

	public String token;
	
	public String message;

	public Map<String, RepositoryModel> repositories;

	/**
	 * The constructor for a federation proposal.
	 * 
	 * @param url
	 *            the url of the source Gitblit instance
	 * @param tokenType
	 *            the type of token from the source Gitblit instance
	 * @param token
	 *            the federation token from the source Gitblit instance
	 * @param repositories
	 *            the map of repositories to be pulled from the source Gitblit
	 *            instance keyed by the repository clone url
	 */
	public FederationProposal(String url, FederationToken tokenType, String token,
			Map<String, RepositoryModel> repositories) {
		this.received = new Date();
		this.url = url;
		this.tokenType = tokenType;
		this.token = token;
		this.message = "";
		this.repositories = repositories;
		try {
			// determine server name and set that as the proposal name
			name = url.substring(url.indexOf("//") + 2);
			if (name.contains("/")) {
				name = name.substring(0, name.indexOf('/'));
			}
			name = name.replace(".", "").replace(";", "").replace(":", "").replace("-", "");
		} catch (Exception e) {
			name = Long.toHexString(System.currentTimeMillis());
		}
	}

	@Override
	public String toString() {
		return "Federation Proposal (" + url + ")";
	}
}
