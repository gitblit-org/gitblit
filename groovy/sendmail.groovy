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
import com.gitblit.models.TeamModel
import com.gitblit.models.UserModel
import com.gitblit.utils.JGitUtils
import java.text.SimpleDateFormat
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.Config
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.transport.ReceiveCommand
import org.eclipse.jgit.transport.ReceiveCommand.Result
import org.slf4j.Logger
import groovy.xml.MarkupBuilder
import java.security.MessageDigest


/**
 * Sample Gitblit Post-Receive Hook: sendmail
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
 *  gitblit			Gitblit Server	 			com.gitblit.GitBlit
 *  repository		Gitblit Repository			com.gitblit.models.RepositoryModel
 *  receivePack		JGit Receive Pack			org.eclipse.jgit.transport.ReceivePack
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
logger.info("sendmail hook triggered by ${user.username} for ${repository.name}")

/*
 * Primitive email notification.
 * This requires the mail settings to be properly configured in Gitblit.
 */

Repository r = gitblit.getRepository(repository.name)

// reuse existing repository config settings, if available
Config config = r.getConfig()
def mailinglist = config.getString('hooks', null, 'mailinglist')
def emailprefix = config.getString('hooks', null, 'emailprefix')

// set default values
def toAddresses = []
if (emailprefix == null) {
    emailprefix = '[Gitblit]'
}

if (mailinglist != null) {
	def addrs = mailinglist.split(/(,|\s)/)
	toAddresses.addAll(addrs)
}

// add all mailing lists defined in gitblit.properties or web.xml
toAddresses.addAll(gitblit.getStrings(Keys.mail.mailingLists))

// add all team mailing lists
def teams = gitblit.getRepositoryTeams(repository)
for (team in teams) {
	TeamModel model = gitblit.getTeamModel(team)
	if (model.mailingLists) {
		toAddresses.addAll(model.mailingLists)
	}
}

// add all mailing lists for the repository
toAddresses.addAll(repository.mailingLists)

// define the summary and commit urls
def repo = repository.name
def summaryUrl = url + "/summary?r=$repo"
def baseCommitUrl = url + "/commit?r=$repo&h="

if (gitblit.getBoolean(Keys.web.mountParameters, true)) {
	repo = repo.replace('/', gitblit.getString(Keys.web.forwardSlashCharacter, '/')).replace('/', '%2F')
	summaryUrl = url + "/summary/$repo"
	baseCommitUrl = url + "/commit/$repo/"
}

class HtmlMailWriter {
	Repository repository
	def baseCommitUrl
	def commitCount = 0
	def commands
	def writer = new StringWriter();
	def builder = new MarkupBuilder(writer)
	
	def writeStyle() {
		builder.style(type:"text/css", '''
    th, td {  
        padding: 2px;  
    }
    thead {
        text-align: left;
        font-weight: bold; 
    }
	thead tr {
        border-bottom: 1px dotted #000; 
    }
	a {
        text-decoration: none;
    }
    .commits-table {
        border-collapse: collapse;
        font-family: sans-serif; 
    }
    .label-commit {
        border-radius:4px;
        background-color: #3A87AD;
        padding: 2px 4px;
        color: white;
        vertical-align: baseline; 
        font-weight: bold; 
        font-family: monospace; 
    }
    ''')
	}
	
	def writeBranchTitle(type, name, action, number) {
		builder.h2 {
			mkp.yield "$type "
			span(style:"font-family: monospace;", name )
			mkp.yield " $action ($number commits)"
		}
	}
	
	def writeBranchDeletedTitle(type, name) {
		builder.h2 {
			mkp.yield "$type "
			span(style:"font-family: monospace;", name )
			mkp.yield " deleted"
		}
	}
	
	def commitUrl(RevCommit commit) {
		"${baseCommitUrl}$commit.id.name"
	}
	
	def writeCommitTable(commits) {
		// Write commits table
		builder.table('class':"commits-table") {
			thead {
				tr {
					th(colspan:2, "Author")
					th( "Commit" )
					th( "Message" )
				}
			}
			tbody() {
				
				// Write all the commits
				for (commit in commits) {
					writeCommit(commit)
				}
			}
		}
	}
	
	def writeCommit(commit) {
		def abbreviated = repository.newObjectReader().abbreviate(commit.id, 6).name()
		def author = commit.authorIdent.name
		def email = commit.authorIdent.emailAddress
		def message = commit.shortMessage
		builder.tr {
			td {
				img(src:gravatarUrl(email))
			}
			td ( author )
			td {
				a(href:commitUrl(commit)) {
					span('class':"label-commit",  abbreviated )
				}
			}
			td ( message )
		}
	}
	
	def md5(text) {
		
	   def digest = MessageDigest.getInstance("MD5")
		
	   //Quick MD5 of text
	   def hash = new BigInteger(1, digest.digest(text.getBytes()))
						 .toString(16)
						 .padLeft(32, "0")
	   hash.toString()
	}
	
	def gravatarUrl(email) {
		def cleaned = email.trim().toLowerCase()
		"http://www.gravatar.com/avatar/${md5(cleaned)}?s=30"
	}
	
	def write() {
		builder.html {
			head {
				writeStyle()
			}
			body {
		
				for (command in commands) {
					def ref = command.refName
					def refType = 'Branch'
					if (ref.startsWith('refs/heads/')) {
						ref  = command.refName.substring('refs/heads/'.length())
					} else if (ref.startsWith('refs/tags/')) {
						ref  = command.refName.substring('refs/tags/'.length())
						refType = 'Tag'
					}
		
					switch (command.type) {
						case ReceiveCommand.Type.CREATE:
							def commits = JGitUtils.getRevLog(repository, command.oldId.name, command.newId.name).reverse()
							commitCount += commits.size()
						// new branch
							// Write header
							writeBranchTitle(refType, ref, "created", commits.size())
							writeCommitTable(commits)
							break
						case ReceiveCommand.Type.UPDATE:
							def commits = JGitUtils.getRevLog(repository, command.oldId.name, command.newId.name).reverse()
							commitCount += commits.size()
						// fast-forward branch commits table
							// Write header
							writeBranchTitle(refType, ref, "updated", commits.size())
							writeCommitTable(commits)
							break
						case ReceiveCommand.Type.UPDATE_NONFASTFORWARD:
							def commits = JGitUtils.getRevLog(repository, command.oldId.name, command.newId.name).reverse()
							commitCount += commits.size()
						// non-fast-forward branch commits table
							// Write header
							writeBranchTitle(refType, ref, "updated [NON fast-forward]", commits.size())
							writeCommitTable(commits)
							break
						case ReceiveCommand.Type.DELETE:
						// deleted branch/tag
							writeBranchDeletedTitle(refType, ref)
							break
						default:
							break
					}
				}
			}
		}
		writer.toString()
	}
	
}

def df = new SimpleDateFormat(gitblit.getString(Keys.web.datetimestampLongFormat, 'EEEE, MMMM d, yyyy h:mm a z'))

def mailWriter = new HtmlMailWriter()
mailWriter.repository = r
mailWriter.baseCommitUrl = baseCommitUrl
mailWriter.commands = commands

def content = mailWriter.write()

// close the repository reference
r.close()

// tell Gitblit to send the message (Gitblit filters duplicate addresses)
gitblit.sendHtmlMail("$emailprefix $user.username pushed ${mailWriter.commitCount} commits => $repository.name",
	             content,
				 toAddresses)
