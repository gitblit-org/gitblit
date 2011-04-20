package com.gitblit.wicket.models;

import java.io.Serializable;

public class Metric implements Serializable {

	private static final long serialVersionUID = 1L;

	public String name;
	public double count;
	public double tag;
	public int duration;

	public Metric(String name) {
		this.name = name;
	}
}