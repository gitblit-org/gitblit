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

import java.util.Date;

import com.gitblit.models.TicketModel.Status;

/**
 * A ticket milestone.
 *
 * @author James Moger
 *
 */
public class TicketMilestone extends TicketLabel {

	private static final long serialVersionUID = 1L;

	public Status status;

	public Date due;

	public TicketMilestone(String name) {
		super(name);
		status = Status.Open;
	}

	public int getProgress() {
		int total = getTotalTickets();
		if (total == 0) {
			return 0;
		}
		return (int) (((getClosedTickets() * 1f) / (total * 1f)) * 100);
	}

	@Override
	public String toString() {
		return name;
	}
}
