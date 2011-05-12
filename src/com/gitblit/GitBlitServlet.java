package com.gitblit;

import java.io.IOException;
import java.text.MessageFormat;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.http.server.GitServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.wicket.models.RepositoryModel;

public class GitBlitServlet extends GitServlet {

	private static final long serialVersionUID = 1L;

	private final Logger logger = LoggerFactory.getLogger(GitBlitServlet.class);

	public GitBlitServlet() {
		super();
	}

	@Override
	protected void service(final HttpServletRequest req, final HttpServletResponse rsp) throws ServletException, IOException {
		// admins have full git access to all repositories
		if (req.isUserInRole(Constants.ADMIN_ROLE)) {
			// admins can do whatever
			super.service(req, rsp);
			return;
		}

		// try to intercept repository names for authenticated access
		String url = req.getRequestURI().substring(req.getServletPath().length());
		if (url.charAt(0) == '/' && url.length() > 1) {
			url = url.substring(1);
		}
		int forwardSlash = url.indexOf('/');
		if (forwardSlash > -1) {
			String repository = url.substring(0, forwardSlash);
			String function = url.substring(forwardSlash + 1);
			String query = req.getQueryString();
			RepositoryModel model = GitBlit.self().getRepositoryModel(repository);
			if (model != null) {
				if (model.accessRestriction.atLeast(AccessRestrictionType.PUSH)) {
					boolean authorizedUser = req.isUserInRole(repository);
					if (function.startsWith("git-receive-pack") || (query.indexOf("service=git-receive-pack") > -1)) {
						// Push request
						if (authorizedUser) {
							// clone-restricted or push-authorized
							super.service(req, rsp);
							return;
						} else {
							// user is unauthorized to push to this repository
							logger.warn(MessageFormat.format("user {0} is not authorized to push to {1} ", req.getUserPrincipal().getName(), repository));
							rsp.sendError(HttpServletResponse.SC_FORBIDDEN, MessageFormat.format("you are not authorized to push to {0} ", repository));
							return;
						}
					} else if (function.startsWith("git-upload-pack") || (query.indexOf("service=git-upload-pack") > -1)) {
						// Clone request
						boolean cloneRestricted = model.accessRestriction.atLeast(AccessRestrictionType.CLONE);
						if (!cloneRestricted || (cloneRestricted && authorizedUser)) {
							// push-restricted or clone-authorized
							super.service(req, rsp);
							return;
						} else {
							// user is unauthorized to clone this repository
							logger.warn(MessageFormat.format("user {0} is not authorized to clone {1} ", req.getUserPrincipal().getName(), repository));
							rsp.sendError(HttpServletResponse.SC_FORBIDDEN, MessageFormat.format("you are not authorized to clone {0} ", repository));
							return;
						}
					}
				}
			}
		}

		// pass-through to git servlet
		super.service(req, rsp);
	}
}
