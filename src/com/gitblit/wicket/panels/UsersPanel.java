package com.gitblit.wicket.panels;

import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;

import com.gitblit.GitBlit;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.EditUserPage;
import com.gitblit.wicket.pages.RepositoriesPage;

public class UsersPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public UsersPanel(String wicketId, final boolean showAdmin) {
		super(wicketId);

		Fragment adminLinks = new Fragment("adminPanel", "adminLinks", this);
		adminLinks.add(new BookmarkablePageLink<Void>("newUser", EditUserPage.class));
		add(adminLinks.setVisible(showAdmin));
		
		DataView<String> usersView = new DataView<String>("userRow", new ListDataProvider<String>(GitBlit.self().getAllUsernames())) {
			private static final long serialVersionUID = 1L;
			private int counter = 0;

			public void populateItem(final Item<String> item) {
				final String entry = item.getModelObject();
				LinkPanel editLink = new LinkPanel("username", "list", entry, EditUserPage.class, WicketUtils.newUsernameParameter(entry));
				WicketUtils.setHtmlTooltip(editLink, getString("gb.edit") + " " + entry);
				item.add(editLink);
				Fragment userLinks = new Fragment("userLinks", "userAdminLinks", this);
				userLinks.add(new BookmarkablePageLink<Void>("editUser", EditUserPage.class, WicketUtils.newUsernameParameter(entry)));
				userLinks.add(new BookmarkablePageLink<Void>("deleteUser", RepositoriesPage.class, WicketUtils.newUsernameParameter(entry)).setEnabled(false));
				item.add(userLinks);

				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(usersView.setVisible(showAdmin));
	}
}
