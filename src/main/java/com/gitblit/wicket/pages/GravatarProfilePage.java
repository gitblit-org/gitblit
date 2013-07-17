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

import java.io.IOException;
import java.text.MessageFormat;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.ExternalLink;

import com.gitblit.models.GravatarProfile;
import com.gitblit.utils.ActivityUtils;
import com.gitblit.wicket.ExternalImage;
import com.gitblit.wicket.WicketUtils;

/**
 * Gravatar Profile Page shows the Gravatar information, if available.
 * 
 * @author James Moger
 * 
 */
public class GravatarProfilePage extends RootPage {

	public GravatarProfilePage(PageParameters params) {
		super();
		setupPage("", "");
		String object = WicketUtils.getObject(params);
		GravatarProfile profile = null;
		try {
			if (object.indexOf('@') > -1) {
				profile = ActivityUtils.getGravatarProfileFromAddress(object);
			} else {
				profile = ActivityUtils.getGravatarProfile(object);
			}
		} catch (IOException e) {
			error(MessageFormat.format(getString("gb.failedToFindGravatarProfile"), object), e, true);
		}
		
		if (profile == null) {
			error(MessageFormat.format(getString("gb.failedToFindGravatarProfile"), object), true);
		}
		add(new Label("displayName", profile.displayName));
		add(new Label("username", profile.preferredUsername));
		add(new Label("location", profile.currentLocation));
		add(new Label("aboutMe", profile.aboutMe));
		ExternalImage image = new ExternalImage("profileImage", profile.thumbnailUrl + "?s=256&d=identicon");
		add(image);
		add(new ExternalLink("profileLink", profile.profileUrl));
	}
}
