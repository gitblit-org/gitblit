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
package com.gitblit.git;

import groovy.lang.Binding;
import groovy.util.GroovyScriptEngine;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.eclipse.jgit.transport.ReceivePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.client.Translation;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.ClientLogger;
import com.gitblit.utils.CommitCache;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.RefLogUtils;
import com.gitblit.utils.StringUtils;

/**
 * The Gitblit receive hook allows for special processing on push events.
 * That might include rejecting writes to specific branches or executing a
 * script.
 * 
 * @author James Moger
 * 
 */
public class ReceiveHook implements PreReceiveHook, PostReceiveHook {

	protected final Logger logger = LoggerFactory.getLogger(ReceiveHook.class);

	protected UserModel user;
	
	protected RepositoryModel repository;

	protected String gitblitUrl;

	private GroovyScriptEngine gse;

	private File groovyDir;

	public ReceiveHook() {
		groovyDir = GitBlit.getGroovyScriptsFolder();
		try {
			// set Grape root
			File grapeRoot = GitBlit.getFileOrFolder(Keys.groovy.grapeFolder, "${baseFolder}/groovy/grape").getAbsoluteFile();
			grapeRoot.mkdirs();
			System.setProperty("grape.root", grapeRoot.getAbsolutePath());

			gse = new GroovyScriptEngine(groovyDir.getAbsolutePath());			
		} catch (IOException e) {
			//throw new ServletException("Failed to instantiate Groovy Script Engine!", e);
		}
	}

