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
package com.gitblit.wicket;

import org.apache.wicket.AbstractRestartResponseException;
import org.apache.wicket.Page;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.RequestCycle;
import org.apache.wicket.protocol.http.RequestUtils;
import org.apache.wicket.request.target.basic.RedirectRequestTarget;

/**
 * This exception bypasses the servlet container rewriting relative redirect
 * urls.  The container can and does decode the carefully crafted %2F path
 * separators on a redirect.  :(  Bad, bad servlet container.
 *
 * org.eclipse.jetty.server.Response#L447: String path=uri.getDecodedPath();
 *
 * @author James Moger
 */
public class GitblitRedirectException extends AbstractRestartResponseException {

	private static final long serialVersionUID = 1L;

	public <C extends Page> GitblitRedirectException(Class<C> pageClass) {
		this(pageClass, null);
	}

	public <C extends Page> GitblitRedirectException(Class<C> pageClass, PageParameters params) {
		RequestCycle cycle = RequestCycle.get();
		String relativeUrl = cycle.urlFor(pageClass, params).toString();
		String absoluteUrl = RequestUtils.toAbsolutePath(relativeUrl);
		cycle.setRequestTarget(new RedirectRequestTarget(absoluteUrl));
		cycle.setRedirect(true);
	}
}
