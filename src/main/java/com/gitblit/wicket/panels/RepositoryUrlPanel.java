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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.Component;
import org.apache.wicket.RequestCycle;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.image.ContextImage;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.protocol.http.request.WebClientInfo;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Keys;
import com.gitblit.models.GitClientApplication;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.RepositoryUrl;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.ExternalImage;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;

/**
 * Smart repository url panel which can display multiple Gitblit repository urls
 * and also supports 3rd party app clone links.
 *
 * @author James Moger
 *
 */
public class RepositoryUrlPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	private final String externalPermission = "?";

	private boolean onlyUrls;
	private UserModel user;
	private RepositoryModel repository;
	private RepositoryUrl primaryUrl;
	private Map<String, String> urlPermissionsMap;
	private Map<AccessRestrictionType, String> accessRestrictionsMap;

	public RepositoryUrlPanel(String wicketId, boolean onlyUrls, UserModel user, RepositoryModel repository) {
		super(wicketId);
		this.onlyUrls = onlyUrls;
		this.user = user == null ? UserModel.ANONYMOUS : user;
		this.repository = repository;
		this.urlPermissionsMap = new HashMap<String, String>();
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();

		HttpServletRequest req = ((WebRequest) getRequest()).getHttpServletRequest();

		List<RepositoryUrl> repositoryUrls = app().services().getRepositoryUrls(req, user, repository);
		// grab primary url from the top of the list
		primaryUrl = repositoryUrls.size() == 0 ? null : repositoryUrls.get(0);

		boolean canClone = primaryUrl != null && ((primaryUrl.permission == null) || primaryUrl.permission.atLeast(AccessPermission.CLONE));

		if (repositoryUrls.size() == 0 || !canClone) {
			// no urls, nothing to show.
			add(new Label("repositoryUrlPanel").setVisible(false));
			add(new Label("applicationMenusPanel").setVisible(false));
			add(new Label("repositoryIndicators").setVisible(false));
			return;
		}

		// display primary url
		add(createPrimaryUrlPanel("repositoryUrlPanel", repository, repositoryUrls));

		if (onlyUrls) {
			add(new Label("repositoryIndicators").setVisible(false));
		} else {
			add(createRepositoryIndicators(repository));
		}

		boolean allowAppLinks = app().settings().getBoolean(Keys.web.allowAppCloneLinks, true);
		if (onlyUrls || !canClone || !allowAppLinks) {
			// only display the url(s)
			add(new Label("applicationMenusPanel").setVisible(false));
			return;
		}
		// create the git client application menus
		add(createApplicationMenus("applicationMenusPanel", user, repository, repositoryUrls));
	}

	public String getPrimaryUrl() {
		return primaryUrl == null ? "" : primaryUrl.url;
	}

	protected Fragment createPrimaryUrlPanel(String wicketId, final RepositoryModel repository, List<RepositoryUrl> repositoryUrls) {

		Fragment urlPanel = new Fragment(wicketId, "repositoryUrlFragment", this);
		urlPanel.setRenderBodyOnly(true);

		if (repositoryUrls.size() == 1) {
			//
			// Single repository url, no dropdown menu
			//
			urlPanel.add(new Label("menu").setVisible(false));
		} else {
			//
			// Multiple repository urls, show url drop down menu
			//
			ListDataProvider<RepositoryUrl> urlsDp = new ListDataProvider<RepositoryUrl>(repositoryUrls);
			DataView<RepositoryUrl> repoUrlMenuItems = new DataView<RepositoryUrl>("repoUrls", urlsDp) {
				private static final long serialVersionUID = 1L;

				@Override
				public void populateItem(final Item<RepositoryUrl> item) {
					RepositoryUrl repoUrl = item.getModelObject();
					// repository url
					Fragment fragment = new Fragment("repoUrl", "actionFragment", this);
					Component content = new Label("content", repoUrl.url).setRenderBodyOnly(true);
					WicketUtils.setCssClass(content, "commandMenuItem");
					fragment.add(content);
					item.add(fragment);

					Label permissionLabel = new Label("permission", repoUrl.isExternal() ? externalPermission : repoUrl.permission.toString());
					WicketUtils.setPermissionClass(permissionLabel, repoUrl.permission);
					String tooltip = getProtocolPermissionDescription(repository, repoUrl);
					WicketUtils.setHtmlTooltip(permissionLabel, tooltip);
					fragment.add(permissionLabel);
					fragment.add(createCopyFragment(repoUrl.url));
				}
			};

			Fragment urlMenuFragment = new Fragment("menu", "urlProtocolMenuFragment", this);
			urlMenuFragment.setRenderBodyOnly(true);
			urlMenuFragment.add(new Label("menuText", getString("gb.url")));
			urlMenuFragment.add(repoUrlMenuItems);
			urlPanel.add(urlMenuFragment);
		}

		// access restriction icon and tooltip
		if (repository.isMirror) {
			urlPanel.add(WicketUtils.newImage("accessRestrictionIcon", "mirror_16x16.png",
					getString("gb.isMirror")));
		} else if (app().services().isServingRepositories()) {
			switch (repository.accessRestriction) {
			case NONE:
				urlPanel.add(WicketUtils.newClearPixel("accessRestrictionIcon").setVisible(false));
				break;
			case PUSH:
				urlPanel.add(WicketUtils.newImage("accessRestrictionIcon", "lock_go_16x16.png",
						getAccessRestrictions().get(repository.accessRestriction)));
				break;
			case CLONE:
				urlPanel.add(WicketUtils.newImage("accessRestrictionIcon", "lock_pull_16x16.png",
						getAccessRestrictions().get(repository.accessRestriction)));
				break;
			case VIEW:
				urlPanel.add(WicketUtils.newImage("accessRestrictionIcon", "shield_16x16.png",
						getAccessRestrictions().get(repository.accessRestriction)));
				break;
			default:
				if (repositoryUrls.size() == 1) {
					// force left end cap to have some width
					urlPanel.add(WicketUtils.newBlankIcon("accessRestrictionIcon"));
				} else {
					urlPanel.add(WicketUtils.newClearPixel("accessRestrictionIcon").setVisible(false));
				}
			}
		} else {
			if (repositoryUrls.size() == 1) {
				// force left end cap to have some width
				urlPanel.add(WicketUtils.newBlankIcon("accessRestrictionIcon"));
			} else {
				urlPanel.add(WicketUtils.newClearPixel("accessRestrictionIcon").setVisible(false));
			}
		}

		urlPanel.add(new Label("primaryUrl", primaryUrl.url).setRenderBodyOnly(true));

		Label permissionLabel = new Label("primaryUrlPermission", primaryUrl.isExternal() ? externalPermission : primaryUrl.permission.toString());
		String tooltip = getProtocolPermissionDescription(repository, primaryUrl);
		WicketUtils.setHtmlTooltip(permissionLabel, tooltip);
		urlPanel.add(permissionLabel);
		urlPanel.add(createCopyFragment(primaryUrl.url));

		return urlPanel;
	}

	protected Fragment createApplicationMenus(String wicketId, final UserModel user, final RepositoryModel repository, final List<RepositoryUrl> repositoryUrls) {
		final List<GitClientApplication> displayedApps = new ArrayList<GitClientApplication>();
		final String userAgent = ((WebClientInfo) GitBlitWebSession.get().getClientInfo()).getUserAgent();

		if (user.canClone(repository)) {
			for (GitClientApplication app : app().gitblit().getClientApplications()) {
				if (app.isActive && app.allowsPlatform(userAgent)) {
					displayedApps.add(app);
				}
			}
		}

		final String baseURL = WicketUtils.getGitblitURL(RequestCycle.get().getRequest());
		ListDataProvider<GitClientApplication> displayedAppsDp = new ListDataProvider<GitClientApplication>(displayedApps);
		DataView<GitClientApplication> appMenus = new DataView<GitClientApplication>("appMenus", displayedAppsDp) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<GitClientApplication> item) {
				final GitClientApplication clientApp = item.getModelObject();

				// filter the urls for the client app
				List<RepositoryUrl> urls = new ArrayList<RepositoryUrl>();
				for (RepositoryUrl repoUrl : repositoryUrls) {
					if (clientApp.minimumPermission == null || repoUrl.permission == null) {
						// no minimum permission or external permissions, assume it is satisfactory
						if (clientApp.supportsTransport(repoUrl.url)) {
							urls.add(repoUrl);
						}
					} else if (repoUrl.permission.atLeast(clientApp.minimumPermission)) {
						// repo url meets minimum permission requirement
						if (clientApp.supportsTransport(repoUrl.url)) {
							urls.add(repoUrl);
						}
					}
				}

				if (urls.size() == 0) {
					// do not show this app menu because there are no urls
					item.add(new Label("appMenu").setVisible(false));
					return;
				}

				Fragment appMenu = new Fragment("appMenu", "appMenuFragment", this);
				appMenu.setRenderBodyOnly(true);
				item.add(appMenu);

				// menu button
				appMenu.add(new Label("applicationName", clientApp.name));

				// application icon
				Component img;
				if (StringUtils.isEmpty(clientApp.icon)) {
					img = WicketUtils.newClearPixel("applicationIcon").setVisible(false);
				} else {
					if (clientApp.icon.contains("://")) {
						// external image
						img = new ExternalImage("applicationIcon", clientApp.icon);
					} else {
						// context image
						img = WicketUtils.newImage("applicationIcon", clientApp.icon);
					}
				}
				appMenu.add(img);

				// application menu title, may be a link
				if (StringUtils.isEmpty(clientApp.productUrl)) {
					appMenu.add(new Label("applicationTitle", clientApp.toString()));
				} else {
					appMenu.add(new LinkPanel("applicationTitle", null, clientApp.toString(), clientApp.productUrl, true));
				}

				// brief application description
				if (StringUtils.isEmpty(clientApp.description)) {
					appMenu.add(new Label("applicationDescription").setVisible(false));
				} else {
					appMenu.add(new Label("applicationDescription", clientApp.description));
				}

				// brief application legal info, copyright, license, etc
				if (StringUtils.isEmpty(clientApp.legal)) {
					appMenu.add(new Label("applicationLegal").setVisible(false));
				} else {
					appMenu.add(new Label("applicationLegal", clientApp.legal));
				}

				// a nested repeater for all action items
				ListDataProvider<RepositoryUrl> urlsDp = new ListDataProvider<RepositoryUrl>(urls);
				DataView<RepositoryUrl> actionItems = new DataView<RepositoryUrl>("actionItems", urlsDp) {
					private static final long serialVersionUID = 1L;

					@Override
					public void populateItem(final Item<RepositoryUrl> repoLinkItem) {
						RepositoryUrl repoUrl = repoLinkItem.getModelObject();
						Fragment fragment = new Fragment("actionItem", "actionFragment", this);
						fragment.add(createPermissionBadge("permission", repoUrl));

						if (!StringUtils.isEmpty(clientApp.cloneUrl)) {
							// custom registered url
							String url = substitute(clientApp.cloneUrl, repoUrl.url, baseURL, user.username, repository.name);
							fragment.add(new LinkPanel("content", "applicationMenuItem", getString("gb.clone") + " " + repoUrl.url, url));
							repoLinkItem.add(fragment);
							fragment.add(new Label("copyFunction").setVisible(false));
						} else if (!StringUtils.isEmpty(clientApp.command)) {
							// command-line
							String command = substitute(clientApp.command, repoUrl.url, baseURL, user.username, repository.name);
							Label content = new Label("content", command);
							WicketUtils.setCssClass(content, "commandMenuItem");
							fragment.add(content);
							repoLinkItem.add(fragment);

							// copy function for command
							fragment.add(createCopyFragment(command));
						}
					}};
					appMenu.add(actionItems);
			}
		};

		Fragment applicationMenus = new Fragment(wicketId, "applicationMenusFragment", this);
		applicationMenus.add(appMenus);
		return applicationMenus;
	}

	protected String substitute(String pattern, String repoUrl, String baseUrl, String username, String repository) {
		return pattern.replace("${repoUrl}", repoUrl).replace("${baseUrl}", baseUrl).replace("${username}", username).replace("${repository}", repository);
	}

	protected Label createPermissionBadge(String wicketId, RepositoryUrl repoUrl) {
		Label permissionLabel = new Label(wicketId, repoUrl.isExternal() ? externalPermission : repoUrl.permission.toString());
		WicketUtils.setPermissionClass(permissionLabel, repoUrl.permission);
		String tooltip = getProtocolPermissionDescription(repository, repoUrl);
		WicketUtils.setHtmlTooltip(permissionLabel, tooltip);
		return permissionLabel;
	}

	protected Fragment createCopyFragment(String text) {
		if (app().settings().getBoolean(Keys.web.allowFlashCopyToClipboard, true)) {
			// clippy: flash-based copy & paste
			Fragment copyFragment = new Fragment("copyFunction", "clippyPanel", this);
			String baseUrl = WicketUtils.getGitblitURL(getRequest());
			ShockWaveComponent clippy = new ShockWaveComponent("clippy", baseUrl + "/clippy.swf");
			clippy.setValue("flashVars", "text=" + StringUtils.encodeURL(text));
			copyFragment.add(clippy);
			return copyFragment;
		} else {
			// javascript: manual copy & paste with modal browser prompt dialog
			Fragment copyFragment = new Fragment("copyFunction", "jsPanel", this);
			ContextImage img = WicketUtils.newImage("copyIcon", "clippy.png");
			img.add(new JavascriptTextPrompt("onclick", "Copy to Clipboard (Ctrl+C, Enter)", text));
			copyFragment.add(img);
			return copyFragment;
		}
	}

	protected String getProtocolPermissionDescription(RepositoryModel repository,
			RepositoryUrl repoUrl) {
		if (!urlPermissionsMap.containsKey(repoUrl.url)) {
			String note;
			if (repoUrl.isExternal()) {
				String protocol;
				int protocolIndex = repoUrl.url.indexOf("://");
				if (protocolIndex > -1) {
					// explicit protocol specified
					protocol = repoUrl.url.substring(0, protocolIndex);
				} else {
					// implicit SSH url
					protocol = "ssh";
				}
				note = MessageFormat.format(getString("gb.externalPermissions"), protocol);
			} else {
				note = null;
				String key;
				switch (repoUrl.permission) {
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
					note = getString("gb.viewAccess");
					break;
				}

				if (note == null) {
					String pattern = getString(key);
					String description = MessageFormat.format(pattern, repoUrl.permission.toString());
					note = description;
				}
			}
			urlPermissionsMap.put(repoUrl.url, note);
		}
		return urlPermissionsMap.get(repoUrl.url);
	}

	protected Map<AccessRestrictionType, String> getAccessRestrictions() {
		if (accessRestrictionsMap == null) {
			accessRestrictionsMap = new HashMap<AccessRestrictionType, String>();
			for (AccessRestrictionType type : AccessRestrictionType.values()) {
				switch (type) {
				case NONE:
					accessRestrictionsMap.put(type, getString("gb.notRestricted"));
					break;
				case PUSH:
					accessRestrictionsMap.put(type, getString("gb.pushRestricted"));
					break;
				case CLONE:
					accessRestrictionsMap.put(type, getString("gb.cloneRestricted"));
					break;
				case VIEW:
					accessRestrictionsMap.put(type, getString("gb.viewRestricted"));
					break;
				}
			}
		}
		return accessRestrictionsMap;
	}

	protected Component createRepositoryIndicators(RepositoryModel repository) {
		Fragment fragment = new Fragment("repositoryIndicators", "indicatorsFragment", this);
		if (repository.isBare) {
			fragment.add(new Label("workingCopyIndicator").setVisible(false));
		} else {
			Fragment wc = new Fragment("workingCopyIndicator", "workingCopyFragment", this);
			Label lbl = new Label("workingCopy", getString("gb.workingCopy"));
			WicketUtils.setHtmlTooltip(lbl,  getString("gb.workingCopyWarning"));
			wc.add(lbl);
			fragment.add(wc);
		}

		boolean allowForking = app().settings().getBoolean(Keys.web.allowForking, true);
		if (!allowForking || user == null || !user.isAuthenticated) {
			// must be logged-in to fork, hide all fork controls
			fragment.add(new Label("forksProhibitedIndicator").setVisible(false));
		} else {
			String fork = app().repositories().getFork(user.username, repository.name);
			boolean hasFork = fork != null;
			boolean canFork = user.canFork(repository);

			if (hasFork || !canFork) {
				if (user.canFork() && !repository.allowForks) {
					// show forks prohibited indicator
					Fragment wc = new Fragment("forksProhibitedIndicator", "forksProhibitedFragment", this);
					Label lbl = new Label("forksProhibited", getString("gb.forksProhibited"));
					WicketUtils.setHtmlTooltip(lbl,  getString("gb.forksProhibitedWarning"));
					wc.add(lbl);
					fragment.add(wc);
				} else {
					// can not fork, no need for forks prohibited indicator
					fragment.add(new Label("forksProhibitedIndicator").setVisible(false));
				}
			} else if (canFork) {
				// can fork and we do not have one
				fragment.add(new Label("forksProhibitedIndicator").setVisible(false));
			}
		}
		return fragment;
	}
}
