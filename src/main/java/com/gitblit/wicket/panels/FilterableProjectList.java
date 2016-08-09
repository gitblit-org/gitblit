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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.wicket.markup.html.basic.Label;

import com.gitblit.Keys;
import com.gitblit.models.ProjectModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.freemarker.FreemarkerPanel;
import com.gitblit.wicket.ng.NgController;

/**
 * A client-side filterable rich project list which uses Freemarker, Wicket,
 * and AngularJS.
 *
 * @author James Moger
 *
 */
public class FilterableProjectList extends BasePanel {

	private static final long serialVersionUID = 1L;

	private final List<ProjectModel> projects;

	private String title;

	private String iconClass;

	public FilterableProjectList(String id, List<ProjectModel> projects) {
		super(id);
		this.projects = projects;
	}

	public void setTitle(String title, String iconClass) {
		this.title = title;
		this.iconClass = iconClass;
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
		FreemarkerPanel panel = new FreemarkerPanel("listComponent", "FilterableProjectList.fm", values);
		panel.setParseGeneratedMarkup(true);
		panel.setRenderBodyOnly(true);
		add(panel);

		// add the Wicket controls that are referenced in the snippet
		String listTitle = StringUtils.isEmpty(title) ? getString("gb.projects") : title;
		panel.add(new Label(ngList + "Title", MessageFormat.format("{0} ({1})", listTitle, projects.size())));
		if (StringUtils.isEmpty(iconClass)) {
			panel.add(new Label(ngList + "Icon").setVisible(false));
		} else {
			Label icon = new Label(ngList + "Icon");
			WicketUtils.setCssClass(icon, iconClass);
			panel.add(icon);
		}

		String format = app().settings().getString(Keys.web.datestampShortFormat, "MM/dd/yy");
		final DateFormat df = new SimpleDateFormat(format);
		df.setTimeZone(getTimeZone());
		Collections.sort(projects, new Comparator<ProjectModel>() {
			@Override
			public int compare(ProjectModel o1, ProjectModel o2) {
				return o2.lastChange.compareTo(o1.lastChange);
			}
		});

		List<ProjectListItem> list = new ArrayList<ProjectListItem>();
		for (ProjectModel proj : projects) {
			if (proj.isUserProject() || proj.repositories.isEmpty()) {
				// exclude user projects from list
				continue;
			}
			ProjectListItem item = new ProjectListItem();
			item.p = proj.name;
			item.n = StringUtils.isEmpty(proj.title) ? proj.name : proj.title;
			item.i = proj.description;
			item.t = getTimeUtils().timeAgo(proj.lastChange);
			item.d = df.format(proj.lastChange);
			item.c = proj.repositories.size();
			list.add(item);
		}

		// inject an AngularJS controller with static data
		NgController ctrl = new NgController(ngCtrl);
		ctrl.addVariable(ngList, list);
		add(ctrl);
	}

	protected class ProjectListItem implements Serializable {

		private static final long serialVersionUID = 1L;

		String p; // path
		String n; // name
		String t; // time ago
		String d; // last updated
		String i; // information/description
		long c;   // repository count
	}
}