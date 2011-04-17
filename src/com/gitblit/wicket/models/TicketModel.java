package com.gitblit.wicket.models;

import java.io.Serializable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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

	public TicketModel() {
		state = "open";
		comments = new ArrayList<Comment>();
		tags = new ArrayList<String>();
	}

	public TicketModel(String ticketName) throws ParseException {
		state = "";
		name = ticketName;
		comments = new ArrayList<Comment>();
		tags = new ArrayList<String>();

		String[] chunks = name.split("_");
		if (chunks.length == 3) {
			date = new Date(Long.parseLong(chunks[0]) * 1000l);
			title = chunks[1].replace('-', ' ');
		}
	}

	public static class Comment implements Serializable, Comparable<Comment> {

		private static final long serialVersionUID = 1L;

		public String text;
		public String author;
		public Date date;

		public Comment(String text, Date date) {
			this.text = text;
			this.date = date;
		}

		public Comment(String filename, String content) throws ParseException {
			String[] chunks = filename.split("_", -1);
			this.date = new Date(Long.parseLong(chunks[1]) * 1000l);
			this.author = chunks[2];
			this.text = content;
		}

		@Override
		public int compareTo(Comment o) {
			return date.compareTo(o.date);
		}
	}

	@Override
	public int compareTo(TicketModel o) {
		return date.compareTo(o.date);
	}
}
