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
import java.text.MessageFormat;

import com.gitblit.GitBlit
import com.gitblit.models.RepositoryModel
import com.gitblit.models.UserModel
import org.eclipse.jgit.transport.ReceiveCommand
import org.eclipse.jgit.transport.ReceiveCommand.Result
import org.slf4j.Logger
import com.gitblit.utils.ClientLogger

/**
 * Sample Gitblit Pre-Receive Hook: blockpush
 * 
 * This script could and perhaps should be further developed to provide
 * a full repository-branch permissions system similar to gitolite or gitosis.
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

// Indicate we have started the script
logger.info("blockpush hook triggered by ${user.username} for ${repository.name}: checking ${commands.size} commands")

/*
 * Example rejection of pushes to the master branch of example.git
 */
def blocked = false
switch (repository.name) {
	case 'ex@mple.git':
		for (ReceiveCommand command : commands) {
			def updatedRef = command.refName
			if (updatedRef.equals('refs/heads/master')) {
				// to reject a command set it's result to anything other than Result.NOT_ATTEMPTED
				command.setResult(Result.REJECTED_OTHER_REASON, "You are not permitted to write to ${repository.name}:${updatedRef}")
				blocked = true
			}
		}
		break

	default:
		break
}

if (blocked) {
	// return false to break the push hook chain
	return false
}