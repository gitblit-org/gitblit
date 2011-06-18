package com.gitblit;

public class GitServlet extends org.eclipse.jgit.http.server.GitServlet {

	private static final long serialVersionUID = 1L;

	@Override
	public String getInitParameter(String name) {
		if (name.equals("base-path")) {
			return GitBlit.getString(Keys.git.repositoriesFolder, "git");
		} else if (name.equals("export-all")) {
			return "1";
		}
		return super.getInitParameter(name);
	}
}
