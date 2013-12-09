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
package com.gitblit.models;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.util.RelativeDateFormatter;

/**
 * The Gitblit Ticket model, its component classes, and enums.
 *
 * @author James Moger
 *
 */
public class TicketModel implements Serializable, Comparable<TicketModel> {

	public static final String R_CHANGES = "refs/changes/";

	private static final long serialVersionUID = 1L;

	public String repository;

	public long number;

	public String changeId;

	public Date createdAt;

	public String createdBy;

	public Date updatedAt;

	public String updatedBy;

	public String title;

	public String body;

	public String topic;

	public Type type;

	public Status status;

	public String assignedTo;

	public String milestone;

	public String mergeSha;

	public String mergeTo;

	public List<Change> changes;

	/**
	 * Builds an effective ticket from the collection of changes.  A change may
	 * Add or Subtract information from a ticket, but the collection of changes
	 * is only additive.
	 *
	 * @param changes
	 * @return the effective ticket
	 */
	public static TicketModel buildTicket(Collection<Change> changes) {
		TicketModel ticket;
		List<Change> effectiveChanges = new ArrayList<Change>();
		Map<String, Change> comments = new HashMap<String, Change>();
		for (Change change : changes) {
			if (change.comment != null) {
				if (comments.containsKey(change.comment.id)) {
					Change original = comments.get(change.comment.id);
					Change clone = copy(original);
					clone.comment.text = change.comment.text;
					clone.comment.deleted = change.comment.deleted;
					int idx = effectiveChanges.indexOf(original);
					effectiveChanges.remove(original);
					effectiveChanges.add(idx, clone);
					comments.put(clone.comment.id, clone);
				} else {
					effectiveChanges.add(change);
					comments.put(change.comment.id, change);
				}
			} else {
				effectiveChanges.add(change);
			}
		}

		// effective ticket
		ticket = new TicketModel();
		for (Change change : effectiveChanges) {
			if (!change.hasComment()) {
				// ensure we do not include a deleted comment
				change.comment = null;
			}
			ticket.applyChange(change);
		}
		return ticket;
	}

	public TicketModel() {
		// the first applied change set the date appropriately
		createdAt = new Date(0);
		changes = new ArrayList<Change>();
	}

	public boolean isOpen() {
		return !status.isClosed();
	}

	public boolean isClosed() {
		return status.isClosed();
	}

	public boolean isMerged() {
		return isClosed() && !isEmpty(mergeSha);
	}

	public boolean isPullRequest() {
		return type != null && Type.Proposal == type;
	}

	public boolean isBug() {
		return Type.Bug == type;
	}

	public Date getLastUpdated() {
		return updatedAt == null ? createdAt : updatedAt;
	}

	public boolean hasPatches() {
		return getPatchsets().size() > 0;
	}

