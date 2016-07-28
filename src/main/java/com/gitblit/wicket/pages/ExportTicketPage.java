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

import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.http.WebResponse;
//import org.apache.wicket.IRequestTarget;
import org.apache.wicket.request.mapper.parameter.PageParameters;
//import org.apache.wicket.RequestCycle;
//import org.apache.wicket.protocol.http.WebResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.tickets.TicketSerializer;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.WicketUtils;

public class ExportTicketPage extends SessionPage {

	private final Logger logger = LoggerFactory.getLogger(getClass().getSimpleName());

	String contentType;

	public ExportTicketPage(final PageParameters params) {
		super(params);

		if (params.get("r").isEmpty()) {
			error(getString("gb.repositoryNotSpecified"));
			redirectToInterceptPage(new RepositoriesPage());
		}

		getRequestCycle().setRequestTarget(new IRequestTarget() {
			@Override
			public void detach(RequestCycle requestCycle) {
			}

			@Override
			public void respond(RequestCycle requestCycle) {
				WebResponse response = (WebResponse) requestCycle.getResponse();

				final String repositoryName = WicketUtils.getRepositoryName(params);
				RepositoryModel repository = app().repositories().getRepositoryModel(repositoryName);
				String objectId = WicketUtils.getObject(params).toLowerCase();
				if (objectId.endsWith(".json")) {
					objectId = objectId.substring(0, objectId.length() - ".json".length());
				}
				long id = Long.parseLong(objectId);
				TicketModel ticket = app().tickets().getTicket(repository, id);

				String content = TicketSerializer.serialize(ticket);
				contentType = "application/json; charset=UTF-8";
				response.setContentType(contentType);
				try {
					response.getOutputStream().write(content.getBytes("UTF-8"));
				} catch (Exception e) {
					logger.error("Failed to write text response", e);
				}
			}
		});
	}

	@Override
	protected void setHeaders(WebResponse response) {
		super.setHeaders(response);
		if (!StringUtils.isEmpty(contentType)) {
			response.setContentType(contentType);
		}
	}
}
