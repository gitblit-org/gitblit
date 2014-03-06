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
package com.gitblit.git;

import org.eclipse.jgit.events.RefsChangedEvent;
import org.eclipse.jgit.transport.ReceiveCommand;

import com.gitblit.models.RepositoryModel;

/**
 * The event fired by other classes to allow this service to index tickets.
 *
 * @author James Moger
 */
public class ReceiveCommandEvent extends RefsChangedEvent {

	public final RepositoryModel model;

	public final ReceiveCommand cmd;

	public ReceiveCommandEvent(RepositoryModel model, ReceiveCommand cmd) {
		this.model = model;
		this.cmd = cmd;
	}
}