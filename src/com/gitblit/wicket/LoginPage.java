package com.gitblit.wicket;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.StatelessForm;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import com.gitblit.Constants;
import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.wicket.models.UserModel;

public class LoginPage extends WebPage {

	IModel<String> username = new Model<String>("");
	IModel<String> password = new Model<String>("");

	public LoginPage(PageParameters params) {
		super(params);

		add(new Label("title", GitBlit.self().settings().getString(Keys.web.siteName, Constants.NAME)));
		add(new Label("name", Constants.NAME));

		Form<Void> loginForm = new LoginForm("loginForm");
		loginForm.add(new TextField<String>("username", username));
		loginForm.add(new PasswordTextField("password", password));
		loginForm.add(new FeedbackPanel("feedback"));
		add(loginForm);
	}

	class LoginForm extends StatelessForm<Void> {
		private static final long serialVersionUID = 1L;

		public LoginForm(String id) {
			super(id);

			// If we are already logged in because user directly accessed
			// the login url, redirect to the home page
			if (GitBlitWebSession.get().isLoggedIn()) {
				setRedirect(true);
				setResponsePage(getApplication().getHomePage());
			}
		}

		@Override
		public void onSubmit() {
			String username = LoginPage.this.username.getObject();
			char[] password = LoginPage.this.password.getObject().toCharArray();

			UserModel user = GitBlit.self().authenticate(username, password);
			if (user == null)
				error("Invalid username or password!");
			else
				loginUser(user);
		}
	}
	
	private void loginUser(UserModel user) {
		if (user != null) {
			// Set the user into the session
			GitBlitWebSession.get().setUser(user);

			if (!continueToOriginalDestination()) {
				// Redirect to home page
				setResponsePage(getApplication().getHomePage());
			}
		}
	}
}
