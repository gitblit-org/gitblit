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

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import dagger.ObjectGraph;

/**
 * Uses Dagger to manually inject dependencies into a servlet filter.
 * This class is useful for servlet containers that offer CDI and are
 * confused by Dagger.
 *
 * @author James Moger
 *
 */
public abstract class DaggerFilter implements Filter {

	@Override
	public final void init(FilterConfig filterConfig) throws ServletException {
		ServletContext context = filterConfig.getServletContext();
		ObjectGraph objectGraph = (ObjectGraph) context.getAttribute(DaggerContext.INJECTOR_NAME);
		inject(objectGraph);
	}

	protected abstract void inject(ObjectGraph dagger);

	@Override
	public void destroy() {
	}
}
