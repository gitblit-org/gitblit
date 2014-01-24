/*
 * Copyright 2014 gitblit.com.
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

import org.parboiled.common.StringUtils;

import com.gitblit.models.UserModel;

/**
 * A ticket responsible.
 *
 * @author James Moger
 *
 */
public class TicketResponsible implements Serializable, Comparable<TicketResponsible> {

	private static final long serialVersionUID = 1L;

	public final String displayname;

	public final String username;

	public final String email;

	public TicketResponsible(UserModel user) {
		this(user.getDisplayName(), user.username, user.emailAddress);
	}

	public TicketResponsible(String displayname, String username, String email) {
		this.displayname = displayname;
		this.username = username;
		this.email = email;
	}

	@Override
	public String toString() {
		return displayname + (StringUtils.isEmpty(username) ? "" : (" (" + username + ")"));
	}

	@Override
	public int compareTo(TicketResponsible o) {
		return toString().compareTo(o.toString());
	}
}
