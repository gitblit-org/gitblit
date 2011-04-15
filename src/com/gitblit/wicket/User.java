package com.gitblit.wicket;

import java.io.Serializable;

import com.gitblit.Constants;
import com.gitblit.utils.StringUtils;

public class User implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private String username;
	private String cookie;
	private boolean canAdmin = false;
	private boolean canClone = false;
	private boolean canPush = false;

	public User(String username, char[] password) {
		this.username = username;
		this.cookie = StringUtils.getSHA1((Constants.NAME + username + new String(password)));
	}

	public void canAdmin(boolean value) {
		canAdmin = value;
	}

	public boolean canAdmin() {
		return canAdmin;
	}

	public void canClone(boolean value) {
		canClone = value;
	}

	public boolean canClone() {
		return canClone;
	}

	public void canPush(boolean value) {
		canPush = value;
	}

	public boolean canPush() {
		return canPush;
	}

	public String getCookie() {
		return cookie;
	}

	public String toString() {
		return username;
	}
}
