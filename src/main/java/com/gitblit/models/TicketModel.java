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
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * TicketModel is a serializable model class that represents a Ticgit ticket.
 * 
 * @author James Moger
 * 
 */
public class TicketModel implements Serializable, Comparable<TicketModel> {

	private static final long serialVersionUID = 1L;

	public String id;
	public String name;
	public String title;
	public String state;
	public Date date;
	public String handler;
	public String milestone;
	public String email;
	public String author;
	public List<Comment> comments;
	public List<String> tags;

	public TicketModel(String ticketName) throws ParseException {
		state = "";
		name = ticketName;
		comments = new ArrayList<Comment>();
		tags = new ArrayList<String>();

		String[] chunks = name.split("_");
		if (chunks.length == 3) {
			date = new Date(Long.parseLong(chunks[0]) * 1000L);
			title = chunks[1].replace('-', ' ');
		}
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof TicketModel) {
			TicketModel other = (TicketModel) o;
			return id.equals(other.id);
		}
		return super.equals(o);
	}

	@Override
	public int compareTo(TicketModel o) {
		return date.compareTo(o.date);
	}

	/**
	 * Comment is a serializable model class that represents a Ticgit ticket
	 * comment.
	 * 
	 * @author James Moger
	 * 
	 */
	public static class Comment implements Serializable, Comparable<Comment> {

		private static final long serialVersionUID = 1L;

		public String text;
		public String author;
		public Date date;

		public Comment(String filename, String content) throws ParseException {
			String[] chunks = filename.split("_", -1);
			this.date = new Date(Long.parseLong(chunks[1]) * 1000L);
			this.author = chunks[2];
			this.text = content;
		}

		@Override
		public int hashCode() {
			return text.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof Comment) {
				Comment other = (Comment) o;
				return text.equals(other.text);
			}
			return super.equals(o);
		}

		@Override
		public int compareTo(Comment o) {
			return date.compareTo(o.date);
		}
	}
}
