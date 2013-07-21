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

import com.gitblit.wicket.RequiresAdminRole;
import com.gitblit.wicket.panels.TeamsPanel;
import com.gitblit.wicket.panels.UsersPanel;

@RequiresAdminRole
public class UsersPage extends RootPage {

	public UsersPage() {
		super();		
		setupPage("", "");

		add(new TeamsPanel("teamsPanel", showAdmin).setVisible(showAdmin));
		
		add(new UsersPanel("usersPanel", showAdmin).setVisible(showAdmin));
	}
}
