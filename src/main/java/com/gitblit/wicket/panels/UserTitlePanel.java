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
package com.gitblit.wicket.panels;

import org.apache.wicket.markup.html.basic.Label;

import com.gitblit.models.UserModel;

public class UserTitlePanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public UserTitlePanel(String wicketId, UserModel user, String title) {
		super(wicketId);
		add(new AvatarImage("userGravatar", user, "gravatar", 36, false));
		add(new Label("userDisplayName", user.getDisplayName()));
		add(new Label("userTitle", title));
	}
}
