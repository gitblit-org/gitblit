/*
 * Copyright 2012 gitblit.com.
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

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.PersonIdent;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.GravatarImage;
import com.gitblit.wicket.panels.LinkPanel;

public class ForksPage extends RepositoryPage {

	public ForksPage(PageParameters params) {
		super(params);
		
		RepositoryModel model = getRepositoryModel();
		RepositoryModel origin;
		List<String> list;
		if (ArrayUtils.isEmpty(model.forks)) {
			// origin repository has forks
			origin = GitBlit.self().getRepositoryModel(model.originRepository);
			list = new ArrayList<String>(origin.forks);
		} else {
			// this repository has forks
			origin = model;
			list = new ArrayList<String>(model.forks);
		}
		
		if (origin.isPersonalRepository()) {
			// personal repository
			UserModel user = GitBlit.self().getUserModel(origin.projectPath.substring(1));
			PersonIdent ident = new PersonIdent(user.getDisplayName(), user.emailAddress);
			add(new GravatarImage("forkSourceAvatar", ident, 20));
			add(new Label("forkSourceSwatch").setVisible(false));
			add(new LinkPanel("forkSourceProject", null, user.getDisplayName(), UserPage.class, WicketUtils.newUsernameParameter(user.username)));
		} else {
			// standard repository
			add(new GravatarImage("forkSourceAvatar", new PersonIdent("", ""), 20).setVisible(false));
			Component swatch;
			if (origin.isBare){
				swatch = new Label("forkSourceSwatch", "&nbsp;").setEscapeModelStrings(false);
			} else {
				swatch = new Label("forkSourceSwatch", "!");
				WicketUtils.setHtmlTooltip(swatch, getString("gb.workingCopyWarning"));
			}
			WicketUtils.setCssBackground(swatch, origin.toString());
			add(swatch);
			final boolean showSwatch = GitBlit.getBoolean(Keys.web.repositoryListSwatches, true);
			swatch.setVisible(showSwatch);
			
			String projectName = origin.projectPath;
			if (StringUtils.isEmpty(projectName)) {
				projectName = GitBlit.getString(Keys.web.repositoryRootGroupName, "main");
			}
			add(new LinkPanel("forkSourceProject", null, projectName, ProjectPage.class, WicketUtils.newProjectParameter(origin.projectPath)));
		}
		
		String source = StringUtils.getLastPathElement(origin.name);
		add(new LinkPanel("forkSource", null, StringUtils.stripDotGit(source), SummaryPage.class, WicketUtils.newRepositoryParameter(origin.name)));

		// only display user-accessible forks
		UserModel user = GitBlitWebSession.get().getUser();
		List<RepositoryModel> forks = new ArrayList<RepositoryModel>();
		for (String aFork : list) {
			RepositoryModel fork = GitBlit.self().getRepositoryModel(user, aFork);
			if (fork != null) {
				forks.add(fork);
			}
		}
		
		ListDataProvider<RepositoryModel> forksDp = new ListDataProvider<RepositoryModel>(forks);
		DataView<RepositoryModel> forksList = new DataView<RepositoryModel>("fork", forksDp) {
			private static final long serialVersionUID = 1L;

			public void populateItem(final Item<RepositoryModel> item) {
				RepositoryModel fork = item.getModelObject();
				
				if (fork.isPersonalRepository()) {
					UserModel user = GitBlit.self().getUserModel(fork.projectPath.substring(1));
					PersonIdent ident = new PersonIdent(user.getDisplayName(), user.emailAddress);
					item.add(new GravatarImage("anAvatar", ident, 20));
					item.add(new LinkPanel("aProject", null, user.getDisplayName(), UserPage.class, WicketUtils.newUsernameParameter(user.username)));
				} else {
					PersonIdent ident = new PersonIdent(fork.name, fork.name);
					item.add(new GravatarImage("anAvatar", ident, 20));
					item.add(new LinkPanel("aProject", null, fork.projectPath, ProjectPage.class, WicketUtils.newProjectParameter(fork.projectPath)));
				}
				
				String repo = StringUtils.getLastPathElement(fork.name);
				item.add(new LinkPanel("aFork", null, StringUtils.stripDotGit(repo), SummaryPage.class, WicketUtils.newRepositoryParameter(fork.name)));
				
				WicketUtils.setCssStyle(item, "margin-left:25px;");
			}
		};
		
		add(forksList);

	}

	@Override
	protected String getPageName() {
		return getString("gb.forks");
	}
}
