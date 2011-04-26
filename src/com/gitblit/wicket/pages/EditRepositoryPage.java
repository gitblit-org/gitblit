package com.gitblit.wicket.pages;

import java.util.Date;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.CompoundPropertyModel;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.GitBlit;
import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.AdminPage;
import com.gitblit.wicket.BasePage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.RepositoryModel;

@AdminPage
public class EditRepositoryPage extends BasePage {

	private final boolean isCreate;

	public EditRepositoryPage() {
		// create constructor
		super();
		isCreate = true;
		setupPage(new RepositoryModel("", "", "", new Date()));
	}

	public EditRepositoryPage(PageParameters params) {
		// edit constructor
		super(params);
		isCreate = false;
		String name = WicketUtils.getRepositoryName(params);
		Repository r = GitBlit.self().getRepository(name);
		String description = JGitUtils.getRepositoryDescription(r);
		String owner = JGitUtils.getRepositoryOwner(r);
		String group = JGitUtils.getRepositoryGroup(r);
		RepositoryModel model = new RepositoryModel(name, description, owner, new Date());
		model.group = group;
		model.useTickets = JGitUtils.getRepositoryUseTickets(r);
		model.useDocs = JGitUtils.getRepositoryUseDocs(r);
		model.useRestrictedAccess = JGitUtils.getRepositoryRestrictedAccess(r);
		setupPage(model);
	}

	protected void setupPage(final RepositoryModel repository) {
		if (isCreate) {
			super.setupPage("", getString("gb.newRepository"));
		} else {
			super.setupPage("", getString("gb.edit"));
		}
		CompoundPropertyModel<RepositoryModel> model = new CompoundPropertyModel<RepositoryModel>(repository);
		Form<RepositoryModel> form = new Form<RepositoryModel>("editForm", model) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit() {
				GitBlit.self().editRepository(repository, isCreate);
				setRedirect(true);
				setResponsePage(RepositoriesPage.class);
			}
		};
		form.add(new TextField<String>("name").setEnabled(isCreate));
		form.add(new TextField<String>("description"));
		form.add(new TextField<String>("owner"));
		form.add(new TextField<String>("group"));
		form.add(new CheckBox("useTickets"));
		form.add(new CheckBox("useDocs"));

		add(form);
	}
}
