package de.akquinet.devops;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
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
		JCommander jc = new JCommander(params);
		try {
			jc.parse(filtered.toArray(new String[filtered.size()]));
			if (params.help) {
				server.usage(jc, null);
			}
		} catch (ParameterException t) {
			server.usage(jc, t);
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
