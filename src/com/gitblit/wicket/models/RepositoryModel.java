package com.gitblit.wicket.models;

import java.io.Serializable;
import java.util.Date;

import com.gitblit.Constants.AccessRestrictionType;

public class RepositoryModel implements Serializable {

	private static final long serialVersionUID = 1L;
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

	public RepositoryModel() {
		this.name = "";
		this.description = "";
		this.owner = "";
		this.lastChange = new Date(0);
		this.accessRestriction = AccessRestrictionType.NONE;
	}

	public RepositoryModel(String name, String description, String owner, Date lastchange) {
		this.name = name;
		this.description = description;
		this.owner = owner;
		this.lastChange = lastchange;
		this.accessRestriction = AccessRestrictionType.NONE;
	}
	
	@Override
	public String toString() {
		return name;
	}
}