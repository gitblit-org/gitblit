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
package com.gitblit.wicket.pages;

import org.apache.wicket.markup.html.form.IChoiceRenderer;

import com.gitblit.models.UserModel;

/**
 * @author Alfred Schmid
 *
 */
final class UserChoiceRenderer implements IChoiceRenderer<UserModel> {

	@Override
	public Object getDisplayValue(UserModel userModel) {
		return userModel.getDisplayName();
	}

	@Override
	public String getIdValue(UserModel userModel, int index) {
		return userModel.getName().toLowerCase();
	}

}
