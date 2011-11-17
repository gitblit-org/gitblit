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
package com.gitblit.wicket;

import java.text.MessageFormat;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.WebComponent;
import org.apache.wicket.model.Model;
import org.eclipse.jgit.lib.PersonIdent;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.utils.StringUtils;

/**
 * Represents a Gravatar image.
 * 
 * @author James Moger
 * 
 */
public class GravatarImage extends WebComponent {

	private static final long serialVersionUID = 1L;

	public GravatarImage(String id, PersonIdent person) {
		this(id, person, 0);
	}
	
	public GravatarImage(String id, PersonIdent person, int width) {
		super(id);
		if (width <= 0) {
			width = 60;
		}
		String authorhash = StringUtils.getMD5(person.getEmailAddress().toLowerCase());
		String url = MessageFormat.format("http://www.gravatar.com/avatar/{0}?s={1,number,0}&d=identicon", authorhash, width);
		add(new AttributeModifier("src", true, new Model<String>(url)));
		setVisible(GitBlit.getBoolean(Keys.web.allowGravatar, true));
		WicketUtils.setCssClass(this, "gravatar");
	}

	@Override
	protected void onComponentTag(ComponentTag tag) {
		super.onComponentTag(tag);
		checkComponentTag(tag, "img");
	}

}