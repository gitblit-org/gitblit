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
		GitBlitServer4UITests server = new GitBlitServer4UITests();

		// filter out the baseFolder parameter
		List<String> filtered = new ArrayList<String>();
		String folder = "data";
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.equals("--baseFolder")) {
				if (i + 1 == args.length) {
					System.out.println("Invalid --baseFolder parameter!");
					System.exit(-1);
				} else if (args[i + 1] != ".") {
					folder = args[i + 1];
				}
				i = i + 1;
			} else {
				filtered.add(arg);
			}
		}

		Params.baseFolder = folder;
		Params params = new Params();
		CmdLineParser parser = new CmdLineParser(params);
		try {
			parser.parseArgument(filtered);
			if (params.help) {
				server.usage(parser, null);
			}
		} catch (CmdLineException t) {
			server.usage(parser, t);
		}

		if (params.stop) {
			server.stop(params);
		} else {
			server.start(params);
		}
	}

	@Override
	protected GitblitContext newGitblit(IStoredSettings settings, File baseFolder) {
		settings.overrideSetting(Keys.web.allowLuceneIndexing, false);
		return new GitblitContext(settings, baseFolder);
	}
}
