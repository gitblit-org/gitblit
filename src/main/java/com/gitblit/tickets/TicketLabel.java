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
import java.util.List;

import com.gitblit.utils.StringUtils;

/**
 * A ticket label.
 *
 * @author James Moger
 *
 */
public class TicketLabel implements Serializable {

	private static final long serialVersionUID = 1L;

	public final String name;

	public String color;

	public List<QueryResult> tickets;


	public TicketLabel(String name) {
		this.name = name;
		this.color = StringUtils.getColor(name);
	}

	public int getTotalTickets() {
		return tickets == null ? 0 : tickets.size();
	}

	public int getOpenTickets() {
		int cnt = 0;
		if (tickets != null) {
			for (QueryResult ticket : tickets) {
				if (!ticket.status.isClosed()) {
					cnt++;
				}
			}
		}
		return cnt;
	}

	public int getClosedTickets() {
		int cnt = 0;
		if (tickets != null) {
			for (QueryResult ticket : tickets) {
				if (ticket.status.isClosed()) {
					cnt++;
				}
			}
		}
		return cnt;
	}

	@Override
	public String toString() {
		return name;
	}
}
