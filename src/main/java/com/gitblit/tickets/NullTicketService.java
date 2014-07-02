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
package com.gitblit.tickets;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IPluginManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Attachment;
import com.gitblit.models.TicketModel.Change;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Implementation of a ticket service that rejects everything.
 *
 * @author James Moger
 *
 */
@Singleton
public class NullTicketService extends ITicketService {

	@Inject
	public NullTicketService(
			IRuntimeManager runtimeManager,
			IPluginManager pluginManager,
			INotificationManager notificationManager,
			IUserManager userManager,
			IRepositoryManager repositoryManager) {

		super(runtimeManager,
				pluginManager,
				notificationManager,
				userManager,
				repositoryManager);
	}

	@Override
	public boolean isReady() {
		return false;
	}

	@Override
	public NullTicketService start() {
		log.info("{} started", getClass().getSimpleName());
		return this;
	}

	@Override
	protected void resetCachesImpl() {
	}

	@Override
	protected void resetCachesImpl(RepositoryModel repository) {
	}

	@Override
	protected void close() {
	}

	@Override
	public boolean hasTicket(RepositoryModel repository, long ticketId) {
		return false;
	}

	@Override
	public synchronized Set<Long> getIds(RepositoryModel repository) {
		return Collections.emptySet();
	}

	@Override
	public synchronized long assignNewId(RepositoryModel repository) {
		return 0L;
	}

	@Override
	public List<TicketModel> getTickets(RepositoryModel repository, TicketFilter filter) {
		return Collections.emptyList();
	}

	@Override
	protected TicketModel getTicketImpl(RepositoryModel repository, long ticketId) {
		return null;
	}

	@Override
	protected List<Change> getJournalImpl(RepositoryModel repository, long ticketId) {
		return null;
	}

	@Override
	public boolean supportsAttachments() {
		return false;
	}

	@Override
	public Attachment getAttachment(RepositoryModel repository, long ticketId, String filename) {
		return null;
	}

	@Override
	protected synchronized boolean deleteTicketImpl(RepositoryModel repository, TicketModel ticket, String deletedBy) {
		return false;
	}

	@Override
	protected synchronized boolean commitChangeImpl(RepositoryModel repository, long ticketId, Change change) {
		return false;
	}

	@Override
	protected boolean deleteAllImpl(RepositoryModel repository) {
		return false;
	}

	@Override
	protected boolean renameImpl(RepositoryModel oldRepository, RepositoryModel newRepository) {
		return false;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
