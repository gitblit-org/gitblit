/*
 * Copyright 2012 gitblit.com.
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
package com.gitblit.servlet;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;


/**
 * The PagesFilter is an AccessRestrictionFilter which ensures the gh-pages
 * requests for a view-restricted repository are authenticated and authorized.
 *
 * @author James Moger
 *
 */

@Singleton
public class PagesFilter extends RawFilter {

	@Inject
	public PagesFilter(
			IRuntimeManager runtimeManager,
			IAuthenticationManager authenticationManager,
			IRepositoryManager repositoryManager) {

		super(runtimeManager, authenticationManager, repositoryManager);
	}

}
