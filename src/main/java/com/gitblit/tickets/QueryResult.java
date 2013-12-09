/*
 * Copyright 2013 gitblit.com.
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
package com.gitblit.tickets;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.gitblit.models.TicketModel.Patchset;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.models.TicketModel.Type;
import com.gitblit.utils.StringUtils;

/**
 * Represents the results of a query to the ticket index.
 *
 * @author James Moger
 *
 */
public class QueryResult implements Serializable {

	private static final long serialVersionUID = 1L;

	public String repository;
	public long number;
	public String changeId;
	public String createdBy;
	public Date createdAt;
	public String updatedBy;
	public Date updatedAt;
	public String dependsOn;
	public String title;
	public String body;
	public Status state;
	public String assignedTo;
	public String milestone;
	public String topic;
	public Type type;
	public String mergeSha;
	public String mergeTo;
	public List<String> labels;
	public List<String> attachments;
	public List<String> participants;
	public Patchset patchset;
	public int patchsetsCount;
	public int commentsCount;

	public int docId;
	public int totalResults;

	public Date getDate() {
		return updatedAt == null ? createdAt : updatedAt;
	}

	public boolean isPullRequest() {
		return type != null && Type.Proposal == type;
	}

	public boolean isMerged() {
		return Status.Merged == state && !StringUtils.isEmpty(mergeSha);
	}

	public List<String> getLabels() {
		List<String> list = new ArrayList<String>();
		if (labels != null) {
			list.addAll(labels);
		}
		if (topic != null) {
			list.add(topic);
		}
		Collections.sort(list);
		return list;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof QueryResult) {
			return hashCode() == o.hashCode();
		}
		return false;
	}

	@Override
	public int hashCode() {
		return (repository + changeId).hashCode();
	}

	@Override
	public String toString() {
		return repository + "-" + number;
	}
}
