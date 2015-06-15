/*
 * Copyright 2015 gitblit.com.
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

import com.gitblit.AvatarGenerator;
import com.gitblit.GravatarGenerator;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.utils.StringUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/**
 * Provides a lazily-instantiated AvatarGenerator configured from IStoredSettings.
 *
 * @author James Moger
 *
 */
@Singleton
public class AvatarGeneratorProvider implements Provider<AvatarGenerator> {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final IRuntimeManager runtimeManager;

	private volatile AvatarGenerator avatarGenerator;

	@Inject
	public AvatarGeneratorProvider(IRuntimeManager runtimeManager) {
		this.runtimeManager = runtimeManager;
	}

	@Override
	public synchronized AvatarGenerator get() {
		if (avatarGenerator != null) {
			return avatarGenerator;
		}

		IStoredSettings settings = runtimeManager.getSettings();
		String clazz = settings.getString(Keys.web.avatarClass, GravatarGenerator.class.getName());
		if (StringUtils.isEmpty(clazz)) {
			clazz = GravatarGenerator.class.getName();
		}
		try {
			Class<? extends AvatarGenerator> generatorClass = (Class<? extends AvatarGenerator>) Class.forName(clazz);
			avatarGenerator = runtimeManager.getInjector().getInstance(generatorClass);
		} catch (Exception e) {
			logger.error("failed to create avatar generator", e);
			avatarGenerator = new GravatarGenerator();
		}
		return avatarGenerator;
	}
}