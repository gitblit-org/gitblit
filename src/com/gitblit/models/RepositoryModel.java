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

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.FederationStrategy;
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
	public boolean isFrozen;
	public boolean showReadme;
	public FederationStrategy federationStrategy;
	public List<String> federationSets;
	public boolean isFederated;
	public boolean skipSizeCalculation;
	public String frequency;
	public String origin;
	public String size;

	public RepositoryModel() {
		this("", "", "", new Date(0));
	}

	public RepositoryModel(String name, String description, String owner, Date lastchange) {
		this.name = name;
		this.description = description;
		this.owner = owner;
		this.lastChange = lastchange;
		this.accessRestriction = AccessRestrictionType.NONE;
		this.federationSets = new ArrayList<String>();
		this.federationStrategy = FederationStrategy.FEDERATE_THIS;
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public int compareTo(RepositoryModel o) {
		return StringUtils.compareRepositoryNames(name, o.name);
	}
}