package com.gitblit.wicket;

import javax.servlet.http.Cookie;

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
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.protocol.http.WebResponse;

import com.gitblit.Constants;
import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.wicket.models.User;

public class LoginPage extends WebPage {

	IModel<String> username = new Model<String>("");
	IModel<String> password = new Model<String>("");

	public LoginPage(PageParameters params) {
		super(params);

		tryAutomaticLogin();

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
			
			tryAutomaticLogin();
		}

		@Override
		public void onSubmit() {
			String username = LoginPage.this.username.getObject();
			char[] password = LoginPage.this.password.getObject().toCharArray();

			User user = GitBlit.self().authenticate(username, password);
			if (user == null)
				error("Invalid username or password!");
			else
				loginUser(user);
		}
	}

	private void tryAutomaticLogin() {
		User user = null;

		// Grab cookie from Browser Session
		Cookie[] cookies = ((WebRequest) getRequestCycle().getRequest()).getCookies();
		if (cookies != null && cookies.length > 0) {
			user = GitBlit.self().authenticate(cookies);
		}

		// Login the user
		loginUser(user);
	}

	private void loginUser(User user) {
		if (user != null) {
			// Set the user into the session
			GitBlitWebSession.get().setUser(user);

			// Set Cookie
			WebResponse response = (WebResponse) getRequestCycle().getResponse();
			GitBlit.self().setCookie(response, user);

			if (!continueToOriginalDestination()) {
				// Redirect to home page
				setResponsePage(getApplication().getHomePage());
			}
		}
	}
}
