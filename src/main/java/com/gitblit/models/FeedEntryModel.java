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
import java.util.Date;
import java.util.List;

/**
 * FeedEntryModel represents an entry in a syndication (RSS) feed.
 *
 * @author James Moger
 */
public class FeedEntryModel implements Serializable, Comparable<FeedEntryModel> {

	public String repository;
	public String branch;
	public String title;
	public String author;
	public Date published;
	public String link;
	public String content;
	public String contentType;
	public List<String> tags;

	private static final long serialVersionUID = 1L;

	public FeedEntryModel() {
	}

	@Override
	public int compareTo(FeedEntryModel o) {
		return o.published.compareTo(published);
	}

	@Override
	public int hashCode() {
		return link.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof FeedEntryModel) {
			return hashCode() == o.hashCode();
		}
		return false;
	}
}
