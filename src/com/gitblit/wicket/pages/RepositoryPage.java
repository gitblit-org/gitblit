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

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.protocol.http.RequestUtils;
import org.apache.wicket.request.target.basic.RedirectRequestTarget;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.Constants;
import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.PagesServlet;
import com.gitblit.SyndicationServlet;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.SubmoduleModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TicgitUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.PageRegistration;
import com.gitblit.wicket.PageRegistration.OtherPageLink;
import com.gitblit.wicket.SessionlessForm;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.LinkPanel;
import com.gitblit.wicket.panels.NavigationPanel;
import com.gitblit.wicket.panels.RefsPanel;

public abstract class RepositoryPage extends BasePage {

	protected final String repositoryName;
	protected final String objectId;
	
	private transient Repository r;

	private RepositoryModel m;

	private Map<String, SubmoduleModel> submodules;
	
	private final Map<String, PageRegistration> registeredPages;

	public RepositoryPage(PageParameters params) {
		super(params);
		repositoryName = WicketUtils.getRepositoryName(params);
		objectId = WicketUtils.getObject(params);
		
		if (StringUtils.isEmpty(repositoryName)) {
			error(MessageFormat.format(getString("gb.repositoryNotSpecifiedFor"), getPageName()), true);
		}

		if (!getRepositoryModel().hasCommits) {
			setResponsePage(EmptyRepositoryPage.class, params);
		}

		// register the available page links for this page and user
		registeredPages = registerPages();

		// standard page links
		List<PageRegistration> pages = new ArrayList<PageRegistration>(registeredPages.values());
		NavigationPanel navigationPanel = new NavigationPanel("navPanel", getClass(), pages);
		add(navigationPanel);

		add(new ExternalLink("syndication", SyndicationServlet.asLink(getRequest()
				.getRelativePathPrefixToContextRoot(), repositoryName, null, 0)));

		// add floating search form
		SearchForm searchForm = new SearchForm("searchForm", repositoryName);
		add(searchForm);
		searchForm.setTranslatedAttributes();

		// set stateless page preference
		setStatelessHint(true);
	}

	private Map<String, PageRegistration> registerPages() {
		PageParameters params = null;
		if (!StringUtils.isEmpty(repositoryName)) {
			params = WicketUtils.newRepositoryParameter(repositoryName);
		}
		Map<String, PageRegistration> pages = new LinkedHashMap<String, PageRegistration>();

		// standard links
		pages.put("repositories", new PageRegistration("gb.repositories", RepositoriesPage.class));
		pages.put("summary", new PageRegistration("gb.summary", SummaryPage.class, params));
		pages.put("log", new PageRegistration("gb.log", LogPage.class, params));
		pages.put("branches", new PageRegistration("gb.branches", BranchesPage.class, params));
		pages.put("tags", new PageRegistration("gb.tags", TagsPage.class, params));
		pages.put("tree", new PageRegistration("gb.tree", TreePage.class, params));

		// conditional links
		Repository r = getRepository();
		RepositoryModel model = getRepositoryModel();

		// per-repository extra page links
		if (model.useTickets && TicgitUtils.getTicketsBranch(r) != null) {
			pages.put("tickets", new PageRegistration("gb.tickets", TicketsPage.class, params));
		}
		if (model.useDocs) {
			pages.put("docs", new PageRegistration("gb.docs", DocsPage.class, params));
		}
		if (JGitUtils.getPagesBranch(r) != null) {
			OtherPageLink pagesLink = new OtherPageLink("gb.pages", PagesServlet.asLink(
					getRequest().getRelativePathPrefixToContextRoot(), repositoryName, null));
			pages.put("pages", pagesLink);
		}

		// Conditionally add edit link
		final boolean showAdmin;
		if (GitBlit.getBoolean(Keys.web.authenticateAdminPages, true)) {
			boolean allowAdmin = GitBlit.getBoolean(Keys.web.allowAdministration, false);
			showAdmin = allowAdmin && GitBlitWebSession.get().canAdmin();
		} else {
			showAdmin = GitBlit.getBoolean(Keys.web.allowAdministration, false);
		}
		if (showAdmin
				|| GitBlitWebSession.get().isLoggedIn()
				&& (model.owner != null && model.owner.equalsIgnoreCase(GitBlitWebSession.get()
						.getUser().username))) {
			pages.put("edit", new PageRegistration("gb.edit", EditRepositoryPage.class, params));
		}
		return pages;
	}

