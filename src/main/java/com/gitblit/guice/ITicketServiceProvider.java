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
package com.gitblit.guice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.tickets.ITicketService;
import com.gitblit.tickets.NullTicketService;
import com.gitblit.utils.StringUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/**
 * Provides a lazily-instantiated ITicketService configured from IStoredSettings.
 *
 * @author James Moger
 *
 */
@Singleton
public class ITicketServiceProvider implements Provider<ITicketService> {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final IRuntimeManager runtimeManager;

	private volatile ITicketService service;

	@Inject
	public ITicketServiceProvider(IRuntimeManager runtimeManager) {
		this.runtimeManager = runtimeManager;
	}

	@Override
	public synchronized ITicketService get() {
		if (service != null) {
			return service;
		}

		IStoredSettings settings = runtimeManager.getSettings();
		String clazz = settings.getString(Keys.tickets.service, NullTicketService.class.getName());
		if (StringUtils.isEmpty(clazz)) {
			clazz = NullTicketService.class.getName();
		}

		try {
			Class<? extends ITicketService> serviceClass = (Class<? extends ITicketService>) Class.forName(clazz);
			service = runtimeManager.getInjector().getInstance(serviceClass);
		} catch (Exception e) {
			logger.error("failed to create ticket service", e);
			service = runtimeManager.getInjector().getInstance(NullTicketService.class);
		}

		return service;
	}
}