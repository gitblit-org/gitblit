package com.gitblit.wicket.pages;

import java.util.Date;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.CompoundPropertyModel;

import com.gitblit.GitBlit;
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
		String repositoryName = WicketUtils.getRepositoryName(params);
		setupPage(new RepositoryModel(repositoryName, "", "", new Date()));
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
		form.add(new CheckBox("useTickets"));
		form.add(new CheckBox("useDocs"));
		form.add(new CheckBox("useNamedUsers"));

		add(form);
	}
}
