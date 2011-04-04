package com.gitblit;

import java.io.File;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

public class MakeRepository {

	public static void main(String... args) throws Exception {
		Params params = new Params();
		JCommander jc = new JCommander(params);
		try {
			jc.parse(args);
			if (params.help)
				jc.usage();
		} catch (ParameterException t) {
			jc.usage();
		}

		File directory = new File(params.create);
		InitCommand init = new InitCommand();
		init.setDirectory(directory);
		init.setBare(true);
		Git git = init.call();
		git.getRepository().close();
		System.out.println("GIT repository " + directory.getCanonicalPath() + " created.");
	}

	@Parameters(separators = " ")
	private static class Params {

		/*
		 * Help/Usage
		 */
		@Parameter(names = { "-h", "--help" }, description = "Show this help")
		public Boolean help = false;

		/*
		 * Repository to Create
		 */
		@Parameter(names = { "--create" }, description = "GIT Repository to Create", required = true)
		public String create = "";

	}
}
