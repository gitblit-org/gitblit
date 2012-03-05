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

import org.apache.wicket.markup.html.form.ChoiceRenderer;

/**
 * Choice renderer for a palette or list of string values.  This renderer
 * controls the id value of each option such that palettes are case insensitive.
 * 
 * @author James Moger
 *
 */
public class StringChoiceRenderer extends ChoiceRenderer<String> {

	private static final long serialVersionUID = 1L;
	
	public StringChoiceRenderer() {
		super("", "");
	}

	/**
	 * @see org.apache.wicket.markup.html.form.IChoiceRenderer#getIdValue(java.lang.Object, int)
	 */
	@Override
	public String getIdValue(String object, int index)
	{
		return object.toLowerCase();
	}
}
