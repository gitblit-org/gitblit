package com.gitblit.models;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import com.gitblit.utils.LuceneUtils.ObjectType;

/**
 * Model class that represents a search result.
 * 
 * @author James Moger
 * 
 */
public class SearchResult implements Serializable {

	private static final long serialVersionUID = 1L;

	public float score;

	public Date date;

	public String author;

	public String committer;

	public String summary;
	
	public String repository;

	public String id;

	public List<String> labels;

	public ObjectType type;

	public SearchResult() {
	}

	@Override
	public String toString() {
		return  score + " : " + type.name() + " : " + repository + " : " + id;
	}
}