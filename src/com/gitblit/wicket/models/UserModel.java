package com.gitblit.wicket.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.gitblit.Constants.AccessRestrictionType;

public class UserModel implements Serializable {

	private static final long serialVersionUID = 1L;

	private String username;
	private String password;
	private String cookie;
	private boolean canAdmin = false;
	private List<String> repositories = new ArrayList<String>();

	public UserModel(String username) {
		this.username = username;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public void canAdmin(boolean value) {
		canAdmin = value;
	}

	public boolean canAdmin() {
		return canAdmin;
	}
	
	public boolean canClone(RepositoryModel repository) {
		return canAccess(repository, AccessRestrictionType.CLONE);
	}

	public boolean canPush(RepositoryModel repository) {
		return canAccess(repository, AccessRestrictionType.PUSH);
	}
	
	public boolean canView(RepositoryModel repository) {
		return canAccess(repository, AccessRestrictionType.VIEW);
	}
	
	private boolean canAccess(RepositoryModel repository, AccessRestrictionType minimum) {
		if (repository.accessRestriction.atLeast(minimum)) {
			// repository is restricted, must check roles
			return canAdmin || repositories.contains(repository.name);
		} else {
			// repository is not restricted
			return true;
		}
	}

	public void setCookie(String cookie) {
		this.cookie = cookie;
	}

	public String getCookie() {
		return cookie;
	}

	public void setRepositories(List<String> repositories) {
		this.repositories.clear();
		this.repositories.addAll(repositories);
	}

	public void addRepository(String name) {
		repositories.add(name.toLowerCase());
	}

	public List<String> getRepositories() {
		return repositories;
	}

	public String toString() {
		return username;
	}
}
