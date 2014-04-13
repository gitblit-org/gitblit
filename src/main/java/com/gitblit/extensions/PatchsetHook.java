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

/**
 * Extension point for plugins to respond to Ticket patchset changes.
 *
 * @author James Moger
 * @since 1.5.0
 *
 */
public abstract class PatchsetHook implements ExtensionPoint {

	/**
	 * Called after a new patchset has been created.  This patchset
	 * may not be the first patchset of the ticket.  The ticket may be a new
	 * proposal or it may be a existing ticket that now has a new patchset.
	 *
	 * @param ticket
	 * @since 1.5.0
	 */
	public abstract void onNewPatchset(TicketModel ticket);

	/**
	 * Called after a patchset has been FAST-FORWARD updated.
	 *
	 * @param ticket
	 * @since 1.5.0
	 */
	public abstract void onUpdatePatchset(TicketModel ticket);

	/**
	 * Called after a patchset has been merged to the integration branch specified
	 * in the ticket.
	 *
	 * @param ticket
	 * @since 1.5.0
	 */
	public abstract void onMergePatchset(TicketModel ticket);
}
