/*
 * Copyright 2011 gitblit.com.
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
package com.gitblit;

/**
 * The GitServlet exists to force configuration of the JGit GitServlet based on
 * the Gitblit settings from either gitblit.properties or from context
 * parameters in the web.xml file.
 * 
 * Access to this servlet is protected by the GitFilter.
 * 
 * @author James Moger
 * 
 */
public class GitServlet extends org.eclipse.jgit.http.server.GitServlet {

	private static final long serialVersionUID = 1L;

	/**
	 * Configure the servlet from Gitblit's configuration.
	 */
	@Override
	public String getInitParameter(String name) {
		if (name.equals("base-path")) {
			return GitBlit.getString(Keys.git.repositoriesFolder, "git");
		} else if (name.equals("export-all")) {
			return "1";
		}
		return super.getInitParameter(name);
	}
}
