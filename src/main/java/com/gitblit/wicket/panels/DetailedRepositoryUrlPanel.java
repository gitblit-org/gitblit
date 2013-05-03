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

import java.text.MessageFormat;

import org.apache.wicket.Component;
import org.apache.wicket.Localizer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.image.ContextImage;
import org.apache.wicket.markup.html.panel.Fragment;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.WicketUtils;

public class DetailedRepositoryUrlPanel extends BasePanel {

	private static final long serialVersionUID = 1L;
	public DetailedRepositoryUrlPanel(String wicketId, Localizer localizer, Component parent, String repository, String url) {
		this(wicketId, localizer, parent, repository, url, null);
	}
	
	public DetailedRepositoryUrlPanel(String wicketId, Localizer localizer, Component parent, String repository, String url, AccessPermission ap) {
		super(wicketId);
		
		String protocol = url.substring(0, url.indexOf(':'));
		String note;
		String permission;
		
		if (ap == null) {
			note = MessageFormat.format(localizer.getString("gb.externalPermissions", parent), protocol, repository);
			permission = "";
		} else {
			note = null;
			permission = ap.toString();
			String key;
			switch (ap) {
				case OWNER:
				case REWIND:
					key = "gb.rewindPermission";
					break;
				case DELETE:
					key = "gb.deletePermission";
					break;
				case CREATE:
					key = "gb.createPermission";
					break;
				case PUSH:
					key = "gb.pushPermission";
					break;
				case CLONE:
					key = "gb.clonePermission";
					break;
				default:
					key = null;
					note = localizer.getString("gb.viewAccess", parent);
					break;
			}
			
			if (note == null) {
				String pattern = localizer.getString(key, parent);
				String description = MessageFormat.format(pattern, permission);
				String permissionPattern = localizer.getString("gb.yourProtocolPermissionIs", parent);
				note = MessageFormat.format(permissionPattern, protocol.toUpperCase(), repository, description);
			}
		}
		
		if (!StringUtils.isEmpty(url) && ((ap == null) || ap.atLeast(AccessPermission.CLONE))) {
			// valid repository url
			Fragment fragment = new Fragment("urlPanel", "repositoryUrlPanel", this);
			add(fragment);
			Label protocolLabel = new Label("repositoryProtocol", protocol + "://");
			WicketUtils.setHtmlTooltip(protocolLabel, note);
			fragment.add(protocolLabel);
			fragment.add(new Label("repositoryUrl", url.substring(url.indexOf("://") + 3)));
			Label permissionLabel = new Label("repositoryUrlPermission", permission);
			WicketUtils.setHtmlTooltip(permissionLabel, note);
			fragment.add(permissionLabel);

			if (StringUtils.isEmpty(url)) {
				fragment.add(new Label("copyFunction").setVisible(false));
			} else if (GitBlit.getBoolean(Keys.web.allowFlashCopyToClipboard, true)) {
				// clippy: flash-based copy & paste
				Fragment copyFragment = new Fragment("copyFunction", "clippyPanel", this);
				String baseUrl = WicketUtils.getGitblitURL(getRequest());
				ShockWaveComponent clippy = new ShockWaveComponent("clippy", baseUrl + "/clippy.swf");
				clippy.setValue("flashVars", "text=" + StringUtils.encodeURL(url));
				copyFragment.add(clippy);
				fragment.add(copyFragment);
			} else {
				// javascript: manual copy & paste with modal browser prompt dialog
				Fragment copyFragment = new Fragment("copyFunction", "jsPanel", this);
				ContextImage img = WicketUtils.newImage("copyIcon", "clippy.png");
				img.add(new JavascriptTextPrompt("onclick", "Copy to Clipboard (Ctrl+C, Enter)", url));
				copyFragment.add(img);
				fragment.add(copyFragment);
			}
		} else {
			// no Git url, there may be a message
			add(new Label("urlPanel", MessageFormat.format("<i>{0}</i>", note)).setEscapeModelStrings(false).setVisible(!StringUtils.isEmpty(note)));
		}
	}
}
