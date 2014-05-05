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
package com.gitblit.servlet;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import ro.fortsoft.pf4j.PluginWrapper;

import com.gitblit.dagger.DaggerFilter;
import com.gitblit.extensions.HttpRequestFilter;
import com.gitblit.manager.IPluginManager;
import com.gitblit.manager.IRuntimeManager;

import dagger.ObjectGraph;

/**
 * A request filter than allows registered extension request filters to access
 * request data.  The intended purpose is for server monitoring plugins.
 *
 * @author David Ostrovsky
 * @since 1.6.0
 */
public class ProxyFilter extends DaggerFilter {
	private List<HttpRequestFilter> filters;

	@Override
	protected void inject(ObjectGraph dagger, FilterConfig filterConfig) throws ServletException {
		IRuntimeManager runtimeManager = dagger.get(IRuntimeManager.class);
		IPluginManager pluginManager = dagger.get(IPluginManager.class);

		filters = pluginManager.getExtensions(HttpRequestFilter.class);
		for (HttpRequestFilter f : filters) {
			// wrap the filter config for Gitblit settings retrieval
			PluginWrapper pluginWrapper = pluginManager.whichPlugin(f.getClass());
			FilterConfig runtimeConfig = new FilterRuntimeConfig(runtimeManager,
					pluginWrapper.getPluginId(), filterConfig);

			f.init(runtimeConfig);
		}
	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse res, final FilterChain last)
			throws IOException, ServletException {
		final Iterator<HttpRequestFilter> itr = filters.iterator();
		new FilterChain() {
			@Override
			public void doFilter(ServletRequest req, ServletResponse res) throws IOException,
					ServletException {
				if (itr.hasNext()) {
					itr.next().doFilter(req, res, this);
				} else {
					last.doFilter(req, res);
				}
			}
		}.doFilter(req, res);
	}

	@Override
	public void destroy() {
		for (HttpRequestFilter f : filters) {
			f.destroy();
		}
	}
}
