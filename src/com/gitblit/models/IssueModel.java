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
import java.util.List;

import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;

/**
 * The Gitblit Issue model, its component classes, and enums.
 * 
 * @author James Moger
 * 
 */
public class IssueModel implements Serializable, Comparable<IssueModel> {

	private static final long serialVersionUID = 1L;;

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
		created = new Date();

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

	public List<String> getLabels() {
		List<String> list = new ArrayList<String>();
		String labels = null;
		for (Change change : changes) {
			if (change.hasFieldChanges()) {
				FieldChange field = change.getField(Field.Labels);
				if (field != null) {
					labels = field.value.toString();
				}
			}
		}
		if (!StringUtils.isEmpty(labels)) {
			list.addAll(StringUtils.getStringsFromValue(labels, " "));
		}
		return list;
	}

	public boolean hasLabel(String label) {
		return getLabels().contains(label);
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

	public void addChange(Change change) {
		if (changes == null) {
			changes = new ArrayList<Change>();
		}
		changes.add(change);
	}

	@Override
	public String toString() {
		return summary;
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

	public static class Change implements Serializable {

		private static final long serialVersionUID = 1L;

		public Date created;

		public String author;

		public Comment comment;

		public List<FieldChange> fieldChanges;

		public List<Attachment> attachments;

		public void comment(String text) {
			comment = new Comment(text);
		}

		public boolean hasComment() {
			return comment != null;
		}

		public boolean hasAttachments() {
			return !ArrayUtils.isEmpty(attachments);
		}

		public boolean hasFieldChanges() {
			return !ArrayUtils.isEmpty(fieldChanges);
		}

		public FieldChange getField(Field field) {
			if (fieldChanges != null) {
				for (FieldChange fieldChange : fieldChanges) {
					if (fieldChange.field == field) {
						return fieldChange;
					}
				}
			}
			return null;
		}

		public void setField(Field field, Object value) {
			FieldChange fieldChange = new FieldChange();
			fieldChange.field = field;
			fieldChange.value = value;
			if (fieldChanges == null) {
				fieldChanges = new ArrayList<FieldChange>();
			}
			fieldChanges.add(fieldChange);
		}

		public String getString(Field field) {
			FieldChange fieldChange = getField(field);
			if (fieldChange == null) {
				return null;
			}
			return fieldChange.value.toString();
		}

		public void addAttachment(Attachment attachment) {
			if (attachments == null) {
				attachments = new ArrayList<Attachment>();
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

		@Override
		public String toString() {
			return created.toString() + " by " + author;
		}
	}

	public static class Comment implements Serializable {

		private static final long serialVersionUID = 1L;

		public String text;
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

		public Field field;

		public Object value;

		@Override
		public String toString() {
			return field + ": " + value;
		}
	}

	public static class Attachment implements Serializable {

		private static final long serialVersionUID = 1L;

		public String name;
		public long size;
		public byte[] content;
		public boolean deleted;

		public Attachment(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	public static enum Field {
		Summary, Description, Reporter, Owner, Type, Status, Priority, Milestone, Labels;
	}

	public static enum Type {
		Defect, Enhancement, Task, Review, Other;
	}

	public static enum Priority {
		Low, Medium, High, Critical;
	}

	public static enum Status {
		New, Accepted, Started, Review, Queued, Testing, Done, Fixed, WontFix, Duplicate, Invalid;

		public boolean atLeast(Status status) {
			return ordinal() >= status.ordinal();
		}

		public boolean exceeds(Status status) {
			return ordinal() > status.ordinal();
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
