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
import com.gitblit.models.ProjectModel;
import com.gitblit.models.RefModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.SubmoduleModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.PushLogUtils;
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

public abstract class RepositoryPage extends RootPage {

	protected final String projectName;
	protected final String repositoryName;
	protected final String objectId;
	
	private transient Repository r;

	private RepositoryModel m;

	private Map<String, SubmoduleModel> submodules;
	
	private final Map<String, PageRegistration> registeredPages;
	private boolean showAdmin;
	private boolean isOwner;
	
	public RepositoryPage(PageParameters params) {
		super(params);
		repositoryName = WicketUtils.getRepositoryName(params);
		String root =StringUtils.getFirstPathElement(repositoryName);
		if (StringUtils.isEmpty(root)) {
			projectName = GitBlit.getString(Keys.web.repositoryRootGroupName, "main");
		} else {
			projectName = root;
		}
		objectId = WicketUtils.getObject(params);
		
		if (StringUtils.isEmpty(repositoryName)) {
			error(MessageFormat.format(getString("gb.repositoryNotSpecifiedFor"), getPageName()), true);
		}

		if (!getRepositoryModel().hasCommits) {
			setResponsePage(EmptyRepositoryPage.class, params);
		}
		
		if (getRepositoryModel().isCollectingGarbage) {
			error(MessageFormat.format(getString("gb.busyCollectingGarbage"), getRepositoryModel().name), true);
		}

		if (objectId != null) {
			RefModel branch = null;
			if ((branch = JGitUtils.getBranch(getRepository(), objectId)) != null) {
				UserModel user = GitBlitWebSession.get().getUser();
				if (user == null) {
					// workaround until get().getUser() is reviewed throughout the app
					user = UserModel.ANONYMOUS;
				}
				boolean canAccess = user.canView(getRepositoryModel(),
								branch.reference.getName());
				if (!canAccess) {
					error(getString("gb.accessDenied"), true);
				}
			}
		}

		// register the available page links for this page and user
		registeredPages = registerPages();

		// standard page links
		List<PageRegistration> pages = new ArrayList<PageRegistration>(registeredPages.values());
		NavigationPanel navigationPanel = new NavigationPanel("repositoryNavPanel", getRepoNavPageClass(), pages);
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
	
	@Override
	protected Class<? extends BasePage> getRootNavPageClass() {
		return RepositoriesPage.class;
	}

	protected Class<? extends BasePage> getRepoNavPageClass() {
		return getClass();
	}
	
	private Map<String, PageRegistration> registerPages() {
		PageParameters params = null;
		if (!StringUtils.isEmpty(repositoryName)) {
			params = WicketUtils.newRepositoryParameter(repositoryName);
		}
		Map<String, PageRegistration> pages = new LinkedHashMap<String, PageRegistration>();

		Repository r = getRepository();
		RepositoryModel model = getRepositoryModel();

		// standard links
		if (PushLogUtils.getPushLogBranch(r) == null) {
			pages.put("summary", new PageRegistration("gb.summary", SummaryPage.class, params));
		} else {
			pages.put("summary", new PageRegistration("gb.summary", SummaryPage.class, params));
//			pages.put("overview", new PageRegistration("gb.overview", OverviewPage.class, params));
		}
		pages.put("commits", new PageRegistration("gb.commits", LogPage.class, params));
		pages.put("tree", new PageRegistration("gb.tree", TreePage.class, params));
		pages.put("compare", new PageRegistration("gb.compare", ComparePage.class, params));
		if (GitBlit.getBoolean(Keys.web.allowForking, true)) {
			pages.put("forks", new PageRegistration("gb.forks", ForksPage.class, params));
		}

		// conditional links
		// per-repository extra page links
		if (model.useTickets && TicgitUtils.getTicketsBranch(r) != null) {
			pages.put("tickets", new PageRegistration("gb.tickets", TicketsPage.class, params));
		}
		if (model.showReadme || model.useDocs) {
			pages.put("docs", new PageRegistration("gb.docs", DocsPage.class, params));
		}
		if (JGitUtils.getPagesBranch(r) != null) {
			OtherPageLink pagesLink = new OtherPageLink("gb.pages", PagesServlet.asLink(
					getRequest().getRelativePathPrefixToContextRoot(), repositoryName, null));
			pages.put("pages", pagesLink);
		}

		// Conditionally add edit link
		showAdmin = false;
		if (GitBlit.getBoolean(Keys.web.authenticateAdminPages, true)) {
			boolean allowAdmin = GitBlit.getBoolean(Keys.web.allowAdministration, false);
			showAdmin = allowAdmin && GitBlitWebSession.get().canAdmin();
		} else {
			showAdmin = GitBlit.getBoolean(Keys.web.allowAdministration, false);
		}
		isOwner = GitBlitWebSession.get().isLoggedIn()
				&& (model.isOwner(GitBlitWebSession.get()
						.getUsername()));
		if (showAdmin || isOwner) {
			pages.put("edit", new PageRegistration("gb.edit", EditRepositoryPage.class, params));
		}
		return pages;
	}
	
	protected boolean allowForkControls() {
		return GitBlit.getBoolean(Keys.web.allowForking, true);
	}

	@Override
	protected void setupPage(String repositoryName, String pageName) {
		String projectName = StringUtils.getFirstPathElement(repositoryName);
		ProjectModel project = GitBlit.self().getProjectModel(projectName);
		if (project.isUserProject()) {
			// user-as-project
			add(new LinkPanel("projectTitle", null, project.getDisplayName(),
					UserPage.class, WicketUtils.newUsernameParameter(project.name.substring(1))));
		} else {
			// project
			add(new LinkPanel("projectTitle", null, project.name,
					ProjectPage.class, WicketUtils.newProjectParameter(project.name)));
		}
		
		String name = StringUtils.stripDotGit(repositoryName);
		if (!StringUtils.isEmpty(projectName) && name.startsWith(projectName)) {
			name = name.substring(projectName.length() + 1);
		}
		add(new LinkPanel("repositoryName", null, name, SummaryPage.class,
				WicketUtils.newRepositoryParameter(repositoryName)));
		add(new Label("pageName", pageName).setRenderBodyOnly(true));
		
		UserModel user = GitBlitWebSession.get().getUser();
		if (user == null) {
			user = UserModel.ANONYMOUS;
		}

		// indicate origin repository
		RepositoryModel model = getRepositoryModel();
		if (StringUtils.isEmpty(model.originRepository)) {
			add(new Label("originRepository").setVisible(false));
		} else {
			RepositoryModel origin = GitBlit.self().getRepositoryModel(model.originRepository);
			if (origin == null) {
				// no origin repository
				add(new Label("originRepository").setVisible(false));
			} else if (!user.canView(origin)) {
				// show origin repository without link
				Fragment forkFrag = new Fragment("originRepository", "originFragment", this);
				forkFrag.add(new Label("originRepository", StringUtils.stripDotGit(model.originRepository)));
				add(forkFrag);
			} else {
				// link to origin repository
				Fragment forkFrag = new Fragment("originRepository", "originFragment", this);
				forkFrag.add(new LinkPanel("originRepository", null, StringUtils.stripDotGit(model.originRepository), 
						SummaryPage.class, WicketUtils.newRepositoryParameter(model.originRepository)));
				add(forkFrag);
			}
		}
		
		// fork controls
		if (!allowForkControls() || user == null || !user.isAuthenticated) {
			// must be logged-in to fork, hide all fork controls
			add(new ExternalLink("forkLink", "").setVisible(false));
			add(new ExternalLink("myForkLink", "").setVisible(false));
		} else {
			String fork = GitBlit.self().getFork(user.username, model.name);
			boolean hasFork = fork != null;
			boolean canFork = user.canFork(model);

			if (hasFork || !canFork) {
				// user not allowed to fork or fork already exists or repo forbids forking
				add(new ExternalLink("forkLink", "").setVisible(false));
				
				if (hasFork && !fork.equals(model.name)) {
					// user has fork, view my fork link
					String url = getRequestCycle().urlFor(SummaryPage.class, WicketUtils.newRepositoryParameter(fork)).toString();
					add(new ExternalLink("myForkLink", url));
				} else {
					// no fork, hide view my fork link
					add(new ExternalLink("myForkLink", "").setVisible(false));
				}
			} else if (canFork) {
				// can fork and we do not have one
				add(new ExternalLink("myForkLink", "").setVisible(false));
				String url = getRequestCycle().urlFor(ForkPage.class, WicketUtils.newRepositoryParameter(model.name)).toString();
				add(new ExternalLink("forkLink", url));
			}
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
				if (GitBlit.self().hasRepository(repositoryName, true)) {
					// has repository, but unauthorized
					authenticationError(getString("gb.unauthorizedAccessForRepository") + " " + repositoryName);
				} else {
					// does not have repository
					error(getString("gb.canNotLoadRepository") + " " + repositoryName, true);
				}
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
		return objectId.substring(0, GitBlit.getInteger(Keys.web.shortCommitIdLength, 6));
	}

	protected void addRefs(Repository r, RevCommit c) {
		add(new RefsPanel("refsPanel", repositoryName, c, JGitUtils.getAllRefs(r, getRepositoryModel().showRemoteBranches)));
	}

	protected void addFullText(String wicketId, String text, boolean substituteRegex) {
		String html = StringUtils.escapeForHtml(text, false);
		if (substituteRegex) {
			html = GitBlit.self().processCommitMessage(repositoryName, html);
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
		name = StringUtils.removeNewlines(name);
		address = StringUtils.removeNewlines(address);
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

	public boolean isShowAdmin() {
		return showAdmin;
	}
	
	public boolean isOwner() {
		return isOwner;
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
			if (StringUtils.isEmpty(searchString)) {
				// redirect to self to avoid wicket page update bug 
				PageParameters params = RepositoryPage.this.getPageParameters();
				String relativeUrl = urlFor(RepositoryPage.this.getClass(), params).toString();
				String absoluteUrl = RequestUtils.toAbsolutePath(relativeUrl);
				getRequestCycle().setRequestTarget(new RedirectRequestTarget(absoluteUrl));
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
