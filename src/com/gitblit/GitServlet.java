/*
 * Copyright 2011 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit;

import groovy.lang.Binding;
import groovy.util.GroovyScriptEngine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.http.server.resolver.DefaultReceivePackFactory;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ClientLogger;
import com.gitblit.utils.HttpUtils;
import com.gitblit.utils.StringUtils;

/**
 * The GitServlet exists to force configuration of the JGit GitServlet based on
 * the Gitblit settings from either gitblit.properties or from context
 * parameters in the web.xml file.
 * 
 * It also implements and registers the Groovy hook mechanism.
 * 
 * Access to this servlet is protected by the GitFilter.
 * 
 * @author James Moger
 * 
 */
public class GitServlet extends org.eclipse.jgit.http.server.GitServlet {

	private static final long serialVersionUID = 1L;

	private GroovyScriptEngine gse;

	private File groovyDir;

	@Override
	public void init(ServletConfig config) throws ServletException {
		groovyDir = GitBlit.getGroovyScriptsFolder();
		try {
			// set Grape root
			File grapeRoot = new File(GitBlit.getString(Keys.groovy.grapeFolder, "groovy/grape")).getAbsoluteFile();
			grapeRoot.mkdirs();
			System.setProperty("grape.root", grapeRoot.getAbsolutePath());
			
			gse = new GroovyScriptEngine(groovyDir.getAbsolutePath());			
		} catch (IOException e) {
			throw new ServletException("Failed to instantiate Groovy Script Engine!", e);
		}

		// set the Gitblit receive hook
		setReceivePackFactory(new DefaultReceivePackFactory() {
			@Override
			public ReceivePack create(HttpServletRequest req, Repository db)
					throws ServiceNotEnabledException, ServiceNotAuthorizedException {
				
				// determine repository name from request
				String repositoryName = req.getPathInfo().substring(1);
				repositoryName = GitFilter.getRepositoryName(repositoryName);
				
				GitblitReceiveHook hook = new GitblitReceiveHook();
				hook.repositoryName = repositoryName;
				hook.gitblitUrl = HttpUtils.getGitblitURL(req);

				ReceivePack rp = super.create(req, db);
				rp.setPreReceiveHook(hook);
				rp.setPostReceiveHook(hook);
				return rp;
			}
		});
		super.init(new GitblitServletConfig(config));
	}

	/**
	 * Transitional wrapper class to configure the JGit 1.2 GitFilter. This
	 * GitServlet will probably be replaced by a GitFilter so that Gitblit can
	 * serve Git repositories on the root URL and not a /git sub-url.
	 * 
	 * @author James Moger
	 * 
	 */
	private class GitblitServletConfig implements ServletConfig {
		final ServletConfig config;

		GitblitServletConfig(ServletConfig config) {
			this.config = config;
		}

		@Override
		public String getServletName() {
			return config.getServletName();
		}

		@Override
		public ServletContext getServletContext() {
			return config.getServletContext();
		}

		@Override
		public String getInitParameter(String name) {
			if (name.equals("base-path")) {
				return GitBlit.getRepositoriesFolder().getAbsolutePath();
			} else if (name.equals("export-all")) {
				return "1";
			}
			return config.getInitParameter(name);
		}

		@Override
		public Enumeration<String> getInitParameterNames() {
			return config.getInitParameterNames();
		}
	}

	/**
	 * The Gitblit receive hook allows for special processing on push events.
	 * That might include rejecting writes to specific branches or executing a
	 * script.
	 * 
	 * @author James Moger
	 * 
	 */
	private class GitblitReceiveHook implements PreReceiveHook, PostReceiveHook {

		protected final Logger logger = LoggerFactory.getLogger(GitblitReceiveHook.class);

		protected String repositoryName;
		
		protected String gitblitUrl;

		/**
		 * Instrumentation point where the incoming push event has been parsed,
		 * validated, objects created BUT refs have not been updated. You might
		 * use this to enforce a branch-write permissions model.
		 */
		@Override
		public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
			RepositoryModel repository = GitBlit.self().getRepositoryModel(repositoryName);
			Set<String> scripts = new LinkedHashSet<String>();
			scripts.addAll(GitBlit.self().getPreReceiveScriptsInherited(repository));
			scripts.addAll(repository.preReceiveScripts);
			UserModel user = getUserModel(rp);
			runGroovy(repository, user, commands, rp, scripts);
			for (ReceiveCommand cmd : commands) {
				if (!Result.NOT_ATTEMPTED.equals(cmd.getResult())) {
					logger.warn(MessageFormat.format("{0} {1} because \"{2}\"", cmd.getNewId()
							.getName(), cmd.getResult(), cmd.getMessage()));
				}
			}

			// Experimental
			// runNativeScript(rp, "hooks/pre-receive", commands);
		}

