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
package com.gitblit.dagger;

import groovy.lang.Singleton;

import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.wicket.protocol.http.IWebApplicationFactory;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.protocol.http.WicketFilter;

/**
 *
 * A Wicket filter that supports Dagger injection.
 *
 * @author James Moger
 *
 */
@Singleton
public class DaggerWicketFilter extends WicketFilter {

	@Inject
	Provider<WebApplication> webApplicationProvider;

	@Inject
	public DaggerWicketFilter() {
		super();
	}

	@Override
	protected IWebApplicationFactory getApplicationFactory() {
		return new IWebApplicationFactory() {
			@Override
			public WebApplication createApplication(WicketFilter filter) {
				return webApplicationProvider.get();
			}
		};
	}
}
