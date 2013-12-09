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
			type = Type.Request;
		}
		switch (type) {
		case Proposal:
			// merge request
			WicketUtils.setCssClass(label, "fa fa-code");
			break;
		case Bug:
			// bug ticket
			WicketUtils.setCssClass(label, "fa fa-bug");
			break;
		default:
			// standard ticket
			WicketUtils.setCssClass(label, "fa fa-ticket");
		}
		WicketUtils.setHtmlTooltip(label, getTypeState(type, state));
		return label;
	}

	protected String getTypeState(Type type, Status state) {
		if (Status.Merged == state) {
			return Type.Proposal == type ? getString("gb.mergedChangeProposal") : getString("gb.merged");
		}
		if (Type.Proposal == type) {
			// change proposal ticket
			if (state.isClosed()) {
				return getString("gb.closedChangeProposal");
			} else {
				return getString("gb.openChangeProposal");
			}
		} else {
			// standard ticket
			if (state.isClosed()) {
				return getString("gb.closedTicket");
			} else {
				return getString("gb.openTicket");
			}
		}
	}

	protected String getLozengeClass(Status resolution, boolean subtle) {
		if (resolution == null) {
			resolution = Status.New;
		}
		String css = "";
		switch (resolution) {
		case Declined:
		case Duplicate:
		case Invalid:
		case Wontfix:
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

	protected String getStatusClass(Status resolution) {
		String css = "";
		switch (resolution) {
		case Declined:
		case Duplicate:
		case Invalid:
		case Wontfix:
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
