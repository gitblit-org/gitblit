package com.gitblit.wicket.pages;

import java.util.Arrays;
import java.util.Date;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.CompoundPropertyModel;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.GitBlit;
import com.gitblit.GitBlitException;
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
		RepositoryModel model = GitBlit.self().getRepositoryModel(name);
		setupPage(model);
	}

	protected void setupPage(final RepositoryModel repositoryModel) {
		if (isCreate) {
			super.setupPage("", getString("gb.newRepository"));
		} else {
			super.setupPage("", getString("gb.edit") + " " + repositoryModel.name);
		}
		CompoundPropertyModel<RepositoryModel> model = new CompoundPropertyModel<RepositoryModel>(repositoryModel);
		Form<RepositoryModel> form = new Form<RepositoryModel>("editForm", model) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit() {
				try {
					GitBlit.self().editRepositoryModel(repositoryModel, isCreate);
				} catch (GitBlitException e) {
					error(e.getMessage());
					return;
				}
				setRedirect(true);
				setResponsePage(RepositoriesPage.class);
			}
		};

		// field names reflective match RepositoryModel fields
		form.add(new TextField<String>("name").setEnabled(isCreate));
		form.add(new TextField<String>("description"));
		form.add(new TextField<String>("owner"));
		form.add(new DropDownChoice<AccessRestrictionType>("accessRestriction", Arrays.asList(AccessRestrictionType.values())));
		form.add(new CheckBox("useTickets"));
		form.add(new CheckBox("useDocs"));
		form.add(new CheckBox("showRemoteBranches"));

		add(form);
	}
}
