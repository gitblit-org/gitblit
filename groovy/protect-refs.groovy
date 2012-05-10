/*
 * Copyright 2012 Philip L. McMahon.
 *
 * Derived from blockpush.groovy, copyright 2011 gitblit.com.
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
import com.gitblit.GitBlit
import com.gitblit.models.RepositoryModel
import com.gitblit.models.UserModel

import org.eclipse.jgit.transport.ReceiveCommand
import org.eclipse.jgit.transport.ReceiveCommand.Result
import org.slf4j.Logger

/**
 * Sample Gitblit Pre-Receive Hook: protect-refs
 * 
 * This script provides basic authorization of receive command types for a list
 * of known ref patterns. Command types and unmatched ref patterns will be
 * ignored, meaning this script has an "allow by default" policy.
 *
 * This script works best when a repository requires authentication on push, but
 * can be used to enforce fast-forward commits or prohibit ref deletion by
 * setting the *authorizedTeams* variable to an empty list and adding a ".+"
 * entry to the *protectedRefs* list.
 *
 * The Pre-Receive hook is executed after an incoming push has been parsed,
 * validated, and objects have been written but BEFORE the refs are updated.
 * This is the appropriate point to block a push for some reason.
 *
 * This script is only executed when pushing to *Gitblit*, not to other Git
 * tooling you may be using.
 * 
 * If this script is specified in *groovy.preReceiveScripts* of gitblit.properties
 * or web.xml then it will be executed by any repository when it receives a
 * push.  If you choose to share your script then you may have to consider
 * tailoring control-flow based on repository access restrictions.
 * 
 * Scripts may also be specified per-repository in the repository settings page.
 * Shared scripts will be excluded from this list of available scripts.
 *
 * This script is dynamically reloaded and it is executed within it's own
 * exception handler so it will not crash another script nor crash Gitblit.
 * 
 * This script may reject one or more commands, but will never return false.
 * Subsequent scripts, if any, will always be invoked.
 *
 * Bound Variables:
 *  gitblit			Gitblit Server	 			com.gitblit.GitBlit
 *  repository		Gitblit Repository			com.gitblit.models.RepositoryModel
 *  user			Gitblit User				com.gitblit.models.UserModel
 *  commands		JGit commands 				Collection<org.eclipse.jgit.transport.ReceiveCommand>
 *	url				Base url for Gitblit		String
 *  logger			Logs messages to Gitblit 	org.slf4j.Logger
 *  clientLogger	Logs messages to Git client	com.gitblit.utils.ClientLogger
 *
 * Accessing Gitblit Custom Fields:
 *   def myCustomField = repository.customFields.myCustomField
 *  
 */

// map of protected command types to returned results type
// commands not included will skip authz check
def protectedCmds = [
	UPDATE_NONFASTFORWARD:	Result.REJECTED_NONFASTFORWARD,
	DELETE:					Result.REJECTED_NODELETE
]

// list of regex patterns for protected refs
def protectedRefs = [
	"refs/heads/master",
	"refs/tags/.+"
]

// teams which are authorized to perform protected commands on protected refs
def authorizedTeams = [ "admins" ]

for (ReceiveCommand command : commands) {
	def updateType = command.type
	def updatedRef = command.refName
	
	// find first regex which matches updated ref, if any
	def refPattern = protectedRefs.find { updatedRef.matches ~it }
	
	// find rejection result for update type, if any
	def result = protectedCmds[updateType.name()]
	
	// command requires authz if ref is protected and has a mapped rejection result
	if (refPattern && result) {
	
		// verify user is a member of any authorized team
		def team = authorizedTeams.find { user.isTeamMember it }
		if (team) {
			// don't adjust command result
			logger.info "${user.username} authorized for ${updateType} of protected ref ${repository.name}:${updatedRef} (${command.oldId.name} -> ${command.newId.name})"
		} else {
			// mark command result as rejected
			command.setResult(result, "${user.username} cannot ${updateType} protected ref ${repository.name}:${updatedRef} matching pattern ${refPattern}")
		}
	}
}
