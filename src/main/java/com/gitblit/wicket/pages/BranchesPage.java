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
package com.gitblit.wicket.pages;

import org.apache.wicket.request.mapper.parameter.PageParameters;

import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.CacheControl.LastModified;
import com.gitblit.wicket.panels.BranchesPanel;

@CacheControl(LastModified.REPOSITORY)
public class BranchesPage extends RepositoryPage {

	public BranchesPage(PageParameters params) {
		super(params);

		add(new BranchesPanel("branchesPanel", getRepositoryModel(), getRepository(), -1, isShowAdmin() || isOwner()));
	}

	@Override
	protected String getPageName() {
		return getString("gb.branches");
	}
}
