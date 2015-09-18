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

import java.io.Serializable;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.wicket.behavior.HeaderContributor;
import org.apache.wicket.markup.html.basic.Label;

import com.gitblit.Keys;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.freemarker.FreemarkerPanel;
import com.gitblit.wicket.ng.NgController;

/**
 * A client-side filterable rich repository list which uses Freemarker, Wicket,
 * and AngularJS.
 *
 * @author James Moger
 *
 */
public class FilterableRepositoryList extends BasePanel {

	private static final long serialVersionUID = 1L;

	private final List<RepositoryModel> repositories;

	private String title;

	private String iconClass;

	private boolean allowCreate;

	public FilterableRepositoryList(String id, List<RepositoryModel> repositories) {
		super(id);
		this.repositories = repositories;
	}

	public void setTitle(String title, String iconClass) {
		this.title = title;
		this.iconClass = iconClass;
	}

	public void setAllowCreate(boolean value) {
		this.allowCreate = value;
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();

		String id = getId();
		String ngCtrl = id + "Ctrl";
		String ngList = id + "List";

		Map<String, Object> values = new HashMap<String, Object>();
		values.put("ngCtrl",  ngCtrl);
		values.put("ngList",  ngList);

		// use Freemarker to setup an AngularJS/Wicket html snippet
		FreemarkerPanel panel = new FreemarkerPanel("listComponent", "FilterableRepositoryList.fm", values);
		panel.setParseGeneratedMarkup(true);
		panel.setRenderBodyOnly(true);
		add(panel);

		// add the Wicket controls that are referenced in the snippet
		String listTitle = StringUtils.isEmpty(title) ? getString("gb.repositories") : title;
		panel.add(new Label(ngList + "Title", MessageFormat.format("{0} ({1})", listTitle, repositories.size())));
		if (StringUtils.isEmpty(iconClass)) {
			panel.add(new Label(ngList + "Icon").setVisible(false));
		} else {
			Label icon = new Label(ngList + "Icon");
			WicketUtils.setCssClass(icon, iconClass);
			panel.add(icon);
		}

		if (allowCreate) {
			panel.add(new LinkPanel(ngList + "Button", "btn btn-mini", getString("gb.newRepository"), app().getNewRepositoryPage()));
		} else {
			panel.add(new Label(ngList + "Button").setVisible(false));
		}

		String format = app().settings().getString(Keys.web.datestampShortFormat, "MM/dd/yy");
		final DateFormat df = new SimpleDateFormat(format);
		df.setTimeZone(getTimeZone());

		// prepare the simplified repository models list
		List<RepoListItem> list = new ArrayList<RepoListItem>();
		for (RepositoryModel repo : repositories) {
			String name = StringUtils.stripDotGit(repo.name);
			String path = "";
			if (name.indexOf('/') > -1) {
				path = name.substring(0, name.lastIndexOf('/') + 1);
				name = name.substring(name.lastIndexOf('/') + 1);
			}

			String mountParamRepoTarget = null;
			if (app().settings().getBoolean(Keys.web.mountParameters, true)) {
				char c = app().settings().getChar(Keys.web.forwardSlashCharacter, '/');
				mountParamRepoTarget = (path + name).replace('/',  c);
			} else {
				mountParamRepoTarget = "?r=" + path + name;
			}

			RepoListItem item = new RepoListItem();
			item.n = name;
			item.p = path;
			item.r = repo.name;
			item.m = mountParamRepoTarget;
			item.i = repo.description;
			item.s = app().repositories().getStarCount(repo);
			item.t = getTimeUtils().timeAgo(repo.lastChange);
			item.d = df.format(repo.lastChange);
			item.c = StringUtils.getColor(StringUtils.stripDotGit(repo.name));
			if (!repo.isBare) {
				item.y = 3;
			} else if (repo.isMirror) {
				item.y = 2;
			} else if (repo.isFork()) {
				item.y = 1;
			} else {
				item.y = 0;
			}
			list.add(item);
		}

		// inject an AngularJS controller with static data
		NgController ctrl = new NgController(ngCtrl);
		ctrl.addVariable(ngList, list);
		add(new HeaderContributor(ctrl));
	}

	protected class RepoListItem implements Serializable {

		private static final long serialVersionUID = 1L;

		String r; // repository
		String n; // name
		String p; // project/path
		String m; // link snippet spec adapted for mountedParameter configuration
		String t; // time ago
		String d; // last updated
		String i; // information/description
		long s;   // stars
		String c; // html color
		int y;    // type: 0 = normal, 1 = fork, 2 = mirror, 3 = clone
	}
}