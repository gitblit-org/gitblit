/*
 * Copyright 2012 gitblit.com.
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
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;

/**
 * The Gitblit Issue model, its component classes, and enums.
 * 
 * @author James Moger
 * 
 */
public class IssueModel implements Serializable, Comparable<IssueModel> {

	private static final long serialVersionUID = 1L;

	public String id;

	public Type type;

	public Status status;

	public Priority priority;

	public Date created;

	public String summary;

	public String description;

	public String reporter;

	public String owner;

	public String milestone;

	public List<Change> changes;

	public IssueModel() {
		// the first applied change set the date appropriately
		created = new Date(0);

		type = Type.Defect;
		status = Status.New;
		priority = Priority.Medium;

		changes = new ArrayList<Change>();
	}

	public String getStatus() {
		String s = status.toString();
		if (!StringUtils.isEmpty(owner))
			s += " (" + owner + ")";
		return s;
	}

	public boolean hasLabel(String label) {
		return getLabels().contains(label);
	}

	public List<String> getLabels() {
		List<String> list = new ArrayList<String>();
		String labels = null;
		for (Change change : changes) {
			if (change.hasField(Field.Labels)) {
				labels = change.getString(Field.Labels);
			}
		}
		if (!StringUtils.isEmpty(labels)) {
			list.addAll(StringUtils.getStringsFromValue(labels, " "));
		}
		return list;
	}

	public Attachment getAttachment(String name) {
		Attachment attachment = null;
		for (Change change : changes) {
			if (change.hasAttachments()) {
				Attachment a = change.getAttachment(name);
				if (a != null) {
					attachment = a;
				}
			}
		}
		return attachment;
	}

	public List<Attachment> getAttachments() {
		List<Attachment> list = new ArrayList<Attachment>();
		for (Change change : changes) {
			if (change.hasAttachments()) {
				list.addAll(change.attachments);
			}
		}
		return list;
	}

	public void applyChange(Change change) {
		if (changes.size() == 0) {
			// first change created the issue
			created = change.created;
		}
		changes.add(change);

		if (change.hasFieldChanges()) {
			for (FieldChange fieldChange : change.fieldChanges) {
				switch (fieldChange.field) {
				case Id:
					id = fieldChange.value.toString();
					break;
				case Type:
					type = IssueModel.Type.fromObject(fieldChange.value);
					break;
				case Status:
					status = IssueModel.Status.fromObject(fieldChange.value);
					break;
				case Priority:
					priority = IssueModel.Priority.fromObject(fieldChange.value);
					break;
				case Summary:
					summary = fieldChange.value.toString();
					break;
				case Description:
					description = fieldChange.value.toString();
					break;
				case Reporter:
					reporter = fieldChange.value.toString();
					break;
				case Owner:
					owner = fieldChange.value.toString();
					break;
				case Milestone:
					milestone = fieldChange.value.toString();
					break;
				}
			}
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("issue ");
		sb.append(id.substring(0, 8));
		sb.append(" (" + summary + ")\n");
		for (Change change : changes) {
			sb.append(change);
			sb.append('\n');
		}
		return sb.toString();
	}

	@Override
	public int compareTo(IssueModel o) {
		return o.created.compareTo(created);
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof IssueModel)
			return id.equals(((IssueModel) o).id);
		return super.equals(o);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	public static class Change implements Serializable, Comparable<Change> {

		private static final long serialVersionUID = 1L;

		public final Date created;

		public final String author;

		public String id;

		public char code;

		public Comment comment;

		public Set<FieldChange> fieldChanges;

		public Set<Attachment> attachments;

		public Change(String author) {
			this.created = new Date((System.currentTimeMillis() / 1000) * 1000);
			this.author = author;
			this.id = StringUtils.getSHA1(created.toString() + author);
		}

		public boolean hasComment() {
			return comment != null && !comment.deleted;
		}

		public void comment(String text) {
			comment = new Comment(text);
			comment.id = StringUtils.getSHA1(created.toString() + author + text);
		}

		public boolean hasAttachments() {
			return !ArrayUtils.isEmpty(attachments);
		}

		public void addAttachment(Attachment attachment) {
			if (attachments == null) {
				attachments = new LinkedHashSet<Attachment>();
			}
			attachments.add(attachment);
		}

		public Attachment getAttachment(String name) {
			for (Attachment attachment : attachments) {
				if (attachment.name.equalsIgnoreCase(name)) {
					return attachment;
				}
			}
			return null;
		}

		public boolean hasField(Field field) {
			return !StringUtils.isEmpty(getString(field));
		}

		public boolean hasFieldChanges() {
			return !ArrayUtils.isEmpty(fieldChanges);
		}

		public Object getField(Field field) {
			if (fieldChanges != null) {
				for (FieldChange fieldChange : fieldChanges) {
					if (fieldChange.field == field) {
						return fieldChange.value;
					}
				}
			}
			return null;
		}

		public void setField(Field field, Object value) {
			FieldChange fieldChange = new FieldChange(field, value);
			if (fieldChanges == null) {
				fieldChanges = new LinkedHashSet<FieldChange>();
			}
			fieldChanges.add(fieldChange);
		}

		public String getString(Field field) {
			Object value = getField(field);
			if (value == null) {
				return null;
			}
			return value.toString();
		}

		@Override
		public int compareTo(Change c) {
			return created.compareTo(c.created);
		}

		@Override
		public int hashCode() {
			return id.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof Change) {
				return id.equals(((Change) o).id);
			}
			return false;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();			
			sb.append(new TimeUtils().timeAgo(created));
			switch (code) {
			case '+':
				sb.append(" created by ");
				break;
			default:
				if (hasComment()) {
					sb.append(" commented on by ");
				} else {
					sb.append(" changed by ");
				}
			}
			sb.append(author).append(" - ");
			if (hasComment()) {
				if (comment.deleted) {
					sb.append("(deleted) ");
				}
				sb.append(comment.text).append(" ");
			}
			if (hasFieldChanges()) {
				switch (code) {
				case '+':
					break;
				default:
					for (FieldChange fieldChange : fieldChanges) {
						sb.append("\n  ");
						sb.append(fieldChange);
					}
					break;
				}
			}
			return sb.toString();
		}
	}

