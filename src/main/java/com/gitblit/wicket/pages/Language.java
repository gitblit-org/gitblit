package com.gitblit.wicket.pages;

import java.io.Serializable;

public class Language implements Serializable {

	private static final long serialVersionUID = 1L;

	final String name;
	final String code;

	public Language(String name, String code) {
		this.name = name;
		this.code = code;
	}

	@Override
	public String toString() {
		return name + " (" + code + ")";
	}
}