	@Override
	protected void setupPage(String repositoryName, String pageName) {
		add(new LinkPanel("repositoryName", null, StringUtils.stripDotGit(repositoryName),
				SummaryPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		add(new Label("pageName", pageName).setRenderBodyOnly(true));
		if (getRepositoryModel().isBare) {
			add(new Label("workingCopy").setVisible(false));
		} else {
			Fragment fragment = new Fragment("workingCopy", "workingCopyFragment", this);
			Label lbl = new Label("workingCopy", getString("gb.workingCopy"));
			WicketUtils.setHtmlTooltip(lbl,  getString("gb.workingCopyWarning"));
			fragment.add(lbl);
			add(fragment);
		}

		super.setupPage(repositoryName, pageName);
	}

	protected void addSyndicationDiscoveryLink() {
		add(WicketUtils.syndicationDiscoveryLink(SyndicationServlet.getTitle(repositoryName,
				objectId), SyndicationServlet.asLink(getRequest()
				.getRelativePathPrefixToContextRoot(), repositoryName, objectId, 0)));
	}

	protected Repository getRepository() {
		if (r == null) {
			Repository r = GitBlit.self().getRepository(repositoryName);
			if (r == null) {
				error(getString("gb.canNotLoadRepository") + " " + repositoryName, true);
				return null;
			}
			this.r = r;
		}
		return r;
	}

	protected RepositoryModel getRepositoryModel() {
		if (m == null) {
			RepositoryModel model = GitBlit.self().getRepositoryModel(
					GitBlitWebSession.get().getUser(), repositoryName);
			if (model == null) {
				authenticationError(getString("gb.unauthorizedAccessForRepository") + " " + repositoryName);
				return null;
			}
			m = model;
		}
		return m;
	}

	protected RevCommit getCommit() {
		RevCommit commit = JGitUtils.getCommit(r, objectId);
		if (commit == null) {
			error(MessageFormat.format(getString("gb.failedToFindCommit"),
					objectId, repositoryName, getPageName()), true);
		}
		getSubmodules(commit);
		return commit;
	}
	
	private Map<String, SubmoduleModel> getSubmodules(RevCommit commit) {	
		if (submodules == null) {
			submodules = new HashMap<String, SubmoduleModel>();
			for (SubmoduleModel model : JGitUtils.getSubmodules(r, commit.getTree())) {
				submodules.put(model.path, model);
			}
		}
		return submodules;
	}
	
	protected Map<String, SubmoduleModel> getSubmodules() {
		return submodules;
	}
	
	protected SubmoduleModel getSubmodule(String path) {
		SubmoduleModel model = submodules.get(path);
		if (model == null) {
			// undefined submodule?!
			model = new SubmoduleModel(path.substring(path.lastIndexOf('/') + 1), path, path);
			model.hasSubmodule = false;
			model.gitblitPath = model.name;
			return model;
		} else {
			// extract the repository name from the clone url
			List<String> patterns = GitBlit.getStrings(Keys.git.submoduleUrlPatterns);
			String submoduleName = StringUtils.extractRepositoryPath(model.url, patterns.toArray(new String[0]));
			
			// determine the current path for constructing paths relative
			// to the current repository
			String currentPath = "";
			if (repositoryName.indexOf('/') > -1) {
				currentPath = repositoryName.substring(0, repositoryName.lastIndexOf('/') + 1);
			}

			// try to locate the submodule repository
			// prefer bare to non-bare names
			List<String> candidates = new ArrayList<String>();

			// relative
			candidates.add(currentPath + StringUtils.stripDotGit(submoduleName));
			candidates.add(candidates.get(candidates.size() - 1) + ".git");

			// relative, no subfolder
			if (submoduleName.lastIndexOf('/') > -1) {
				String name = submoduleName.substring(submoduleName.lastIndexOf('/') + 1);
				candidates.add(currentPath + StringUtils.stripDotGit(name));
				candidates.add(currentPath + candidates.get(candidates.size() - 1) + ".git");
			}

			// absolute
			candidates.add(StringUtils.stripDotGit(submoduleName));
			candidates.add(candidates.get(candidates.size() - 1) + ".git");

			// absolute, no subfolder
			if (submoduleName.lastIndexOf('/') > -1) {
				String name = submoduleName.substring(submoduleName.lastIndexOf('/') + 1);
				candidates.add(StringUtils.stripDotGit(name));
				candidates.add(candidates.get(candidates.size() - 1) + ".git");
			}

			// create a unique, ordered set of candidate paths
			Set<String> paths = new LinkedHashSet<String>(candidates);
			for (String candidate : paths) {
				if (GitBlit.self().hasRepository(candidate)) {
					model.hasSubmodule = true;
					model.gitblitPath = candidate;
					return model;
				}
			}
			
			// we do not have a copy of the submodule, but we need a path
			model.gitblitPath = candidates.get(0);
			return model;
		}		
	}

	protected String getShortObjectId(String objectId) {
		return objectId.substring(0, 8);
	}

	protected void addRefs(Repository r, RevCommit c) {
		add(new RefsPanel("refsPanel", repositoryName, c, JGitUtils.getAllRefs(r)));
	}

	protected void addFullText(String wicketId, String text, boolean substituteRegex) {
		String html = StringUtils.escapeForHtml(text, true);
		if (substituteRegex) {
			html = GitBlit.self().processCommitMessage(repositoryName, text);
		} else {
			html = StringUtils.breakLinesForHtml(html);
		}
		add(new Label(wicketId, html).setEscapeModelStrings(false));
	}

	protected abstract String getPageName();

	protected Component createPersonPanel(String wicketId, PersonIdent identity,
			Constants.SearchType searchType) {
		String name = identity == null ? "" : identity.getName();
		String address = identity == null ? "" : identity.getEmailAddress();
		boolean showEmail = GitBlit.getBoolean(Keys.web.showEmailAddresses, false);
		if (!showEmail || StringUtils.isEmpty(name) || StringUtils.isEmpty(address)) {
			String value = name;
			if (StringUtils.isEmpty(value)) {
				if (showEmail) {
					value = address;
				} else {
					value = getString("gb.missingUsername");
				}
			}
			Fragment partial = new Fragment(wicketId, "partialPersonIdent", this);
			LinkPanel link = new LinkPanel("personName", "list", value, GitSearchPage.class,
					WicketUtils.newSearchParameter(repositoryName, objectId, value, searchType));
			setPersonSearchTooltip(link, value, searchType);
			partial.add(link);
			return partial;
		} else {
			Fragment fullPerson = new Fragment(wicketId, "fullPersonIdent", this);
			LinkPanel nameLink = new LinkPanel("personName", "list", name, GitSearchPage.class,
					WicketUtils.newSearchParameter(repositoryName, objectId, name, searchType));
			setPersonSearchTooltip(nameLink, name, searchType);
			fullPerson.add(nameLink);

			LinkPanel addressLink = new LinkPanel("personAddress", "hidden-phone list", "<" + address + ">",
					GitSearchPage.class, WicketUtils.newSearchParameter(repositoryName, objectId,
							address, searchType));
			setPersonSearchTooltip(addressLink, address, searchType);
			fullPerson.add(addressLink);
			return fullPerson;
		}
	}

	protected void setPersonSearchTooltip(Component component, String value,
			Constants.SearchType searchType) {
		if (searchType.equals(Constants.SearchType.AUTHOR)) {
			WicketUtils.setHtmlTooltip(component, getString("gb.searchForAuthor") + " " + value);
		} else if (searchType.equals(Constants.SearchType.COMMITTER)) {
			WicketUtils.setHtmlTooltip(component, getString("gb.searchForCommitter") + " " + value);
		}
	}

	protected void setChangeTypeTooltip(Component container, ChangeType type) {
		switch (type) {
		case ADD:
			WicketUtils.setHtmlTooltip(container, getString("gb.addition"));
			break;
		case COPY:
		case RENAME:
			WicketUtils.setHtmlTooltip(container, getString("gb.rename"));
			break;
		case DELETE:
			WicketUtils.setHtmlTooltip(container, getString("gb.deletion"));
			break;
		case MODIFY:
			WicketUtils.setHtmlTooltip(container, getString("gb.modification"));
			break;
		}
	}

	@Override
	protected void onBeforeRender() {
		// dispose of repository object
		if (r != null) {
			r.close();
			r = null;
		}
		// setup page header and footer
		setupPage(repositoryName, "/ " + getPageName());
		super.onBeforeRender();
	}

	protected PageParameters newRepositoryParameter() {
		return WicketUtils.newRepositoryParameter(repositoryName);
	}

	protected PageParameters newCommitParameter() {
		return WicketUtils.newObjectParameter(repositoryName, objectId);
	}

	protected PageParameters newCommitParameter(String commitId) {
		return WicketUtils.newObjectParameter(repositoryName, commitId);
	}

	private class SearchForm extends SessionlessForm<Void> implements Serializable {
		private static final long serialVersionUID = 1L;

		private final String repositoryName;

		private final IModel<String> searchBoxModel = new Model<String>("");

		private final IModel<Constants.SearchType> searchTypeModel = new Model<Constants.SearchType>(
				Constants.SearchType.COMMIT);

		public SearchForm(String id, String repositoryName) {
			super(id, RepositoryPage.this.getClass(), RepositoryPage.this.getPageParameters());
			this.repositoryName = repositoryName;
			DropDownChoice<Constants.SearchType> searchType = new DropDownChoice<Constants.SearchType>(
					"searchType", Arrays.asList(Constants.SearchType.values()));
			searchType.setModel(searchTypeModel);
			add(searchType.setVisible(GitBlit.getBoolean(Keys.web.showSearchTypeSelection, false)));
			TextField<String> searchBox = new TextField<String>("searchBox", searchBoxModel);
			add(searchBox);
		}

		void setTranslatedAttributes() {
			WicketUtils.setHtmlTooltip(get("searchType"), getString("gb.searchTypeTooltip"));
			WicketUtils.setHtmlTooltip(get("searchBox"),
					MessageFormat.format(getString("gb.searchTooltip"), repositoryName));
			WicketUtils.setInputPlaceholder(get("searchBox"), getString("gb.search"));
		}

		@Override
		public void onSubmit() {
			Constants.SearchType searchType = searchTypeModel.getObject();
			String searchString = searchBoxModel.getObject();
			if (searchString == null) {
				return;
			}
			for (Constants.SearchType type : Constants.SearchType.values()) {
				if (searchString.toLowerCase().startsWith(type.name().toLowerCase() + ":")) {
					searchType = type;
					searchString = searchString.substring(type.name().toLowerCase().length() + 1)
							.trim();
					break;
				}
			}
			Class<? extends BasePage> searchPageClass = GitSearchPage.class;
			RepositoryModel model = GitBlit.self().getRepositoryModel(repositoryName);
			if (GitBlit.getBoolean(Keys.web.allowLuceneIndexing, true)
					&& !ArrayUtils.isEmpty(model.indexedBranches)) {
				// this repository is Lucene-indexed
				searchPageClass = LuceneSearchPage.class;
			}
			// use an absolute url to workaround Wicket-Tomcat problems with
			// mounted url parameters (issue-111)
			PageParameters params = WicketUtils.newSearchParameter(repositoryName, null, searchString, searchType);
			String relativeUrl = urlFor(searchPageClass, params).toString();
			String absoluteUrl = RequestUtils.toAbsolutePath(relativeUrl);
			getRequestCycle().setRequestTarget(new RedirectRequestTarget(absoluteUrl));
		}
	}
}
