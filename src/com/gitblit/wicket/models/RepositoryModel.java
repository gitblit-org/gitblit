package com.gitblit.wicket.models;

import java.io.Serializable;
import java.util.Date;

public class RepositoryModel implements Serializable {

	private static final long serialVersionUID = 1L;
	public String name;
	public String description;
	public String owner;
	public String group;
	public Date lastChange;
	public boolean useTickets;
	public boolean useDocs;
	public boolean useRestrictedAccess;

	public RepositoryModel(String name, String description, String owner, Date lastchange) {
		this.name = name;
		this.description = description;
		this.owner = owner;
		this.lastChange = lastchange;
	}
}