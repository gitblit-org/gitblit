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
package com.gitblit.extensions;

import ro.fortsoft.pf4j.ExtensionPoint;

import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Change;

/**
 * Extension point for plugins to respond to Ticket changes.
 *
 * @author James Moger
 * @since 1.5.0
 */
public abstract class TicketHook implements ExtensionPoint {

	/**
	 * Called when a new ticket is created.
	 *
	 * @param ticket
	 * @since 1.5.0
	 */
	public abstract void onNewTicket(TicketModel ticket);

	/**
	 * Called when an existing ticket is updated.  Tickets can be updated for
	 * many, many reasons like state changes votes, watches, etc.
	 *
	 * @param ticket
	 * @param change
	 * @since 1.5.0
	 */
	public abstract void onUpdateTicket(TicketModel ticket, Change change);
}
