package com.gitblit.wicket;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.image.ContextImage;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.protocol.http.WebResponse;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;

import com.gitblit.Constants;

public class LoginPage extends WebPage {

	IModel<String> username = new Model<String>("");
	IModel<String> password = new Model<String>("");

	public LoginPage(PageParameters params) {
		super(params);

		tryAutomaticLogin();

		add(new Label("title", getServerName()));
		add(new ContextImage("logo", "gitblt2.png"));
		add(new Label("name", Constants.NAME));

		Form<Void> loginForm = new LoginForm("loginForm");
		loginForm.add(new TextField<String>("username", username));
		loginForm.add(new PasswordTextField("password", password));
		loginForm.add(new FeedbackPanel("feedback"));
		add(loginForm);
	}
	
	protected String getServerName() {
		ServletWebRequest servletWebRequest = (ServletWebRequest) getRequest();
		HttpServletRequest req = servletWebRequest.getHttpServletRequest();
		return req.getServerName();
	}

	class LoginForm extends Form<Void> {
		private static final long serialVersionUID = 1L;

		public LoginForm(String id) {
			super(id);
		}

		@Override
		public void onSubmit() {
			String username = LoginPage.this.username.getObject();
			char [] password = LoginPage.this.password.getObject().toCharArray();

			User user = GitBlitWebApp.get().authenticate(username, password);
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
			user = GitBlitWebApp.get().authenticate(cookies);
		}

		// Login the user
		loginUser(user);
	}

	private void loginUser(User user) {
		if (user != null) {
			GitBlitWebSession session = GitBlitWebSession.get();

			// Set Cookie
			WebResponse response = (WebResponse) getRequestCycle().getResponse();
			GitBlitWebApp.get().setCookie(response, user);
			
			// track user object so that we do not have to continue
			// re-authenticating on each request.
			session.setUser(user);

			// Redirect to original page OR to first available tab
			if (!continueToOriginalDestination()) {
				// Redirect to home page
				setResponsePage(session.getApplication().getHomePage());
			}
		}
	}
}
