/*
 * Copyright 2013 gitblit.com.
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

import org.apache.wicket.Component;
import org.apache.wicket.Localizer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.image.ContextImage;
import org.apache.wicket.markup.html.link.ExternalLink;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.wicket.WicketUtils;

public class SparkleShareInvitePanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public SparkleShareInvitePanel(String wicketId, Localizer localizer, Component parent, String url, AccessPermission ap) {
		super(wicketId);
		ContextImage star = WicketUtils.newImage("sparkleshareIcon", "star_16x16.png");
		add(star);
		add(new ExternalLink("inviteUrl", url));
		String note = localizer.getString("gb.externalAccess", parent);
		String permission = "";
		if (ap != null) {
			permission = ap.toString();
			if (ap.atLeast(AccessPermission.PUSH)) {
				note = localizer.getString("gb.readWriteAccess", parent);
			} else if (ap.atLeast(AccessPermission.CLONE)) {
				note = localizer.getString("gb.readOnlyAccess", parent);
			} else {
				note = localizer.getString("gb.viewAccess", parent);
			}
		}
		Label label = new Label("accessPermission", permission);
		WicketUtils.setHtmlTooltip(label, note);
		add(label);
	}
}
