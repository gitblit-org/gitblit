package com.gitblit.wicket.models;

import java.io.Serializable;
import java.util.Date;

public class RepositoryModel implements Serializable {

	private static final long serialVersionUID = 1L;
	public final String name;
	public final String description;
	public final String owner;
	public final Date lastChange;

	public RepositoryModel(String name, String description, String owner, Date lastchange) {
		this.name = name;
		this.description = description;
		this.owner = owner;
		this.lastChange = lastchange;
	}
}