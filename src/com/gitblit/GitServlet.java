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

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.http.server.resolver.DefaultReceivePackFactory;
import org.eclipse.jgit.http.server.resolver.DefaultUploadPackFactory;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.RefFilter;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ClientLogger;
import com.gitblit.utils.HttpUtils;
import com.gitblit.utils.IssueUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.PushLogUtils;
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
			File grapeRoot = GitBlit.getFileOrFolder(Keys.groovy.grapeFolder, "${baseFolder}/groovy/grape").getAbsoluteFile();
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

				// determine pushing user
				PersonIdent person = rp.getRefLogIdent();
				UserModel user = GitBlit.self().getUserModel(person.getName());
				if (user == null) {
					// anonymous push, create a temporary usermodel
					user = new UserModel(person.getName());
				}
				
				// enforce advanced ref permissions
				RepositoryModel repository = GitBlit.self().getRepositoryModel(repositoryName);
				rp.setAllowCreates(user.canCreateRef(repository));
				rp.setAllowDeletes(user.canDeleteRef(repository));
				rp.setAllowNonFastForwards(user.canRewindRef(repository));
				
				if (repository.isFrozen) {
					throw new ServiceNotEnabledException();
				}
				
				return rp;
			}
		});
		
		// override the default upload pack to exclude gitblit refs
		setUploadPackFactory(new DefaultUploadPackFactory() {
			@Override
			public UploadPack create(final HttpServletRequest req, final Repository db)
					throws ServiceNotEnabledException, ServiceNotAuthorizedException {
				UploadPack up = super.create(req, db);
				RefFilter refFilter = new RefFilter() {
					@Override
					public Map<String, Ref> filter(Map<String, Ref> refs) {
						// admin accounts can access all refs 
						UserModel user = GitBlit.self().authenticate(req);
						if (user == null) {
							user = UserModel.ANONYMOUS;
						}
						if (user.canAdmin()) {
							return refs;
						}

						// normal users can not clone gitblit refs
						refs.remove(IssueUtils.GB_ISSUES);
						refs.remove(PushLogUtils.GB_PUSHES);
						return refs;
					}
				};
				up.setRefFilter(refFilter);
				return up;
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
			UserModel user = getUserModel(rp);
			
			if (repository.accessRestriction.atLeast(AccessRestrictionType.PUSH) && repository.verifyCommitter) {
				if (StringUtils.isEmpty(user.emailAddress)) {
					// emit warning if user does not have an email address 
					logger.warn(MessageFormat.format("Consider setting an email address for {0} ({1}) to improve committer verification.", user.getDisplayName(), user.username));
				}
				
				// Optionally enforce that the committer of the left parent chain
				// match the account being used to push the commits.
				// 
				// This requires all merge commits are executed with the "--no-ff"
				// option to force a merge commit even if fast-forward is possible.
				// This ensures that the chain of left parents has the commit
				// identity of the merging user.
				for (ReceiveCommand cmd : commands) {
					try {
						List<RevCommit> commits = JGitUtils.getRevLog(rp.getRepository(), cmd.getOldId().name(), cmd.getNewId().name());
						for (RevCommit commit : commits) {
							PersonIdent committer = commit.getCommitterIdent();
							if (!user.is(committer.getName(), committer.getEmailAddress())) {
								String reason;
								if (StringUtils.isEmpty(user.emailAddress)) {
									// account does not have en email address
									reason = MessageFormat.format("{0} by {1} <{2}> was not committed by {3} ({4})", commit.getId().name(), committer.getName(), StringUtils.isEmpty(committer.getEmailAddress()) ? "?":committer.getEmailAddress(), user.getDisplayName(), user.username);
								} else {
									// account has an email address
									reason = MessageFormat.format("{0} by {1} <{2}> was not committed by {3} ({4}) <{5}>", commit.getId().name(), committer.getName(), StringUtils.isEmpty(committer.getEmailAddress()) ? "?":committer.getEmailAddress(), user.getDisplayName(), user.username, user.emailAddress);
								}
								cmd.setResult(Result.REJECTED_OTHER_REASON, reason);
								break;
							}
						}
					} catch (Exception e) {
						logger.error("Failed to verify commits were made by pushing user", e);
					}
				}
			}
			
			Set<String> scripts = new LinkedHashSet<String>();
			scripts.addAll(GitBlit.self().getPreReceiveScriptsInherited(repository));
			scripts.addAll(repository.preReceiveScripts);
			runGroovy(repository, user, commands, rp, scripts);
			for (ReceiveCommand cmd : commands) {
				if (!Result.NOT_ATTEMPTED.equals(cmd.getResult())) {
					logger.warn(MessageFormat.format("{0} {1} because \"{2}\"", cmd.getNewId()
							.getName(), cmd.getResult(), cmd.getMessage()));
				}
			}
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

			UserModel user = getUserModel(rp);
			RepositoryModel repository = GitBlit.self().getRepositoryModel(repositoryName);
			
			if (repository.useIncrementalRevisionNumbers) {
				List<ReceiveCommand> allCommands = rp.getAllCommands();
				String cmds = "";
				for (ReceiveCommand receiveCommand : allCommands) {
					cmds += receiveCommand.getType() + "_"
							+ receiveCommand.getResult() + "_"
							+ receiveCommand.getMessage() + ", ";
					if (receiveCommand.getType().equals(
							ReceiveCommand.Type.UPDATE)
							&& receiveCommand.getResult().equals(
									ReceiveCommand.Result.OK)) {
						// if type=update and update was ok, autotag
						String objectId = receiveCommand.getNewId().toString()
								.replace("AnyObjectId[", "").replace("]", "");
						boolean result = JGitUtils
								.createIncrementalRevisionTag(
										rp.getRepository(), objectId);						
					}
				}				
			}
			
			// log ref changes
			for (ReceiveCommand cmd : commands) {
				if (Result.OK.equals(cmd.getResult())) {
					// add some logging for important ref changes
					switch (cmd.getType()) {
					case DELETE:
						logger.info(MessageFormat.format("{0} DELETED {1} in {2} ({3})", user.username, cmd.getRefName(), repository.name, cmd.getOldId().name()));
						break;
					case CREATE:
						logger.info(MessageFormat.format("{0} CREATED {1} in {2}", user.username, cmd.getRefName(), repository.name));
						break;
					case UPDATE_NONFASTFORWARD:
						logger.info(MessageFormat.format("{0} UPDATED NON-FAST-FORWARD {1} in {2} (from {3} to {4})", user.username, cmd.getRefName(), repository.name, cmd.getOldId().name(), cmd.getNewId().name()));
						break;
					default:
						break;
					}
				}
			}

			// update push log
			try {
				PushLogUtils.updatePushLog(user, rp.getRepository(), commands);
				logger.info(MessageFormat.format("{0} push log updated", repository.name));
			} catch (Exception e) {
				logger.error(MessageFormat.format("Failed to update {0} pushlog", repository.name), e);
			}
			
			// run Groovy hook scripts 
			Set<String> scripts = new LinkedHashSet<String>();
			scripts.addAll(GitBlit.self().getPostReceiveScriptsInherited(repository));
			scripts.addAll(repository.postReceiveScripts);
			runGroovy(repository, user, commands, rp, scripts);
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
			binding.setVariable("receivePack", rp);
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
	}
}
