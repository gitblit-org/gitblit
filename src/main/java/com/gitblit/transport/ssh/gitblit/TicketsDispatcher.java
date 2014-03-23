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
package com.gitblit.transport.ssh.gitblit;

import java.util.List;

import org.kohsuke.args4j.Argument;

import com.gitblit.manager.IGitblit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.models.UserModel;
import com.gitblit.tickets.ITicketService;
import com.gitblit.tickets.QueryBuilder;
import com.gitblit.tickets.QueryResult;
import com.gitblit.tickets.TicketIndexer.Lucene;
import com.gitblit.transport.ssh.commands.CommandMetaData;
import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.gitblit.transport.ssh.commands.ListCommand;
import com.gitblit.utils.FlipTable;
import com.gitblit.utils.FlipTable.Borders;
import com.gitblit.utils.StringUtils;

@CommandMetaData(name = "tickets", description = "Ticket commands", hidden = true)
public class TicketsDispatcher extends DispatchCommand {

	@Override
	protected void setup(UserModel user) {
		register(user, ReviewCommand.class);
		register(user, ListTickets.class);
	}
	
	/* List tickets */
	@CommandMetaData(name = "list", aliases= { "ls" }, description = "List tickets")
	public static class ListTickets extends ListCommand<QueryResult> {

		@Argument(index = 0, metaVar = "REPOSITORY", usage = "repository")
		protected String repository;
		
		@Override
		protected List<QueryResult> getItems() throws UnloggedFailure {
			IGitblit gitblit = getContext().getGitblit();
			ITicketService tickets = gitblit.getTicketService();

			QueryBuilder sb = new QueryBuilder();
			sb.and(Lucene.status.matches(Status.New.toString())).or(Lucene.status.matches(Status.Open.toString()));

			QueryBuilder qb;
			if (StringUtils.isEmpty(repository)) {
				qb = sb;
			} else {
				qb = new QueryBuilder();
				RepositoryModel r = gitblit.getRepositoryModel(repository);
				if (r == null) {
					throw new UnloggedFailure(1,  String.format("%s not found!", repository));
				}
				qb.and(Lucene.rid.matches(r.getRID()));
				qb.and(sb.toSubquery().toString());
			}
			
			String query = qb.build();
			List<QueryResult> list = tickets.queryFor(query, 0, 0, null, true);
			return list;
		}

		@Override
		protected void asTable(List<QueryResult> list) {
			boolean forRepo = !StringUtils.isEmpty(repository);
			String[] headers;
			if (verbose) {
				if (forRepo) {
					String[] h = { "ID", "Title", "Status", "Last Modified" };
					headers = h;
				} else {
					String[] h = { "Repository", "ID", "Title", "Status", "Last Modified" };
					headers = h;
				}
			} else {
				if (forRepo) {
					String[] h = { "ID", "Title", "Status", "Last Modifed" };
					headers = h;
				} else {
					String[] h = { "Repository", "ID", "Title", "Status", "Last Modified" };
					headers = h;
				}
			}

			String[][] data = new String[list.size()][];
			for (int i = 0; i < list.size(); i++) {
				QueryResult q = list.get(i);

				if (verbose) {
					if (forRepo) {
						data[i] = new String[] { "" + q.number, q.title, q.status.toString(), formatDate(q.getDate()) };
					} else {
						data[i] = new String[] { q.repository, "" + q.number, q.title, q.status.toString(), formatDate(q.getDate()) };
					}
				} else {
					if (forRepo) {
						data[i] = new String[] { "" + q.number, q.title, q.status.toString(), formatDate(q.getDate()) };
					} else {
						data[i] = new String[] { q.repository, "" + q.number, q.title, q.status.toString(), formatDate(q.getDate()) };
					}
				}
			}
			stdout.println(FlipTable.of(headers, data, Borders.BODY_HCOLS));
		}

		@Override
		protected void asTabbed(List<QueryResult> list) {
			if (verbose) {
				for (QueryResult q : list) {
					outTabbed(q.repository, q.number, q.title, q.status.toString(),
							formatDate(q.getDate()));
				}
			} else {
				for (QueryResult q : list) {
					outTabbed(q.repository, q.number, q.title);
				}
			}
		}
	}
}
