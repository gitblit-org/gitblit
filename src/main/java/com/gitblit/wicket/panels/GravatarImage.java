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

import java.text.MessageFormat;

import org.apache.wicket.behavior.SimpleAttributeModifier;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;
import org.eclipse.jgit.lib.PersonIdent;

import com.gitblit.Keys;
import com.gitblit.utils.ActivityUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.ExternalImage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.GravatarProfilePage;

/**
 * Represents a Gravatar image and links to the Gravatar profile page.
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
		this(id, person, width, true);
	}

	public GravatarImage(String id, PersonIdent person, int width, boolean linked) {
		this(id, person.getName(), person.getEmailAddress(), "gravatar", width, linked, true);
	}

	public GravatarImage(String id, String username, String emailaddress, String cssClass, int width, boolean linked, boolean identicon) {
		super(id);

		String email = emailaddress == null ? username.toLowerCase() : emailaddress.toLowerCase();
		String hash = StringUtils.getMD5(email);
		Link<Void> link = new BookmarkablePageLink<Void>("link", GravatarProfilePage.class,
				WicketUtils.newObjectParameter(hash));
		link.add(new SimpleAttributeModifier("target", "_blank"));
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
		link.add(image);
		if (linked) {
			WicketUtils.setHtmlTooltip(link,
				MessageFormat.format("View Gravatar profile for {0}", username));
		} else {
			WicketUtils.setHtmlTooltip(link, username);
		}
		add(link.setEnabled(linked));
		setVisible(app().settings().getBoolean(Keys.web.allowGravatar, true));
	}
}