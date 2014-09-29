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
package com.gitblit.wicket;

import java.io.Serializable;
import java.text.MessageFormat;

import org.apache.wicket.markup.html.basic.Label;

import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Priority;
import com.gitblit.models.TicketModel.Severity;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.models.TicketModel.Type;
import com.gitblit.utils.StringUtils;

/**
 * Common tickets ui methods and classes.
 *
 * @author James Moger
 *
 */
public class TicketsUI {

	public static final String [] openStatii = new String [] { Status.New.name().toLowerCase(), Status.Open.name().toLowerCase() };

	public static final String [] closedStatii = new String [] { "!" + Status.New.name().toLowerCase(), "!" + Status.Open.name().toLowerCase() };
	
	public static Label getStateIcon(String wicketId, TicketModel ticket) {
		return getStateIcon(wicketId, ticket.type, ticket.status);
	}

	public static Label getStateIcon(String wicketId, Type type, Status state) {
		Label label = new Label(wicketId);
		if (type == null) {
			type = Type.defaultType;
		}
		switch (type) {
		case Proposal:
			WicketUtils.setCssClass(label, "fa fa-code-fork fa-fw");
			break;
		case Bug:
			WicketUtils.setCssClass(label, "fa fa-bug fa-fw");
			break;
		case Enhancement:
			WicketUtils.setCssClass(label, "fa fa-magic fa-fw");
			break;
		case Question:
			WicketUtils.setCssClass(label, "fa fa-question fa-fw");
			break;
		case Maintenance:
			WicketUtils.setCssClass(label, "fa fa-cogs fa-fw");
			break;
		default:
			// standard ticket
			WicketUtils.setCssClass(label, "fa fa-ticket fa-fw");
		}
		WicketUtils.setHtmlTooltip(label, getTypeState(type, state));
		
		return label;
	}
	
	public static Label getPriorityIcon(String wicketId, Priority priority) {
		Label label = new Label(wicketId);
		if (priority == null) {
			priority = Priority.defaultPriority;
		}
		switch (priority) {
		case Urgent:
			WicketUtils.setCssClass(label, "fa fa-step-forward fa-rotate-270");
			break;
		case High:
			WicketUtils.setCssClass(label, "fa fa-caret-up fa-lg");
			break;
		case Low:
			WicketUtils.setCssClass(label, "fa fa-caret-down fa-lg");
			break;
		default:
		}
		WicketUtils.setHtmlTooltip(label, priority.toString());
		
		return label;
	}
	
	public static String getPriorityClass(Priority priority) {
		return String.format("priority-%s", priority);
	}

	public static String getSeverityClass(Severity severity) {
		return String.format("severity-%s", severity);	
	}
	
	public static String getTypeState(Type type, Status state) {
		return state.toString() + " " + type.toString();
	}

	public static String getLozengeClass(Status status, boolean subtle) {
		if (status == null) {
			status = Status.New;
		}
		String css = "";
		switch (status) {
		case Declined:
		case Duplicate:
		case Invalid:
		case Wontfix:
		case Abandoned:
			css = "aui-lozenge-error";
			break;
		case Fixed:
		case Merged:
		case Resolved:
			css = "aui-lozenge-success";
			break;
		case New:
			css = "aui-lozenge-complete";
			break;
		case On_Hold:
		case No_Change_Required:
			css = "aui-lozenge-current";
			break;
		default:
			css = "";
			break;
		}

		return "aui-lozenge" + (subtle ? " aui-lozenge-subtle": "") + (css.isEmpty() ? "" : " ") + css;
	}

	public static String getStatusClass(Status status) {
		String css = "";
		switch (status) {
		case Declined:
		case Duplicate:
		case Invalid:
		case Wontfix:
		case Abandoned:
			css = "resolution-error";
			break;
		case Fixed:
		case Merged:
		case Resolved:
			css = "resolution-success";
			break;
		case New:
			css = "resolution-complete";
			break;
		case On_Hold:
		case No_Change_Required:
			css = "resolution-current";
			break;
		default:
			css = "";
			break;
		}

		return "resolution" + (css.isEmpty() ? "" : " ") + css;
	}

	public static class TicketSort implements Serializable {

		private static final long serialVersionUID = 1L;

		public final String name;
		public final String sortBy;
		public final boolean desc;

		public TicketSort(String name, String sortBy, boolean desc) {
			this.name = name;
			this.sortBy = sortBy;
			this.desc = desc;
		}
	}

	public static class Indicator implements Serializable {

		private static final long serialVersionUID = 1L;

		public final String css;
		public final int count;
		public final String tooltip;

		public Indicator(String css, String tooltip) {
			this.css = css;
			this.tooltip = tooltip;
			this.count = 0;
		}

		public Indicator(String css, int count, String pattern) {
			this.css = css;
			this.count = count;
			this.tooltip = StringUtils.isEmpty(pattern) ? "" : MessageFormat.format(pattern, count);
		}

		public String getTooltip() {
			return tooltip;
		}
	}

	public static class TicketQuery implements Serializable, Comparable<TicketQuery> {

		private static final long serialVersionUID = 1L;

		public final String name;
		public final String query;
		public String color;

		public TicketQuery(String name, String query) {
			this.name = name;
			this.query = query;
		}

		public TicketQuery color(String value) {
			this.color = value;
			return this;
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof TicketQuery) {
				return ((TicketQuery) o).query.equals(query);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return query.hashCode();
		}

		@Override
		public int compareTo(TicketQuery o) {
			return query.compareTo(o.query);
		}
	}
}
