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
package com.gitblit.wicket.pages;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;

import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.models.TicketModel.Type;
import com.gitblit.wicket.WicketUtils;

public abstract class TicketBasePage extends RepositoryPage {

	public TicketBasePage(PageParameters params) {
		super(params);
	}

	protected Label getStateIcon(String wicketId, TicketModel ticket) {
		return getStateIcon(wicketId, ticket.type, ticket.status);
	}

	protected Label getStateIcon(String wicketId, Type type, Status state) {
		Label label = new Label(wicketId);
		if (type == null) {
			type = Type.defaultType;
		}
		switch (type) {
		case Proposal:
			WicketUtils.setCssClass(label, "fa fa-code-fork");
			break;
		case Bug:
			WicketUtils.setCssClass(label, "fa fa-bug");
			break;
		case Enhancement:
			WicketUtils.setCssClass(label, "fa fa-magic");
			break;
		case Question:
			WicketUtils.setCssClass(label, "fa fa-question");
			break;
		default:
			// standard ticket
			WicketUtils.setCssClass(label, "fa fa-ticket");
		}
		WicketUtils.setHtmlTooltip(label, getTypeState(type, state));
		return label;
	}

	protected String getTypeState(Type type, Status state) {
		return state.toString() + " " + type.toString();
	}

	protected String getLozengeClass(Status status, boolean subtle) {
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
			css = "aui-lozenge-current";
			break;
		default:
			css = "";
			break;
		}

		return "aui-lozenge" + (subtle ? " aui-lozenge-subtle": "") + (css.isEmpty() ? "" : " ") + css;
	}

	protected String getStatusClass(Status status) {
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
			css = "resolution-current";
			break;
		default:
			css = "";
			break;
		}

		return "resolution" + (css.isEmpty() ? "" : " ") + css;
	}
}
