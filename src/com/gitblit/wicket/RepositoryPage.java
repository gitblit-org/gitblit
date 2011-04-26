package com.gitblit.wicket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Fragment;
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
import com.gitblit.wicket.pages.RepositoriesPage;
import com.gitblit.wicket.pages.SearchPage;
import com.gitblit.wicket.panels.PageLinksPanel;
import com.gitblit.wicket.panels.RefsPanel;

public abstract class RepositoryPage extends BasePage {

	protected final String repositoryName;
	protected final String objectId;
	protected String description;

	private transient Repository r = null;

	private final Logger logger = LoggerFactory.getLogger(RepositoryPage.class);
	
	public RepositoryPage(PageParameters params) {
		super(params);
		if (!params.containsKey("r")) {
			error("Repository not specified!");
			redirectToInterceptPage(new RepositoriesPage());
		}
		repositoryName = WicketUtils.getRepositoryName(params);
		objectId = WicketUtils.getObject(params);

		Repository r = getRepository();

		// setup the page links and disable this page's link
		PageLinksPanel pageLinks = new PageLinksPanel("pageLinks", r, repositoryName, getPageName());
		add(pageLinks);
		pageLinks.disablePageLink(getPageName());

		setStatelessHint(true);
	}

	protected Repository getRepository() {
		if (r == null) {
			Repository r = GitBlit.self().getRepository(repositoryName);
			if (r == null) {
				error("Can not load repository " + repositoryName);
				redirectToInterceptPage(new RepositoriesPage());
				return null;
			}
			description = JGitUtils.getRepositoryDescription(r);
			this.r = r;
		}
		return r;
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
}