	/**
	 * Returns true if multiple participants are involved in discussing a ticket.
	 * The ticket creator is excluded from this determination because a
	 * discussion requires more than one participant.
	 *
	 * @return true if this ticket has a discussion
	 */
	public boolean hasDiscussion() {
		for (Change change : getComments()) {
			if (!change.createdBy.equals(createdBy)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns the list of changes with comments.
	 *
	 * @return
	 */
	public List<Change> getComments() {
		List<Change> list = new ArrayList<Change>();
		for (Change change : changes) {
			if (change.hasComment()) {
				list.add(change);
			}
		}
		return list;
	}

	/**
	 * Returns the list of participants for the ticket.
	 *
	 * @return the list of participants
	 */
	public List<String> getParticipants() {
		Set<String> set = new LinkedHashSet<String>();
		for (Change change : changes) {
			set.add(change.createdBy);
		}
		if (assignedTo != null && assignedTo.length() > 0) {
			set.add(assignedTo);
		}
		return new ArrayList<String>(set);
	}

	public boolean hasLabel(String label) {
		return getLabels().contains(label);
	}

	public List<String> getLabels() {
		List<String> list = new ArrayList<String>();
		String labels = null;
		for (Change change : changes) {
			if (change.hasField(Field.labels)) {
				labels = change.getString(Field.labels);
			}
		}
		if (!isEmpty(labels)) {
			list.addAll(Arrays.asList(labels.split(" ")));
		}
		return list;
	}

	public boolean isAssignedTo(String username) {
		return username.equals(assignedTo);
	}

	public boolean isAuthor(String username) {
		return username.equals(createdBy);
	}

	public boolean isReviewer(String username) {
		return getReviewers().contains(username);
	}

	public List<String> getReviewers() {
		List<String> list = new ArrayList<String>();
		String reviewers = null;
		for (Change change : changes) {
			if (change.hasField(Field.reviewers)) {
				reviewers = change.getString(Field.reviewers);
			}
		}
		if (!isEmpty(reviewers)) {
			list.addAll(Arrays.asList(reviewers.split(" ")));
		}
		return list;
	}

	public boolean isWatching(String username) {
		return getWatchers().contains(username);
	}

	public List<String> getWatchers() {
		List<String> list = new ArrayList<String>();
		String watchers = null;
		for (Change change : changes) {
			if (change.hasField(Field.watchers)) {
				watchers = change.getString(Field.watchers);
			}
		}
		if (!isEmpty(watchers)) {
			list.addAll(Arrays.asList(watchers.split(" ")));
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

	public boolean hasAttachments() {
		for (Change change : changes) {
			if (change.hasAttachments()) {
				return true;
			}
		}
		return false;
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

	public List<Patchset> getPatchsets() {
		List<Patchset> list = new ArrayList<Patchset>();
		for (Change change : changes) {
			if (change.patch != null) {
				list.add(change.patch);
			}
		}
		return list;
	}

	public Patchset getPatchset(String sha) {
		for (Change change : changes) {
			if (change.patch != null) {
				if (sha.equals(change.patch.tip)) {
					return change.patch;
				}
			}
		}
		return null;
	}

	public Patchset getCurrentPatchset() {
		Patchset patchset = null;
		for (Change change : changes) {
			if (change.patch != null) {
				if (patchset == null || change.patch.rev > patchset.rev) {
					patchset = change.patch;
				}
			}
		}
		return patchset;
	}

	public boolean isCurrent(Patchset patch) {
		if (patch == null) {
			return false;
		}
		int rev = patch.rev;
		int latestRev = 0;
		for (Change change : changes) {
			if (change.hasPatchset()) {
				int r = change.patch.rev;
				if (latestRev < r) {
					latestRev = r;
				}
			}
		}
		return rev == latestRev;
	}


	public boolean isApproved(Patchset patch) {
		if (patch == null) {
			return false;
		}
		int rev = patch.rev;
		for (Change change : changes) {
			if (change.hasReview()) {
				if (rev == change.review.revision) {
					if (change.review.score > 1) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public boolean isVetoed(Patchset patch) {
		if (patch == null) {
			return false;
		}
		int rev = patch.rev;
		for (Change change : changes) {
			if (change.hasReview()) {
				if (rev == change.review.revision) {
					if (change.review.score < -1) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public boolean isPatchsetAuthor(String username) {
		for (Change change : changes) {
			if (change.hasPatchset()) {
				if (change.createdBy.equals(username)) {
					return true;
				}
			}
		}
		return false;
	}

	public void applyChange(Change change) {
		if (changes.size() == 0) {
			// first change created the ticket
			createdAt = change.createdAt;
			createdBy = change.createdBy;
			status = Status.New;
		} else if (createdAt == null || change.createdAt.after(createdAt)) {
			// track last ticket update
			updatedAt = change.createdAt;
			updatedBy = change.createdBy;
		}

		if (change.isMerge()) {
			// identify merge patches
			if (isEmpty(assignedTo)) {
				assignedTo = change.createdBy;
			}
			status = Status.Merged;
		}

		if (change.hasFieldChanges()) {
			for (FieldChange fieldChange : change.fields) {
				switch (fieldChange.field) {
				case repository:
					repository = toString(fieldChange.value);
					break;
				case number:
					number = Double.valueOf(fieldChange.value.toString()).longValue();
					break;
				case changeId:
					changeId = toString(fieldChange.value);
					break;
				case type:
					type = TicketModel.Type.fromObject(fieldChange.value);
					break;
				case status:
					status = TicketModel.Status.fromObject(fieldChange.value);
					break;
				case title:
					title = toString(fieldChange.value);
					break;
				case body:
					body = toString(fieldChange.value);
					break;
				case createdBy:
					createdBy = toString(fieldChange.value);
					break;
				case topic:
					topic = toString(fieldChange.value);
					break;
				case assignedTo:
					assignedTo = toString(fieldChange.value);
					break;
				case milestone:
					milestone = toString(fieldChange.value);
					break;
				case mergeTo:
					mergeTo = toString(fieldChange.value);
					break;
				case mergeSha:
					mergeSha = toString(fieldChange.value);
					break;
				case labels:
					break;
				default:
					// unknown
					break;
				}
			}
		}

		// add the change to the ticket
		changes.add(change);
	}

	protected String toString(Object value) {
		if (value == null) {
			return null;
		}
		return value.toString();
	}

	public String toIndexableString() {
		StringBuilder sb = new StringBuilder();
		if (!isEmpty(title)) {
			sb.append(title).append('\n');
		}
		if (!isEmpty(body)) {
			sb.append(body).append('\n');
		}
		for (Change change : changes) {
			if (change.hasComment()) {
				sb.append(change.comment.text);
				sb.append('\n');
			}
		}
		return sb.toString();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("ticket ");
		sb.append(number);
		sb.append(" ");
		sb.append(changeId);
		sb.append(" (" + title + ")\n");
		for (Change change : changes) {
			sb.append(change);
			sb.append('\n');
		}
		return sb.toString();
	}

	@Override
	public int compareTo(TicketModel o) {
		return o.createdAt.compareTo(createdAt);
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof TicketModel) {
			return changeId.equals(((TicketModel) o).changeId);
		}
		return super.equals(o);
	}

	@Override
	public int hashCode() {
		return changeId.hashCode();
	}

	/**
	 * Encapsulates a ticket change
	 */
	public static class Change implements Serializable, Comparable<Change> {

		private static final long serialVersionUID = 1L;

		public final String id;

		public final Date createdAt;

		public final String createdBy;

		public Comment comment;

		public Set<FieldChange> fields;

		public Set<Attachment> attachments;

		public Patchset patch;

		public Review review;

		public Change(String createdBy) {
			this(createdBy, new Date());
		}

		public Change(String createdBy, Date created) {
			this.createdAt = created;
			this.createdBy = createdBy;
			this.id = TicketModel.getSHA1(created.toString() + createdBy);
		}

		public boolean isStatusChange() {
			return hasField(Field.status);
		}

		public Status getStatus() {
			Status state = Status.fromObject(getField(Field.status));
			return state;
		}

		public boolean isMerge() {
			return hasField(Field.status) && hasField(Field.mergeSha);
		}

		public boolean hasPatchset() {
			return patch != null;
		}

		public boolean hasReview() {
			return review != null;
		}

		public boolean hasComment() {
			return comment != null && !comment.isDeleted();
		}

		public Comment comment(String text) {
			comment = new Comment(text);
			comment.id = TicketModel.getSHA1(createdAt.toString() + createdBy + text);
			return comment;
		}

		public boolean hasAttachments() {
			return !TicketModel.isEmpty(attachments);
		}

		public void addAttachment(Attachment attachment) {
			if (attachments == null) {
				attachments = new LinkedHashSet<Attachment>();
			}
			attachments.add(attachment);
		}

		public Attachment getAttachment(String name) {
			if (attachments != null) {
				for (Attachment attachment : attachments) {
					if (attachment.name.equalsIgnoreCase(name)) {
						return attachment;
					}
				}
			}
			return null;
		}

		public boolean hasField(Field field) {
			return !TicketModel.isEmpty(getString(field));
		}

		public boolean hasFieldChanges() {
			return !TicketModel.isEmpty(fields);
		}

		public Object getField(Field field) {
			if (fields != null) {
				for (FieldChange fieldChange : fields) {
					if (fieldChange.field == field) {
						return fieldChange.value;
					}
				}
			}
			return null;
		}

		public void setField(Field field, Object value) {
			FieldChange fieldChange = new FieldChange(field, value);
			if (fields == null) {
				fields = new LinkedHashSet<FieldChange>();
			}
			fields.add(fieldChange);
		}

		public void remove(Field field) {
			if (fields != null) {
				FieldChange change = null;
				for (FieldChange fc : fields) {
					if (fc.field.equals(field)) {
						change = fc;
						break;
					}
				}
				if (change != null) {
					fields.remove(change);
				}
			}
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
			return createdAt.compareTo(c.createdAt);
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
			sb.append(RelativeDateFormatter.format(createdAt));
			if (hasField(Field.number)) {
				sb.append(" created by ");
			} else {
				if (hasComment()) {
					sb.append(" commented on by ");
				} else if (hasPatchset()) {
					sb.append(MessageFormat.format(" patch revision {0,number,0} uploaded by ",
							patch.rev));
				} else {
					sb.append(" changed by ");
				}
			}
			sb.append(createdBy).append(" - ");
			if (hasComment()) {
				if (comment.isDeleted()) {
					sb.append("(deleted) ");
				}
				sb.append(comment.text).append(" ");
			}

			if (hasFieldChanges()) {
				for (FieldChange fieldChange : fields) {
					sb.append("\n  ");
					sb.append(fieldChange);
				}
			}
			return sb.toString();
		}
	}

	/**
	 * Returns true if the string is null or empty.
	 *
	 * @param value
	 * @return true if string is null or empty
	 */
	static boolean isEmpty(String value) {
		return value == null || value.trim().length() == 0;
	}

	/**
	 * Returns true if the collection is null or empty
	 *
	 * @param collection
	 * @return
	 */
	static boolean isEmpty(Collection<?> collection) {
		return collection == null || collection.size() == 0;
	}

	/**
	 * Calculates the SHA1 of the string.
	 *
	 * @param text
	 * @return sha1 of the string
	 */
	static String getSHA1(String text) {
		try {
			byte[] bytes = text.getBytes("iso-8859-1");
			return getSHA1(bytes);
		} catch (UnsupportedEncodingException u) {
			throw new RuntimeException(u);
		}
	}

	/**
	 * Calculates the SHA1 of the byte array.
	 *
	 * @param bytes
	 * @return sha1 of the byte array
	 */
	static String getSHA1(byte[] bytes) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			md.update(bytes, 0, bytes.length);
			byte[] digest = md.digest();
			return toHex(digest);
		} catch (NoSuchAlgorithmException t) {
			throw new RuntimeException(t);
		}
	}

	/**
	 * Returns the hex representation of the byte array.
	 *
	 * @param bytes
	 * @return byte array as hex string
	 */
	static String toHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (int i = 0; i < bytes.length; i++) {
			if ((bytes[i] & 0xff) < 0x10) {
				sb.append('0');
			}
			sb.append(Long.toString(bytes[i] & 0xff, 16));
		}
		return sb.toString();
	}

	/**
	 * Produce a deep copy of the given object. Serializes the entire object to
	 * a byte array in memory. Recommended for relatively small objects.
	 */
	@SuppressWarnings("unchecked")
	static <T> T copy(T original) {
		T o = null;
		try {
			ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(byteOut);
			oos.writeObject(original);
			ByteArrayInputStream byteIn = new ByteArrayInputStream(byteOut.toByteArray());
			ObjectInputStream ois = new ObjectInputStream(byteIn);
			try {
				o = (T) ois.readObject();
			} catch (ClassNotFoundException cex) {
				// actually can not happen in this instance
			}
		} catch (IOException iox) {
			// doesn't seem likely to happen as these streams are in memory
			throw new RuntimeException(iox);
		}
		return o;
	}

	public static class Patchset implements Serializable {

		private static final long serialVersionUID = 1L;

		public int rev;
		public String tip;
		public String base;
		public int insertions;
		public int deletions;
		public int totalCommits;
		public int addedCommits;
		public PatchsetType type;
		public String ref;

		@Override
		public String toString() {
			return "r" + rev;
		}
	}

	public static class Comment implements Serializable {

		private static final long serialVersionUID = 1L;

		public String text;

		public String id;

		public Boolean deleted;

		public CommentSource src;

		public String replyTo;

		Comment(String text) {
			this.text = text;
		}

		public boolean isDeleted() {
			return deleted != null && deleted;
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

		public FieldChange(Field field, Object value) {
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
		public long size;
		public byte[] content;
		public Boolean deleted;

		public Attachment(String name) {
			this.name = name;
		}

		public boolean isDeleted() {
			return deleted != null && deleted;
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

	public static class Review implements Serializable {

		private static final long serialVersionUID = 1L;

		public final int revision;

		public int score;

		public Review(int revision) {
			this.revision = revision;
		}
	}

	public static enum Field {
		repository, number, changeId, title, body, createdBy, assignedTo, type,
		status, milestone, mergeSha, mergeTo, labels, topic, watchers, reviewers;
	}

	public static enum Type {
		Request, Task, Bug, Proposal;

		public static Type [] choices() {
			return new Type [] { Request, Task, Bug };
		}

		@Override
		public String toString() {
			return name().toLowerCase().replace('_', ' ');
		}

		public static Type fromObject(Object o) {
			if (o instanceof Type) {
				// cast and return
				return (Type) o;
			} else if (o instanceof String) {
				// find by name
				for (Type type : values()) {
					String str = o.toString();
					if (type.name().equalsIgnoreCase(str)
							|| type.toString().equalsIgnoreCase(str)) {
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

			// default to no specified type
			return null;
		}
	}

	public static enum Status {
		New, Open, On_Hold, Resolved, Fixed, Merged, Wontfix, Duplicate, Invalid, Declined;

		public static Status [] ticketWorkflow = { Open, On_Hold, Resolved, Fixed, Wontfix, Duplicate, Invalid };

		public static Status [] patchsetWorkflow = { On_Hold, Merged, Declined };

		@Override
		public String toString() {
			return name().toLowerCase().replace('_', ' ');
		}

		public static Status fromObject(Object o) {
			if (o instanceof Status) {
				// cast and return
				return (Status) o;
			} else if (o instanceof String) {
				// find by name
				String name = o.toString();
				for (Status state : values()) {
					if (state.name().equalsIgnoreCase(name)
							|| state.toString().equalsIgnoreCase(name)) {
						return state;
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

		public boolean isClosed() {
			return ordinal() > Open.ordinal();
		}
	}

	public static enum CommentSource {
		Comment, Email
	}

	public static enum PatchsetType {
		Proposal, FastForward, Rebase, Squash, Rebase_Squash, Amend;

		@Override
		public String toString() {
			return name().toLowerCase().replace('_', '+');
		}

		public static PatchsetType fromObject(Object o) {
			if (o instanceof PatchsetType) {
				// cast and return
				return (PatchsetType) o;
			} else if (o instanceof String) {
				// find by name
				String name = o.toString();
				for (PatchsetType type : values()) {
					if (type.name().equalsIgnoreCase(name)
							|| type.toString().equalsIgnoreCase(name)) {
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
}
