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
import com.gitblit.models.TicketModel.Priority;
import com.gitblit.models.TicketModel.Severity;
import com.gitblit.utils.StringUtils;

/**
 * Represents the results of a query to the ticket index.
 *
 * @author James Moger
 *
 */
public class QueryResult implements Serializable {

	private static final long serialVersionUID = 1L;

	public String project;
	public String repository;
	public long number;
	public String createdBy;
	public Date createdAt;
	public String updatedBy;
	public Date updatedAt;
	public String dependsOn;
	public String title;
	public String body;
	public Status status;
	public String responsible;
	public String milestone;
	public String topic;
	public Type type;
	public String mergeSha;
	public String mergeTo;
	public List<String> labels;
	public List<String> attachments;
	public List<String> participants;
	public List<String> watchedby;
	public List<String> mentions;
	public List<String> dependencies;
	public Patchset patchset;
	public int commentsCount;
	public int votesCount;
	public int approvalsCount;
	public Priority priority;
	public Severity severity;

	public int docId;
	public int totalResults;

	public Date getDate() {
		return updatedAt == null ? createdAt : updatedAt;
	}

	public boolean isProposal() {
		return type != null && Type.Proposal == type;
	}

	public boolean isOpen() {
		return !status.isClosed();
	}

	public boolean isClosed() {
		return status.isClosed();
	}

	public boolean isMerged() {
		return Status.Merged == status && !StringUtils.isEmpty(mergeSha);
	}

	public boolean isWatching(String username) {
		return watchedby != null && watchedby.contains(username);
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
		return (repository + number).hashCode();
	}

	@Override
	public String toString() {
		return repository + "-" + number;
	}
}
