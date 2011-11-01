/*
 * Copyright 2011 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.models;

import java.io.Serializable;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.gitblit.utils.StringUtils;

/**
 * FeedModel represents a syndication (RSS) feed.
 * 
 * @author James Moger
 */
public class FeedModel implements Serializable, Comparable<FeedModel> {

	public String repository;
	public String branch;
	public int maxRetrieval;
	public Date lastRefresh;

	public boolean subscribed;

	private static final long serialVersionUID = 1L;

	public FeedModel() {
		this("");
	}

	public FeedModel(String definition) {
		maxRetrieval = -1;
		lastRefresh = new Date(0);

		String[] fields = definition.split(":");
		repository = fields[0];
		if (fields.length > 1) {
			branch = fields[1];
			maxRetrieval = Integer.parseInt(fields[2]);
			try {
				lastRefresh = new SimpleDateFormat("yyyyMMddHHmmss").parse(fields[3]);
			} catch (ParseException e) {
			}
			subscribed = true;
		}
	}

	@Override
	public String toString() {
		return MessageFormat.format("{0}:{1}:{2,number,0}:{3,date,yyyyMMddHHmmss}", repository,
				branch, maxRetrieval, lastRefresh);
	}

	@Override
	public int compareTo(FeedModel o) {
		int repositoryCompare = repository.compareTo(o.repository);
		if (repositoryCompare == 0) {
			// same repository
			if (StringUtils.isEmpty(branch)) {
				return 1;
			} else if (StringUtils.isEmpty(o.branch)) {
				return -1;
			}
			return branch.compareTo(o.branch);
		}
		return repositoryCompare;
	}

	@Override
	public int hashCode() {
		return (repository + (StringUtils.isEmpty(branch) ? "" : branch)).toLowerCase().hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof FeedModel) {
			return hashCode() == o.hashCode();
		}
		return false;
	}
}
