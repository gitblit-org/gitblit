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
import com.gitblit.GitBlit
import com.gitblit.models.RepositoryModel
import com.gitblit.models.UserModel

import org.eclipse.jgit.transport.ReceiveCommand
import org.eclipse.jgit.transport.ReceiveCommand.Result
import org.eclipse.jgit.transport.ReceiveCommand.Type
import org.slf4j.Logger

/**
 * Sample Gitblit Pre-Receive Hook: protect-refs
 * 
 * This script provides basic authorization for receive command types for a list
 * of known ref patterns. Command types and unmatched ref patterns will be
 * ignored, meaning this script has an "allow by default" policy.
 *
 * This script works best when a repository requires authentication on push, but
 * can be used to enforce fast-forward commits or prohibit ref deletion by
 * setting the authorizedTeams variable to an empty list.
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
 * If you want this hook script to fail and abort all subsequent scripts in the
 * chain, "return false" at the appropriate failure points.
 *
 * Bound Variables:
 *  gitblit		Gitblit Server	 		com.gitblit.GitBlit
 *  repository	Gitblit Repository		com.gitblit.models.RepositoryModel
 *  user		Gitblit User			com.gitblit.models.UserModel
 *  commands	JGit commands 			Collection<org.eclipse.jgit.transport.ReceiveCommand>
 *  url			Base url for Gitblit	String
 *  logger		Logger instance			org.slf4j.Logger
 *  
 */

def protectedCmds = [ Type.UPDATE_NONFASTFORWARD, Type.DELETE ]
def protectedRefs = [ "refs/heads/master", "refs/tags/.+" ]
def authorizedTeams = [ "admins" ]
def blocked = false

for (ReceiveCommand command : commands) {
	def updateType = command.type
	def updatedRef = command.refName
	
	// find first regex which matches updated ref
	def protectedRef = protectedRefs.find { updatedRef.matches ~it }
	
	// ...and check if command type requires authz check
	if (protectedRef && updateType in protectedCmds) {
	
		// verify user is a member of any authorized team
		def team = authorizedTeams.find { user.isTeamMember it }
		if (team) {
			logger.info "authorized ${command} for ${team} member ${user.username}"
		} else {
			command.setResult(Result.REJECTED_OTHER_REASON, "${user.username} cannot ${updateType} protected ref ${repository.name}:${updatedRef} (matched pattern ${protectedRef})")
			blocked = true
		}
	}
}

if (blocked) {
	// return false to break the push hook chain
	return false
}