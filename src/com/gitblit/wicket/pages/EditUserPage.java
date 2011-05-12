package com.gitblit.wicket.pages;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.extensions.markup.html.form.palette.Palette;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.util.CollectionModel;
import org.apache.wicket.model.util.ListModel;
import org.eclipse.jetty.http.security.Credential.MD5;

import com.gitblit.GitBlit;
import com.gitblit.GitBlitException;
import com.gitblit.wicket.AdminPage;
import com.gitblit.wicket.BasePage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.UserModel;

@AdminPage
public class EditUserPage extends BasePage {

	private final boolean isCreate;

	public EditUserPage() {
		// create constructor
		super();
		isCreate = true;
		setupPage(new UserModel(""));
	}

	public EditUserPage(PageParameters params) {
		// edit constructor
		super(params);
		isCreate = false;
		String name = WicketUtils.getUsername(params);
		UserModel model = GitBlit.self().getUser(name);
		setupPage(model);
	}

	protected void setupPage(final UserModel userModel) {
		if (isCreate) {
			super.setupPage("", getString("gb.newUser"));
		} else {
			super.setupPage("", getString("gb.edit"));
		}
		final Model<String> confirmPassword = new Model<String>();
		CompoundPropertyModel<UserModel> model = new CompoundPropertyModel<UserModel>(userModel);

		List<String> repos = GitBlit.self().getRepositoryList();
		repos.add(0, "*"); // all repositories wildcard
		final Palette<String> repositories = new Palette<String>("repositories", new ListModel<String>(userModel.getRepositories()), new CollectionModel<String>(repos), new ChoiceRenderer<String>("", ""), 10, false);		
		Form<UserModel> form = new Form<UserModel>("editForm", model) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit() {
				if (!userModel.getPassword().equals(confirmPassword.getObject())) {
					error("Passwords do not match!");
					return;
				}
				userModel.setPassword(MD5.digest(userModel.getPassword()));
				
				Iterator<String> selectedRepositories = repositories.getSelectedChoices();
				List<String> repos = new ArrayList<String>();
				while (selectedRepositories.hasNext()) {
					repos.add(selectedRepositories.next());
				}
				userModel.setRepositories(repos);
				try {
					GitBlit.self().editUserModel(userModel, isCreate);
				} catch (GitBlitException e) {
					error(e.getMessage());
					return;
				}
				setRedirect(true);
				setResponsePage(EditUserPage.class);
			}
		};

		// field names reflective match UserModel fields
		form.add(new TextField<String>("username").setEnabled(isCreate));
		form.add(new PasswordTextField("password"));
		form.add(new PasswordTextField("confirmPassword", confirmPassword));
		form.add(new CheckBox("canAdmin"));
		form.add(repositories);
		add(form);
	}
}
