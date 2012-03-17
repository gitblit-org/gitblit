package com.gitblit.models;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import com.gitblit.Constants.SearchObjectType;

/**
 * Model class that represents a search result.
 * 
 * @author James Moger
 * 
 */
public class SearchResult implements Serializable {

	private static final long serialVersionUID = 1L;
	
	public int hitId;
	
	public int totalHits;

	public float score;

	public Date date;

	public String author;

	public String committer;

	public String summary;
	
	public String fragment;
	
	public String repository;
	
	public String branch;

	public String commitId;
	
	public String path;
	
	public String issueId;

	public List<String> tags;
	
	public List<String> labels;

	public SearchObjectType type;

	public SearchResult() {
	}
	
	public String getId() {
		switch (type) {
		case blob:
			return path;
		case commit:
			return commitId;
		case issue:
			return issueId;
		}
		return commitId;
	}

	@Override
	public String toString() {
		return  score + " : " + type.name() + " : " + repository + " : " + getId() + " (" + branch + ")";
	}
}