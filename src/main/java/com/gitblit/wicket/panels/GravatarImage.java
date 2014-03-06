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
package com.gitblit.wicket.panels;

import org.eclipse.jgit.lib.PersonIdent;

import com.gitblit.Keys;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ActivityUtils;
import com.gitblit.wicket.ExternalImage;
import com.gitblit.wicket.WicketUtils;

/**
 * Represents a Gravatar image.
 *
 * @author James Moger
 *
 */
public class GravatarImage extends BasePanel {

	private static final long serialVersionUID = 1L;

	public GravatarImage(String id, PersonIdent person) {
		this(id, person, 0);
	}

	public GravatarImage(String id, PersonIdent person, int width) {
		this(id, person.getName(), person.getEmailAddress(), "gravatar", width, true);
	}

	public GravatarImage(String id, PersonIdent person, String cssClass, int width, boolean identicon) {
		this(id, person.getName(), person.getEmailAddress(), cssClass, width, identicon);
	}

	public GravatarImage(String id, UserModel user, String cssClass, int width, boolean identicon) {
		this(id, user.getDisplayName(), user.emailAddress, cssClass, width, identicon);
	}

	public GravatarImage(String id, String username, String emailaddress, String cssClass, int width, boolean identicon) {
		super(id);

		String email = emailaddress == null ? username.toLowerCase() : emailaddress.toLowerCase();
		String url;
		if (identicon) {
			url = ActivityUtils.getGravatarIdenticonUrl(email, width);
		} else {
			url = ActivityUtils.getGravatarThumbnailUrl(email, width);
		}
		ExternalImage image = new ExternalImage("image", url);
		if (cssClass != null) {
			WicketUtils.setCssClass(image, cssClass);
		}
		add(image);
		WicketUtils.setHtmlTooltip(image, username);
		setVisible(app().settings().getBoolean(Keys.web.allowGravatar, true));
	}

	public void setTooltip(String tooltip) {
		WicketUtils.setHtmlTooltip(get("image"), tooltip);
	}
}