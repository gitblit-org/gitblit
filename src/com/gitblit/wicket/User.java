package com.gitblit.wicket;

import com.gitblit.Build;
import com.gitblit.Constants;

public class User {
	
	private String username;
	private char [] password;
	private boolean canAdmin = false;
	private boolean canClone = false;
	private boolean canPush = false;
	
	public User(String username, char [] password) {
		this.username = username;
		this.password = password;
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
		return Build.getSHA1((Constants.NAME + username + new String(password)).getBytes());
	}
	
	public String toString() {
		return username;
	}
}
