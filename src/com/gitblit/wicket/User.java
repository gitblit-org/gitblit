package com.gitblit.wicket;

import com.gitblit.Build;
import com.gitblit.Constants;

public class User {
	
	private String username;
	private char [] password;
	
	public User(String username, char [] password) {
		this.username = username;
		this.password = password;
	}
	
	public String getCookie() {
		return Build.getSHA1((Constants.NAME + username + new String(password)).getBytes());
	}
	
	public String toString() {
		return username;
	}
}
