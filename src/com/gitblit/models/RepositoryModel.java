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
import java.util.Set;
import java.util.TreeSet;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.Constants.FederationStrategy;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;

/**
 * RepositoryModel is a serializable model class that represents a Gitblit
 * repository including its configuration settings and access restriction.
 * 
 * @author James Moger
 * 
 */
public class RepositoryModel implements Serializable, Comparable<RepositoryModel> {

	private static final long serialVersionUID = 1L;

	// field names are reflectively mapped in EditRepository page
	public String name;
	public String description;
	public String owner;
	public Date lastChange;
	public boolean hasCommits;
	public boolean showRemoteBranches;
	public boolean useTickets;
	public boolean useDocs;
	public AccessRestrictionType accessRestriction;
	public AuthorizationControl authorizationControl;
	public boolean allowAuthenticated;
	public boolean isFrozen;
	public boolean showReadme;
	public FederationStrategy federationStrategy;
	public List<String> federationSets;
	public boolean isFederated;
	public boolean skipSizeCalculation;
	public boolean skipSummaryMetrics;
	public String frequency;
	public boolean isBare;
	public String origin;
	public String HEAD;
	public List<String> availableRefs;
	public List<String> indexedBranches;
	public String size;
	public List<String> preReceiveScripts;
	public List<String> postReceiveScripts;
	public List<String> mailingLists;
	public Map<String, String> customFields;
	public String projectPath;
	private String displayName;
	public boolean allowForks;
	public Set<String> forks;
	public String originRepository;
	public boolean verifyCommitter;
	
	public RepositoryModel() {
		this("", "", "", new Date(0));
	}

	public RepositoryModel(String name, String description, String owner, Date lastchange) {
		this.name = name;
		this.description = description;
		this.owner = owner;
		this.lastChange = lastchange;
		this.accessRestriction = AccessRestrictionType.NONE;
		this.authorizationControl = AuthorizationControl.NAMED;
		this.federationSets = new ArrayList<String>();
		this.federationStrategy = FederationStrategy.FEDERATE_THIS;	
		this.projectPath = StringUtils.getFirstPathElement(name);
	}
	
	public List<String> getLocalBranches() {
		if (ArrayUtils.isEmpty(availableRefs)) {
			return new ArrayList<String>();
		}
		List<String> localBranches = new ArrayList<String>();
		for (String ref : availableRefs) {
			if (ref.startsWith("refs/heads")) {
				localBranches.add(ref);
			}
		}
		return localBranches;
	}
	
	public void addFork(String repository) {
		if (forks == null) {
			forks = new TreeSet<String>();
		}
		forks.add(repository);
	}
	
	public void removeFork(String repository) {
		if (forks == null) {
			return;
		}
		forks.remove(repository);
	}
	
	public void resetDisplayName() {
		displayName = null;
	}
	
	@Override
	public int hashCode() {
		return name.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof RepositoryModel) {
			return name.equals(((RepositoryModel) o).name);
		}
		return false;
	}

	@Override
	public String toString() {
		if (displayName == null) {
			displayName = StringUtils.stripDotGit(name);
		}
		return displayName;
	}

	@Override
	public int compareTo(RepositoryModel o) {
		return StringUtils.compareRepositoryNames(name, o.name);
	}
	
	public boolean isFork() {
		return !StringUtils.isEmpty(originRepository);
	}
	
	public boolean isOwner(String username) {
		return owner != null && username != null && owner.equalsIgnoreCase(username);
	}
	
	public boolean isPersonalRepository() {
		return !StringUtils.isEmpty(projectPath) && projectPath.charAt(0) == '~';
	}
	
	public boolean isUsersPersonalRepository(String username) {
		return !StringUtils.isEmpty(projectPath) && projectPath.equalsIgnoreCase("~" + username);
	}
	
	public boolean allowAnonymousView() {
		return !accessRestriction.atLeast(AccessRestrictionType.VIEW);
	}
	
	public RepositoryModel cloneAs(String cloneName) {
		RepositoryModel clone = new RepositoryModel();
		clone.originRepository = name;
		clone.name = cloneName;
		clone.projectPath = StringUtils.getFirstPathElement(cloneName);
		clone.isBare = true;
		clone.description = description;
		clone.accessRestriction = AccessRestrictionType.PUSH;
		clone.authorizationControl = AuthorizationControl.NAMED;
		clone.federationStrategy = federationStrategy;
		clone.showReadme = showReadme;
		clone.showRemoteBranches = false;
		clone.allowForks = false;
		clone.useDocs = useDocs;
		clone.useTickets = useTickets;
		clone.skipSizeCalculation = skipSizeCalculation;
		clone.skipSummaryMetrics = skipSummaryMetrics;
		return clone;
	}
}