	/**
	 * Instrumentation point where the incoming push event has been parsed,
	 * validated, objects created BUT refs have not been updated. You might
	 * use this to enforce a branch-write permissions model.
	 */
	@Override
	public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands) {
		if (repository.isFrozen) {
			// repository is frozen/readonly
			String reason = MessageFormat.format("Gitblit does not allow pushes to \"{0}\" because it is frozen!", repository.name);
			logger.warn(reason);
			for (ReceiveCommand cmd : commands) {
				cmd.setResult(Result.REJECTED_OTHER_REASON, reason);
			}
			return;
		}
		
		if (!repository.isBare) {
			// repository has a working copy
			String reason = MessageFormat.format("Gitblit does not allow pushes to \"{0}\" because it has a working copy!", repository.name);
			logger.warn(reason);
			for (ReceiveCommand cmd : commands) {
				cmd.setResult(Result.REJECTED_OTHER_REASON, reason);
			}
			return;
		}

		if (!user.canPush(repository)) {
			// user does not have push permissions
			String reason = MessageFormat.format("User \"{0}\" does not have push permissions for \"{1}\"!", user.username, repository.name);
			logger.warn(reason);
			for (ReceiveCommand cmd : commands) {
				cmd.setResult(Result.REJECTED_OTHER_REASON, reason);
			}
			return;
		}

		if (repository.accessRestriction.atLeast(AccessRestrictionType.PUSH) && repository.verifyCommitter) {
			// enforce committer verification
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
			boolean allRejected = false;
			for (ReceiveCommand cmd : commands) {
				String linearParent = null;
				try {
					List<RevCommit> commits = JGitUtils.getRevLog(rp.getRepository(), cmd.getOldId().name(), cmd.getNewId().name());
					for (RevCommit commit : commits) {
						
						if (linearParent != null) {
		            		if (!commit.getName().equals(linearParent)) {
		            			// ignore: commit is right-descendant of a merge
		            			continue;
		            		}
		            	}
						
						// update expected next commit id
						if (commit.getParentCount() == 0) {
		                	linearParent = null;
						} else {
							linearParent = commit.getParents()[0].getId().getName();
						}
						
						PersonIdent committer = commit.getCommitterIdent();
						if (!user.is(committer.getName(), committer.getEmailAddress())) {
							String reason;
							if (StringUtils.isEmpty(user.emailAddress)) {
								// account does not have an email address
								reason = MessageFormat.format("{0} by {1} <{2}> was not committed by {3} ({4})", 
										commit.getId().name(), committer.getName(), StringUtils.isEmpty(committer.getEmailAddress()) ? "?":committer.getEmailAddress(), user.getDisplayName(), user.username);
							} else {
								// account has an email address
								reason = MessageFormat.format("{0} by {1} <{2}> was not committed by {3} ({4}) <{5}>", 
										commit.getId().name(), committer.getName(), StringUtils.isEmpty(committer.getEmailAddress()) ? "?":committer.getEmailAddress(), user.getDisplayName(), user.username, user.emailAddress);
							}
							logger.warn(reason);
							cmd.setResult(Result.REJECTED_OTHER_REASON, reason);
							allRejected &= true;
							break;
						} else {
							allRejected = false;
						}
					}
				} catch (Exception e) {
					logger.error("Failed to verify commits were made by pushing user", e);
				}
			}

			if (allRejected) {
				// all ref updates rejected, abort
				return;
			}
		}
		
		// reset branch commit cache on REWIND and DELETE
		for (ReceiveCommand cmd : commands) {
			String ref = cmd.getRefName();
			if (ref.startsWith(Constants.R_HEADS)) {
				switch (cmd.getType()) {
				case UPDATE_NONFASTFORWARD:
				case DELETE:
					CommitCache.instance().clear(repository.name, ref);
					break;
				default:
					break;
				}
			}
		}

		Set<String> scripts = new LinkedHashSet<String>();
		scripts.addAll(GitBlit.self().getPreReceiveScriptsInherited(repository));
		if (!ArrayUtils.isEmpty(repository.preReceiveScripts)) {
			scripts.addAll(repository.preReceiveScripts);
		}
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
			logger.debug("skipping post-receive hooks, no refs created, updated, or removed");
			return;
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
				case UPDATE:
					logger.info(MessageFormat.format("{0} UPDATED {1} in {2} (from {3} to {4})", user.username, cmd.getRefName(), repository.name, cmd.getOldId().name(), cmd.getNewId().name()));
					break;
				case UPDATE_NONFASTFORWARD:
					logger.info(MessageFormat.format("{0} UPDATED NON-FAST-FORWARD {1} in {2} (from {3} to {4})", user.username, cmd.getRefName(), repository.name, cmd.getOldId().name(), cmd.getNewId().name()));
					break;
				default:
					break;
				}
			}
		}

		if (repository.useIncrementalPushTags) {
			// tag each pushed branch tip
			String emailAddress = user.emailAddress == null ? rp.getRefLogIdent().getEmailAddress() : user.emailAddress;
			PersonIdent userIdent = new PersonIdent(user.getDisplayName(), emailAddress);

			for (ReceiveCommand cmd : commands) {
				if (!cmd.getRefName().startsWith("refs/heads/")) {
					// only tag branch ref changes
					continue;
				}

				if (!ReceiveCommand.Type.DELETE.equals(cmd.getType())
						&& ReceiveCommand.Result.OK.equals(cmd.getResult())) {
					String objectId = cmd.getNewId().getName();
					String branch = cmd.getRefName().substring("refs/heads/".length());
					// get translation based on the server's locale setting
					String template = Translation.get("gb.incrementalPushTagMessage");
					String msg = MessageFormat.format(template, branch);
					String prefix;
					if (StringUtils.isEmpty(repository.incrementalPushTagPrefix)) {
						prefix = GitBlit.getString(Keys.git.defaultIncrementalPushTagPrefix, "r");
					} else {
						prefix = repository.incrementalPushTagPrefix;
					}

					JGitUtils.createIncrementalRevisionTag(
							rp.getRepository(),
							objectId,
							userIdent,
							prefix,
							"0",
							msg);
				}
			}				
		}

		// update push log
		try {
			RefLogUtils.updateRefLog(user, rp.getRepository(), commands);
			logger.debug(MessageFormat.format("{0} push log updated", repository.name));
		} catch (Exception e) {
			logger.error(MessageFormat.format("Failed to update {0} pushlog", repository.name), e);
		}

		// run Groovy hook scripts 
		Set<String> scripts = new LinkedHashSet<String>();
		scripts.addAll(GitBlit.self().getPostReceiveScriptsInherited(repository));
		if (!ArrayUtils.isEmpty(repository.postReceiveScripts)) {
			scripts.addAll(repository.postReceiveScripts);
		}
		runGroovy(repository, user, commands, rp, scripts);
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