		/**
		 * Instrumentation point where the incoming push has been applied to the
		 * repository. This is the point where we would trigger a Jenkins build
		 * or send an email.
		 */
		@Override
		public void onPostReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
			if (commands.size() == 0) {
				logger.info("skipping post-receive hooks, no refs created, updated, or removed");
				return;
			}
			RepositoryModel repository = GitBlit.self().getRepositoryModel(repositoryName);
			Set<String> scripts = new LinkedHashSet<String>();
			scripts.addAll(GitBlit.self().getPostReceiveScriptsInherited(repository));
			scripts.addAll(repository.postReceiveScripts);
			UserModel user = getUserModel(rp);
			runGroovy(repository, user, commands, rp, scripts);

			// Experimental
			// runNativeScript(rp, "hooks/post-receive", commands);
		}

		/**
		 * Returns the UserModel for the user pushing the changes.
		 * 
		 * @param rp
		 * @return a UserModel
		 */
		protected UserModel getUserModel(ReceivePack rp) {
			PersonIdent person = rp.getRefLogIdent();
			UserModel user = GitBlit.self().getUserModel(person.getName());
			if (user == null) {
				// anonymous push, create a temporary usermodel
				user = new UserModel(person.getName());
				user.isAuthenticated = false;
			}
			return user;
		}

		/**
		 * Runs the specified Groovy hook scripts.
		 * 
		 * @param repository
		 * @param user
		 * @param commands
		 * @param scripts
		 */
		protected void runGroovy(RepositoryModel repository, UserModel user,
				Collection<ReceiveCommand> commands, ReceivePack rp, Set<String> scripts) {
			if (scripts == null || scripts.size() == 0) {
				// no Groovy scripts to execute
				return;
			}

			Binding binding = new Binding();
			binding.setVariable("gitblit", GitBlit.self());
			binding.setVariable("repository", repository);
			binding.setVariable("user", user);
			binding.setVariable("commands", commands);
			binding.setVariable("url", gitblitUrl);
			binding.setVariable("logger", logger);
			binding.setVariable("clientLogger", new ClientLogger(rp));
			for (String script : scripts) {
				if (StringUtils.isEmpty(script)) {
					continue;
				}
				// allow script to be specified without .groovy extension
				// this is easier to read in the settings
				File file = new File(groovyDir, script);
				if (!file.exists() && !script.toLowerCase().endsWith(".groovy")) {
					file = new File(groovyDir, script + ".groovy");
					if (file.exists()) {
						script = file.getName();
					}
				}
				try {
					Object result = gse.run(script, binding);
					if (result instanceof Boolean) {
						if (!((Boolean) result)) {
							logger.error(MessageFormat.format(
									"Groovy script {0} has failed!  Hook scripts aborted.", script));
							break;
						}
					}
				} catch (Exception e) {
					logger.error(
							MessageFormat.format("Failed to execute Groovy script {0}", script), e);
				}
			}
		}

		/**
		 * Runs the native push hook script.
		 * 
		 * http://book.git-scm.com/5_git_hooks.html
		 * http://longair.net/blog/2011/04/09/missing-git-hooks-documentation/
		 * 
		 * @param rp
		 * @param script
		 * @param commands
		 */
		@SuppressWarnings("unused")
		protected void runNativeScript(ReceivePack rp, String script,
				Collection<ReceiveCommand> commands) {

			Repository repository = rp.getRepository();
			File scriptFile = new File(repository.getDirectory(), script);

			int resultCode = 0;
			if (scriptFile.exists()) {
				try {
					logger.debug("executing " + scriptFile);
					Process process = Runtime.getRuntime().exec(scriptFile.getAbsolutePath(), null,
							repository.getDirectory());
					BufferedReader reader = new BufferedReader(new InputStreamReader(
							process.getInputStream()));
					BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
							process.getOutputStream()));
					for (ReceiveCommand command : commands) {
						switch (command.getType()) {
						case UPDATE:
							// updating a ref
							writer.append(MessageFormat.format("{0} {1} {2}\n", command.getOldId()
									.getName(), command.getNewId().getName(), command.getRefName()));
							break;
						case CREATE:
							// new ref
							// oldrev hard-coded to 40? weird.
							writer.append(MessageFormat.format("40 {0} {1}\n", command.getNewId()
									.getName(), command.getRefName()));
							break;
						}
					}
					resultCode = process.waitFor();

					// read and buffer stdin
					// this is supposed to be piped back to the git client.
					// not sure how to do that right now.
					StringBuilder sb = new StringBuilder();
					String line = null;
					while ((line = reader.readLine()) != null) {
						sb.append(line).append('\n');
					}
					logger.debug(sb.toString());
				} catch (Throwable e) {
					resultCode = -1;
					logger.error(
							MessageFormat.format("Failed to execute {0}",
									scriptFile.getAbsolutePath()), e);
				}
			}

			// reject push
			if (resultCode != 0) {
				for (ReceiveCommand command : commands) {
					command.setResult(Result.REJECTED_OTHER_REASON, MessageFormat.format(
							"Native script {0} rejected push or failed",
							scriptFile.getAbsolutePath()));
				}
			}
		}
	}
}