	public static class Comment implements Serializable {

		private static final long serialVersionUID = 1L;

		public String text;

		public String id;

		public boolean deleted;

		Comment(String text) {
			this.text = text;
		}

		@Override
		public String toString() {
			return text;
		}
	}

	public static class FieldChange implements Serializable {

		private static final long serialVersionUID = 1L;

		public final Field field;

		public final Object value;

		FieldChange(Field field, Object value) {
			this.field = field;
			this.value = value;
		}

		@Override
		public int hashCode() {
			return field.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof FieldChange) {
				return field.equals(((FieldChange) o).field);
			}
			return false;
		}

		@Override
		public String toString() {
			return field + ": " + value;
		}
	}

	public static class Attachment implements Serializable {

		private static final long serialVersionUID = 1L;

		public final String name;
		public String id;
		public long size;
		public byte[] content;
		public boolean deleted;

		public Attachment(String name) {
			this.name = name;
		}

		@Override
		public int hashCode() {
			return name.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof Attachment) {
				return name.equalsIgnoreCase(((Attachment) o).name);
			}
			return false;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	public static enum Field {
		Id, Summary, Description, Reporter, Owner, Type, Status, Priority, Milestone, Component, Labels;
	}

	public static enum Type {
		Defect, Enhancement, Task, Review, Other;

		public static Type fromObject(Object o) {
			if (o instanceof Type) {
				// cast and return
				return (Type) o;
			} else if (o instanceof String) {
				// find by name
				for (Type type : values()) {
					String str = o.toString();
					if (type.toString().equalsIgnoreCase(str)) {
						return type;
					}
				}
			} else if (o instanceof Number) {
				// by ordinal
				int id = ((Number) o).intValue();
				if (id >= 0 && id < values().length) {
					return values()[id];
				}
			}
			return null;
		}
	}

	public static enum Priority {
		Low, Medium, High, Critical;

		public static Priority fromObject(Object o) {
			if (o instanceof Priority) {
				// cast and return
				return (Priority) o;
			} else if (o instanceof String) {
				// find by name
				for (Priority priority : values()) {
					String str = o.toString();
					if (priority.toString().equalsIgnoreCase(str)) {
						return priority;
					}
				}
			} else if (o instanceof Number) {
				// by ordinal
				int id = ((Number) o).intValue();
				if (id >= 0 && id < values().length) {
					return values()[id];
				}
			}
			return null;
		}
	}

	public static enum Status {
		New, Accepted, Started, Review, Queued, Testing, Done, Fixed, WontFix, Duplicate, Invalid;

		public static Status fromObject(Object o) {
			if (o instanceof Status) {
				// cast and return
				return (Status) o;
			} else if (o instanceof String) {
				// find by name
				for (Status status : values()) {
					String str = o.toString();
					if (status.toString().equalsIgnoreCase(str)) {
						return status;
					}
				}
			} else if (o instanceof Number) {
				// by ordinal
				int id = ((Number) o).intValue();
				if (id >= 0 && id < values().length) {
					return values()[id];
				}
			}
			return null;
		}

		public boolean atLeast(Status status) {
			return ordinal() >= status.ordinal();
		}

		public boolean exceeds(Status status) {
			return ordinal() > status.ordinal();
		}

		public boolean isClosed() {
			return ordinal() >= Done.ordinal();
		}

		public Status next() {
			switch (this) {
			case New:
				return Started;
			case Accepted:
				return Started;
			case Started:
				return Testing;
			case Review:
				return Testing;
			case Queued:
				return Testing;
			case Testing:
				return Done;
			}
			return Accepted;
		}
	}
}
