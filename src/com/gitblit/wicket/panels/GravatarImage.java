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
import org.apache.wicket.markup.html.panel.Panel;
import org.eclipse.jgit.lib.PersonIdent;

import com.gitblit.GitBlit;
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
public class GravatarImage extends Panel {

	private static final long serialVersionUID = 1L;

	public GravatarImage(String id, PersonIdent person) {
		this(id, person, 0);
	}

	public GravatarImage(String id, PersonIdent person, int width) {
		super(id);

		String email = person.getEmailAddress().toLowerCase();
		String hash = StringUtils.getMD5(email);
		Link<Void> link = new BookmarkablePageLink<Void>("link", GravatarProfilePage.class,
				WicketUtils.newObjectParameter(hash));
		link.add(new SimpleAttributeModifier("target", "_blank"));
		String url = ActivityUtils.getGravatarThumbnailUrl(email, width);
		ExternalImage image = new ExternalImage("image", url);
		WicketUtils.setCssClass(image, "gravatar");
		link.add(image);
		WicketUtils.setHtmlTooltip(link,
				MessageFormat.format("View Gravatar profile for {0}", person.getName()));
		add(link);
		setVisible(GitBlit.getBoolean(Keys.web.allowGravatar, true));
	}
}