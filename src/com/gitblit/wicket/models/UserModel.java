package com.gitblit.wicket.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

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

	public boolean canAccessRepository(String repositoryName) {
		return canAdmin || repositories.contains(repositoryName);
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
