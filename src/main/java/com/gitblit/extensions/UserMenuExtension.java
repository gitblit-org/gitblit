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
package com.gitblit.extensions;

import java.util.List;

import ro.fortsoft.pf4j.ExtensionPoint;

import com.gitblit.models.Menu.MenuItem;
import com.gitblit.models.UserModel;

/**
 * Extension point to contribute user menu items.
 *
 * @author James Moger
 * @since 1.6.0
 *
 */
public abstract class UserMenuExtension implements ExtensionPoint {

	/**
	 * @param user
	 * @since 1.6.0
	 * @return a list of menu items
	 */
	public abstract List<MenuItem> getMenuItems(UserModel user);
}
