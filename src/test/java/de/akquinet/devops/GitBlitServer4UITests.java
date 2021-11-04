package de.akquinet.devops;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import com.gitblit.GitBlitServer;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.servlet.GitblitContext;

public class GitBlitServer4UITests extends GitBlitServer {

	public static void main(String... args) {
		GitBlitServer.main(GitBlitServer4UITests.class, args);
	}

	@Override
	protected GitblitContext newGitblit(IStoredSettings settings, File baseFolder) {
		settings.overrideSetting(Keys.web.allowLuceneIndexing, false);
		return new GitblitContext(settings, baseFolder);
	}
}
