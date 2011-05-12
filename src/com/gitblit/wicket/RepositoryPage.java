package com.gitblit.wicket;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.StatelessForm;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.JGitUtils.SearchType;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.models.RepositoryModel;
import com.gitblit.wicket.pages.BranchesPage;
import com.gitblit.wicket.pages.DocsPage;
import com.gitblit.wicket.pages.LogPage;
import com.gitblit.wicket.pages.RepositoriesPage;
import com.gitblit.wicket.pages.SearchPage;
import com.gitblit.wicket.pages.SummaryPage;
import com.gitblit.wicket.pages.TagsPage;
import com.gitblit.wicket.pages.TicketsPage;
import com.gitblit.wicket.pages.TreePage;
import com.gitblit.wicket.panels.RefsPanel;

public abstract class RepositoryPage extends BasePage {

	protected final String repositoryName;
	protected final String objectId;

	private transient Repository r = null;

	private RepositoryModel m = null;

	private final Logger logger = LoggerFactory.getLogger(RepositoryPage.class);

	private final Map<String, String> knownPages = new HashMap<String, String>() {

		private static final long serialVersionUID = 1L;

		{
			put("summary", "gb.summary");
			put("log", "gb.log");
			put("branches", "gb.branches");
			put("tags", "gb.tags");
			put("tree", "gb.tree");
			put("tickets", "gb.tickets");
		}
	};

