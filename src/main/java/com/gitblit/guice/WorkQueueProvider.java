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

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.utils.IdGenerator;
import com.gitblit.utils.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/**
 * Provides a lazily-instantiated WorkQueue configured from IStoredSettings.
 *
 * @author James Moger
 *
 */
@Singleton
public class WorkQueueProvider implements Provider<WorkQueue> {

	private final IRuntimeManager runtimeManager;

	private volatile WorkQueue workQueue;

	@Inject
	public WorkQueueProvider(IRuntimeManager runtimeManager) {
		this.runtimeManager = runtimeManager;
	}

	@Override
	public synchronized WorkQueue get() {
		if (workQueue != null) {
			return workQueue;
		}

		IStoredSettings settings = runtimeManager.getSettings();
		int defaultThreadPoolSize = settings.getInteger(Keys.execution.defaultThreadPoolSize, 1);
		IdGenerator idGenerator = new IdGenerator();
		workQueue = new WorkQueue(idGenerator, defaultThreadPoolSize);
		return workQueue;
	}
}