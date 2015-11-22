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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.PersonIdent;

import com.gitblit.Keys;
import com.gitblit.models.ForkModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.AvatarImage;
import com.gitblit.wicket.panels.LinkPanel;

public class ForksPage extends RepositoryPage {

	public ForksPage(PageParameters params) {
		super(params);

		final RepositoryModel pageRepository = getRepositoryModel();

		ForkModel root = app().repositories().getForkNetwork(pageRepository.name);
		List<FlatFork> network = flatten(root);

		ListDataProvider<FlatFork> forksDp = new ListDataProvider<FlatFork>(network);
		DataView<FlatFork> forksList = new DataView<FlatFork>("fork", forksDp) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<FlatFork> item) {
				FlatFork fork = item.getModelObject();
				RepositoryModel repository = fork.repository;

				if (repository.isPersonalRepository()) {
					UserModel user = app().users().getUserModel(repository.projectPath.substring(1));
					if (user == null) {
						// user account no longer exists
						user = new UserModel(repository.projectPath.substring(1));
					}
					PersonIdent ident = new PersonIdent(user.getDisplayName(), user.emailAddress == null ? user.getDisplayName() : user.emailAddress);
					item.add(new AvatarImage("anAvatar", ident, 20));
					if (pageRepository.equals(repository)) {
						// do not link to self
						item.add(new Label("aProject", user.getDisplayName()));
					} else {
						item.add(new LinkPanel("aProject", null, user.getDisplayName(), UserPage.class, WicketUtils.newUsernameParameter(user.username)));
					}
				} else {
					Component swatch;
					if (repository.isBare){
						swatch = new Label("anAvatar", "&nbsp;").setEscapeModelStrings(false);
					} else {
						swatch = new Label("anAvatar", "!");
						WicketUtils.setHtmlTooltip(swatch, getString("gb.workingCopyWarning"));
					}
					WicketUtils.setCssClass(swatch,  "repositorySwatch");
					WicketUtils.setCssBackground(swatch, repository.toString());
					item.add(swatch);
					String projectName = repository.projectPath;
					if (StringUtils.isEmpty(projectName)) {
						projectName = app().settings().getString(Keys.web.repositoryRootGroupName, "main");
					}
					if (pageRepository.equals(repository)) {
						// do not link to self
						item.add(new Label("aProject", projectName));
					} else {
						item.add(new LinkPanel("aProject", null, projectName, ProjectPage.class, WicketUtils.newProjectParameter(projectName)));
					}
				}

				String repo = StringUtils.getLastPathElement(repository.name);
				UserModel user = GitBlitWebSession.get().getUser();
				if (user == null) {
					user = UserModel.ANONYMOUS;
				}
				if (user.canView(repository)) {
					if (pageRepository.equals(repository)) {
						// do not link to self
						item.add(new Label("aFork", StringUtils.stripDotGit(repo)));
					} else {
						item.add(new LinkPanel("aFork", null, StringUtils.stripDotGit(repo), SummaryPage.class, WicketUtils.newRepositoryParameter(repository.name)));
					}
					item.add(WicketUtils.createDateLabel("lastChange", repository.lastChange, getTimeZone(), getTimeUtils()));
				} else {
					item.add(new Label("aFork", repo));
					item.add(new Label("lastChange").setVisible(false));
				}

				WicketUtils.setCssStyle(item, "margin-left:" + (32*fork.level) + "px;");
				if (fork.level == 0) {
					WicketUtils.setCssClass(item, "forkSource");
				} else {
					WicketUtils.setCssClass(item,  "forkEntry");
				}
			}
		};

		add(forksList);
	}

	@Override
	protected String getPageName() {
		return getString("gb.forks");
	}

	protected List<FlatFork> flatten(ForkModel root) {
		List<FlatFork> list = new ArrayList<FlatFork>();
		list.addAll(flatten(root, 0));
		return list;
	}

	protected List<FlatFork> flatten(ForkModel node, int level) {
		List<FlatFork> list = new ArrayList<FlatFork>();
		if (node == null) {
			return list;
		}
		list.add(new FlatFork(node.repository, level));
		if (!node.isLeaf()) {
			for (ForkModel fork : node.forks) {
				list.addAll(flatten(fork, level + 1));
			}
		}
		return list;
	}

	private class FlatFork implements Serializable {

		private static final long serialVersionUID = 1L;

		public final RepositoryModel repository;
		public final int level;

		public FlatFork(RepositoryModel repository, int level) {
			this.repository = repository;
			this.level = level;
		}
	}
}