	public RepositoryPage(PageParameters params) {
		super(params);
		repositoryName = WicketUtils.getRepositoryName(params);
		objectId = WicketUtils.getObject(params);

		if (StringUtils.isEmpty(repositoryName)) {
			error(MessageFormat.format("Repository not specified for {0}!", getPageName()), true);
		}

		Repository r = getRepository();
		if (r == null) {
			error(MessageFormat.format("Failed to open repository {0} for {1}!", repositoryName, getPageName()), true);
		}

		// standard page links
		add(new BookmarkablePageLink<Void>("summary", SummaryPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		add(new BookmarkablePageLink<Void>("log", LogPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		add(new BookmarkablePageLink<Void>("branches", BranchesPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		add(new BookmarkablePageLink<Void>("tags", TagsPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		add(new BookmarkablePageLink<Void>("tree", TreePage.class, WicketUtils.newRepositoryParameter(repositoryName)));

		// per-repository extra page links
		List<String> extraPageLinks = new ArrayList<String>();

		// Conditionally add tickets page
		if (getRepositoryModel().useTickets && JGitUtils.getTicketsBranch(r) != null) {
			extraPageLinks.add("tickets");
		}

		// Conditionally add docs page
		if (getRepositoryModel().useDocs) {
			extraPageLinks.add("docs");
		}

		ListDataProvider<String> extrasDp = new ListDataProvider<String>(extraPageLinks);
		DataView<String> extrasView = new DataView<String>("extra", extrasDp) {
			private static final long serialVersionUID = 1L;

			public void populateItem(final Item<String> item) {
				String extra = item.getModelObject();
				if (extra.equals("tickets")) {
					item.add(new Label("extraSeparator", " | "));
					item.add(new LinkPanel("extraLink", null, getString("gb.tickets"), TicketsPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
				} else if (extra.equals("docs")) {
					item.add(new Label("extraSeparator", " | "));
					item.add(new LinkPanel("extraLink", null, getString("gb.docs"), DocsPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
				}
			}
		};
		add(extrasView);

		// disable current page
		disablePageLink(getPageName());

		// add floating search form
		SearchForm searchForm = new SearchForm("searchForm", repositoryName);
		add(searchForm);
		searchForm.setTranslatedAttributes();

		// set stateless page preference
		setStatelessHint(true);
	}

	public void disablePageLink(String pageName) {
		for (String wicketId : knownPages.keySet()) {
			String key = knownPages.get(wicketId);
			String linkName = getString(key);
			if (linkName.equals(pageName)) {
				Component c = get(wicketId);
				if (c != null) {
					c.setEnabled(false);
				}
				break;
			}
		}
	}

	protected Repository getRepository() {
		if (r == null) {
			Repository r = GitBlit.self().getRepository(repositoryName);
			if (r == null) {
				error("Can not load repository " + repositoryName);
				redirectToInterceptPage(new RepositoriesPage());
				return null;
			}
			this.r = r;
		}
		return r;
	}

	protected RepositoryModel getRepositoryModel() {
		if (m == null) {
			RepositoryModel model = GitBlit.self().getRepositoryModel(GitBlitWebSession.get().getUser(), repositoryName);
			if (model == null) {
				error("Unauthorized access for repository " + repositoryName);
				redirectToInterceptPage(new RepositoriesPage());
				return null;				
			}
			m = model;
		}
		return m;
	}

	protected RevCommit getCommit() {
		RevCommit commit = JGitUtils.getCommit(r, objectId);
		if (commit == null) {
			error(MessageFormat.format("Failed to find commit \"{0}\" in {1} for {2} page!", objectId, repositoryName, getPageName()), true);
		}
		return commit;
	}

	protected void addRefs(Repository r, RevCommit c) {
		add(new RefsPanel("refsPanel", repositoryName, c, JGitUtils.getAllRefs(r)));
	}

	protected void addFullText(String wicketId, String text, boolean substituteRegex) {
		String html = StringUtils.breakLinesForHtml(text);
		if (substituteRegex) {
			Map<String, String> map = new HashMap<String, String>();
			// global regex keys
			if (GitBlit.self().settings().getBoolean(Keys.regex.global, false)) {
				for (String key : GitBlit.self().settings().getAllKeys(Keys.regex.global)) {
					if (!key.equals(Keys.regex.global)) {
						String subKey = key.substring(key.lastIndexOf('.') + 1);
						map.put(subKey, GitBlit.self().settings().getString(key, ""));
					}
				}
			}

			// repository-specific regex keys
			List<String> keys = GitBlit.self().settings().getAllKeys(Keys.regex._ROOT + "." + repositoryName.toLowerCase());
			for (String key : keys) {
				String subKey = key.substring(key.lastIndexOf('.') + 1);
				map.put(subKey, GitBlit.self().settings().getString(key, ""));
			}

			for (String key : map.keySet()) {
				String definition = map.get(key).trim();
				String[] chunks = definition.split("!!!");
				if (chunks.length == 2) {
					html = html.replaceAll(chunks[0], chunks[1]);
				} else {
					logger.warn(key + " improperly formatted.  Use !!! to separate match from replacement: " + definition);
				}
			}
		}
		add(new Label(wicketId, html).setEscapeModelStrings(false));
	}

	protected abstract String getPageName();

	protected Component createPersonPanel(String wicketId, PersonIdent identity, SearchType searchType) {
		boolean showEmail = GitBlit.self().settings().getBoolean(Keys.web.showEmailAddresses, false);
		if (!showEmail || StringUtils.isEmpty(identity.getName()) || StringUtils.isEmpty(identity.getEmailAddress())) {
			String value = identity.getName();
			if (StringUtils.isEmpty(value)) {
				if (showEmail) {
					value = identity.getEmailAddress();
				} else {
					value = getString("gb.missingUsername");
				}
			}
			Fragment partial = new Fragment(wicketId, "partialPersonIdent", this);
			LinkPanel link = new LinkPanel("personName", "list", value, SearchPage.class, WicketUtils.newSearchParameter(repositoryName, objectId, value, searchType));
			setPersonSearchTooltip(link, value, searchType);
			partial.add(link);
			return partial;
		} else {
			Fragment fullPerson = new Fragment(wicketId, "fullPersonIdent", this);
			LinkPanel nameLink = new LinkPanel("personName", "list", identity.getName(), SearchPage.class, WicketUtils.newSearchParameter(repositoryName, objectId, identity.getName(), searchType));
			setPersonSearchTooltip(nameLink, identity.getName(), searchType);
			fullPerson.add(nameLink);

			LinkPanel addressLink = new LinkPanel("personAddress", "list", "<" + identity.getEmailAddress() + ">", SearchPage.class, WicketUtils.newSearchParameter(repositoryName, objectId, identity.getEmailAddress(), searchType));
			setPersonSearchTooltip(addressLink, identity.getEmailAddress(), searchType);
			fullPerson.add(addressLink);
			return fullPerson;
		}
	}

	protected void setPersonSearchTooltip(Component component, String value, SearchType searchType) {
		if (searchType.equals(SearchType.AUTHOR)) {
			WicketUtils.setHtmlTooltip(component, getString("gb.searchForAuthor") + " " + value);
		} else if (searchType.equals(SearchType.COMMITTER)) {
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

	protected PageParameters newPathParameter(String path) {
		return WicketUtils.newPathParameter(repositoryName, objectId, path);
	}

	class SearchForm extends StatelessForm<Void> {
		private static final long serialVersionUID = 1L;

		private final String repositoryName;

		private final IModel<String> searchBoxModel = new Model<String>("");

		private final IModel<SearchType> searchTypeModel = new Model<SearchType>(SearchType.COMMIT);

		public SearchForm(String id, String repositoryName) {
			super(id);
			this.repositoryName = repositoryName;
			DropDownChoice<SearchType> searchType = new DropDownChoice<SearchType>("searchType", Arrays.asList(SearchType.values()));
			searchType.setModel(searchTypeModel);
			add(searchType.setVisible(GitBlit.self().settings().getBoolean(Keys.web.showSearchTypeSelection, false)));
			TextField<String> searchBox = new TextField<String>("searchBox", searchBoxModel);
			add(searchBox);
		}

		void setTranslatedAttributes() {
			WicketUtils.setHtmlTooltip(get("searchType"), getString("gb.searchTypeTooltip"));
			WicketUtils.setHtmlTooltip(get("searchBox"), getString("gb.searchTooltip"));
			WicketUtils.setInputPlaceholder(get("searchBox"), getString("gb.search"));
		}

		@Override
		public void onSubmit() {
			SearchType searchType = searchTypeModel.getObject();
			String searchString = searchBoxModel.getObject();
			for (SearchType type : SearchType.values()) {
				if (searchString.toLowerCase().startsWith(type.name().toLowerCase() + ":")) {
					searchType = type;
					searchString = searchString.substring(type.name().toLowerCase().length() + 1).trim();
					break;
				}
			}
			setResponsePage(SearchPage.class, WicketUtils.newSearchParameter(repositoryName, null, searchString, searchType));
		}
	}
}
