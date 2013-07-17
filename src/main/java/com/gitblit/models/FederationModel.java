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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.gitblit.Constants.FederationPullStatus;
import com.gitblit.utils.StringUtils;

/**
 * Represents a federated server registration. Gitblit federation allows one
 * Gitblit instance to pull the repositories and configuration from another
 * Gitblit instance. This is a backup operation and can be considered something
 * like svn-sync.
 * 
 */
public class FederationModel implements Serializable, Comparable<FederationModel> {

	private static final long serialVersionUID = 1L;

	public String name;

	public String url;

	public String token;

	public String frequency;

	public String folder;
	
	public boolean bare;

    public boolean mirror;

	public boolean mergeAccounts;

	public boolean sendStatus;

	public boolean notifyOnError;

	public List<String> exclusions = new ArrayList<String>();

	public List<String> inclusions = new ArrayList<String>();

	public Date lastPull;

	public Date nextPull;

	private Map<String, FederationPullStatus> results = new ConcurrentHashMap<String, FederationPullStatus>();

	/**
	 * The constructor for a remote server configuration.
	 * 
	 * @param serverName
	 */
	public FederationModel(String serverName) {
		this.name = serverName;
		bare = true;
		mirror = true;
		this.lastPull = new Date(0);
		this.nextPull = new Date(0);
	}

	public boolean isIncluded(RepositoryModel repository) {
		// if exclusions has the all wildcard, then check for specific
		// inclusions
		if (exclusions.contains("*")) {
			for (String name : inclusions) {
				if (StringUtils.fuzzyMatch(repository.name, name)) {
					results.put(repository.name, FederationPullStatus.PENDING);
					return true;
				}
			}
			results.put(repository.name, FederationPullStatus.EXCLUDED);
			return false;
		}

		// named exclusions
		for (String name : exclusions) {
			if (StringUtils.fuzzyMatch(repository.name, name)) {
				results.put(repository.name, FederationPullStatus.EXCLUDED);
				return false;
			}
		}

		// included by default
		results.put(repository.name, FederationPullStatus.PENDING);
		return true;
	}

	/**
	 * Updates the pull status of a particular repository in this federation
	 * registration.
	 * 
	 * @param repository
	 * @param status
	 */
	public void updateStatus(RepositoryModel repository, FederationPullStatus status) {
		if (!results.containsKey(repository.name)) {
			results.put(repository.name, FederationPullStatus.PENDING);
		}
		if (status != null) {
			results.put(repository.name, status);
		}
	}

	public List<RepositoryStatus> getStatusList() {
		List<RepositoryStatus> list = new ArrayList<RepositoryStatus>();
		for (Map.Entry<String, FederationPullStatus> entry : results.entrySet()) {
			list.add(new RepositoryStatus(entry.getKey(), entry.getValue()));
		}
		return list;
	}

	/**
	 * Iterates over the current pull results and returns the lowest pull
	 * status.
	 * 
	 * @return the lowest pull status of the registration
	 */
	public FederationPullStatus getLowestStatus() {
		if (results.size() == 0) {
			return FederationPullStatus.PENDING;
		}
		FederationPullStatus status = FederationPullStatus.MIRRORED;
		for (FederationPullStatus result : results.values()) {
			if (result.ordinal() < status.ordinal()) {
				status = result;
			}
		}
		return status;
	}

	/**
	 * Returns true if this registration represents the result data sent by a
	 * pulling Gitblit instance.
	 * 
	 * @return true, if this is result data
	 */
	public boolean isResultData() {
		return !url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://");
	}

	@Override
	public String toString() {
		return "Federated " + name + " (" + url + ")";
	}

	@Override
	public int compareTo(FederationModel o) {
		boolean r1 = isResultData();
		boolean r2 = o.isResultData();
		if ((r1 && r2) || (!r1 && !r2)) {
			// sort registrations and results by name
			return name.compareTo(o.name);
		}
		// sort registrations first
		if (r1) {
			return 1;
		}
		return -1;
	}

	/**
	 * Class that encapsulates a point-in-time pull result.
	 * 
	 */
	public static class RepositoryStatus implements Serializable, Comparable<RepositoryStatus> {

		private static final long serialVersionUID = 1L;

		public final String name;
		public final FederationPullStatus status;

		RepositoryStatus(String name, FederationPullStatus status) {
			this.name = name;
			this.status = status;
		}

		@Override
		public int compareTo(RepositoryStatus o) {
			if (status.equals(o.status)) {
				return StringUtils.compareRepositoryNames(name, o.name);
			}
			return status.compareTo(o.status);
		}
	}
}
