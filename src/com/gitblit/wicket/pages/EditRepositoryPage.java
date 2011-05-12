package com.gitblit.wicket.pages;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.wicket.PageParameters;
import org.apache.wicket.extensions.markup.html.form.palette.Palette;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.util.CollectionModel;
import org.apache.wicket.model.util.ListModel;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.GitBlit;
import com.gitblit.GitBlitException;
import com.gitblit.utils.StringUtils;
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
		List<String> repositoryUsers = new ArrayList<String>();
		if (isCreate) {
			super.setupPage("", getString("gb.newRepository"));
		} else {
			super.setupPage("", getString("gb.edit"));
			if (repositoryModel.accessRestriction.exceeds(AccessRestrictionType.NONE)) {
				repositoryUsers.addAll(GitBlit.self().getRepositoryUsers(repositoryModel));
			}
		}

		final Palette<String> usersPalette = new Palette<String>("users", new ListModel<String>(repositoryUsers), new CollectionModel<String>(GitBlit.self().getAllUsernames()), new ChoiceRenderer<String>("", ""), 10, false);
		CompoundPropertyModel<RepositoryModel> model = new CompoundPropertyModel<RepositoryModel>(repositoryModel);
		Form<RepositoryModel> form = new Form<RepositoryModel>("editForm", model) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit() {
				try {
					// confirm a repository name was entered
					if (StringUtils.isEmpty(repositoryModel.name)) {
						error("Please set repository name!");
						return;
					}

					// automatically convert backslashes to forward slashes
					repositoryModel.name = repositoryModel.name.replace('\\', '/');

					// confirm valid characters in repository name
					char[] validChars = { '/', '.', '_', '-' };
					for (char c : repositoryModel.name.toCharArray()) {
						if (!Character.isLetterOrDigit(c)) {
							boolean ok = false;
							for (char vc : validChars) {
								ok |= c == vc;
							}
							if (!ok) {
								error(MessageFormat.format("Illegal character '{0}' in repository name!", c));
								return;
							}
						}
					}

					// confirm access restriction selection
					if (repositoryModel.accessRestriction == null) {
						error("Please select access restriction!");
						return;
					}
					
					// save the repository
					GitBlit.self().editRepositoryModel(repositoryModel, isCreate);
					
					// save the repository access list
					if (repositoryModel.accessRestriction.exceeds(AccessRestrictionType.NONE)) {
						Iterator<String> users = usersPalette.getSelectedChoices();
						List<String> repositoryUsers = new ArrayList<String>();
						while (users.hasNext()) {
							repositoryUsers.add(users.next());
						}
						GitBlit.self().setRepositoryUsers(repositoryModel, repositoryUsers);
					}
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
		form.add(new DropDownChoice<AccessRestrictionType>("accessRestriction", Arrays.asList(AccessRestrictionType.values()), new AccessRestrictionRenderer()));
		form.add(new CheckBox("useTickets"));
		form.add(new CheckBox("useDocs"));
		form.add(new CheckBox("showRemoteBranches"));
		form.add(usersPalette);

		add(form);
	}

	private class AccessRestrictionRenderer implements IChoiceRenderer<AccessRestrictionType> {

		private static final long serialVersionUID = 1L;

		private final Map<AccessRestrictionType, String> map;

		public AccessRestrictionRenderer() {
			map = getAccessRestrictions();
		}

		@Override
		public String getDisplayValue(AccessRestrictionType type) {
			return map.get(type);
		}

		@Override
		public String getIdValue(AccessRestrictionType type, int index) {
			return Integer.toString(index);
		}
	}
}
