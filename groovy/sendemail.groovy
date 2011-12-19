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
import com.gitblit.Keys
import com.gitblit.models.RepositoryModel
import com.gitblit.models.UserModel
import com.gitblit.utils.JGitUtils
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.Config
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.transport.ReceiveCommand
import org.eclipse.jgit.transport.ReceiveCommand.Result
import org.slf4j.Logger

/**
 * Sample Gitblit Post-Receive Hook: sendemail
 *
 * The Post-Receive hook is executed AFTER the pushed commits have been applied
 * to the Git repository.  This is the appropriate point to trigger an
 * integration build or to send a notification.
 * 
 * This script is only executed when pushing to *Gitblit*, not to other Git
 * tooling you may be using.
 * 
 * If this script is specified in *groovy.postReceiveScripts* of gitblit.properties
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
 *	url			Base url for Gitblit	String
 *  logger		Logger instance			org.slf4j.Logger
 *  
 */

// Indicate we have started the script
logger.info("sendemail hook triggered by ${user.username} for ${repository.name}")

/*
 * Primitive example email notification with example repository-specific checks.
 * This requires the mail settings to be properly configured in Gitblit.
 */

Repository r = gitblit.getRepository(repository.name)

// reuse some existing repository config settings, if available
Config config = r.getConfig()
def mailinglist = config.getString('hooks', null, 'mailinglist')
def emailprefix = config.getString('hooks', null, 'emailprefix')

// set default values
def toAddresses = []
if (emailprefix == null)
emailprefix = '"[Gitblit]'

if (mailinglist != null) {
	def addrs = mailinglist.split('(,|\\s)')
	toAddresses.addAll(addrs)
}

// add all mailing lists defined in gitblit.properties or web.xml
toAddresses.addAll(gitblit.getStrings(Keys.mail.mailingLists))

// add all mail recipients for the repository
toAddresses.addAll(repository.mailRecipients)

// special custom cases
switch(repository.name) {
	case 'ex@mple.git':
		toAddresses.add 'dev-team@somewhere.com'
		toAddresses.add 'qa-team@somewhere.com'
		break
}

// define the summary and commit urls
def summaryUrl
def commitUrl
if (gitblit.getBoolean(Keys.web.mountParameters, true)) {
	summaryUrl = url + '/summary/' + repository.name.replace('/', gitblit.getString(Keys.web.forwardSlashCharacter, '/'))
	commitUrl = url + '/commit/'
} else {
	summaryUrl = url + '/summary?r=' + repository.name
	commitUrl = url + '/commit?h='
}

// construct a simple text summary of the changes contained in the push
def commitCount = 0
def changes = ''
def table = { it.authorIdent.name.padRight(25, ' ') + it.shortMessage + "\n$commitUrl" + it.id.name }
for (command in commands) {
	def ref = command.refName.substring('refs/heads/'.length())
	switch (command.type) {
		case ReceiveCommand.Type.CREATE:
			def commits = JGitUtils.getRevLog(r, command.oldId.name, command.newId.name)
			commitCount += commits.size()
			// new branch commits table
			changes += "created $ref ($commits.size commits)\n\n"
			changes += commits.collect(table).join('\n\n')
			changes += '\n'
			break
		case ReceiveCommand.Type.UPDATE:
			def commits = JGitUtils.getRevLog(r, command.oldId.name, command.newId.name)
			commitCount += commits.size()
			// fast-forward branch commits table
			changes += "updated $ref ($commits.size commits)\n\n"
			changes += commits.collect(table).join('\n\n')
			changes += '\n'
			break
		case ReceiveCommand.Type.UPDATE_NONFASTFORWARD:
			def commits = JGitUtils.getRevLog(r, command.oldId.name, command.newId.name)
			commitCount += commits.size()
			// non-fast-forward branch commits table
			changes += "updated $ref [NON fast-forward] ($commits.size commits)\n\n"
			changes += commits.collect(table).join('\n\n')
			changes += '\n'
			break
		case ReceiveCommand.Type.DELETE:
			// deleted branch
			changes += "deleted $ref\n\n"
			break
		default:
			break
	}
}
// close the repository reference
r.close()

// tell Gitblit to send the message (Gitblit filters duplicate addresses)
gitblit.sendEmail("$emailprefix $user.username pushed $commitCount commits => $repository.name", "$summaryUrl\n\n$changes", toAddresses)