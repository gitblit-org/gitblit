/*
 * Copyright 2012 gitblit.com.
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

import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.Config
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand
import org.eclipse.jgit.transport.ReceiveCommand.Result
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger
import groovy.xml.MarkupBuilder

import java.io.IOException;
import java.security.MessageDigest


/**
 * Sample Gitblit Post-Receive Hook: sendmail-html
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
 *  gitblit         Gitblit Server               com.gitblit.GitBlit
 *  repository      Gitblit Repository           com.gitblit.models.RepositoryModel
 *  user            Gitblit User                 com.gitblit.models.UserModel
 *  commands        JGit commands                Collection<org.eclipse.jgit.transport.ReceiveCommand>
 *  url             Base url for Gitblit         java.lang.String
 *  logger          Logs messages to Gitblit     org.slf4j.Logger
 *  clientLogger    Logs messages to Git client  com.gitblit.utils.ClientLogger
 *
 * Accessing Gitblit Custom Fields:
 *   def myCustomField = repository.customFields.myCustomField
 *  
 */

com.gitblit.models.UserModel userModel = user

// Indicate we have started the script
logger.info("sendmail-html hook triggered by ${user.username} for ${repository.name}")

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
def baseBlobDiffUrl = url + "/blobdiff/?r=$repo&h="
def baseCommitDiffUrl = url + "/commitdiff/?r=$repo&h="
def forwardSlashChar = gitblit.getString(Keys.web.forwardSlashCharacter, '/')

if (gitblit.getBoolean(Keys.web.mountParameters, true)) {
    repo = repo.replace('/', forwardSlashChar).replace('/', '%2F')
    summaryUrl = url + "/summary/$repo"
    baseCommitUrl = url + "/commit/$repo/"
    baseBlobDiffUrl = url + "/blobdiff/$repo/"
    baseCommitDiffUrl = url + "/commitdiff/$repo/"
}

class HtmlMailWriter {
    Repository repository
    def url
    def baseCommitUrl
    def baseCommitDiffUrl
    def baseBlobDiffUrl
    def mountParameters
	def forwardSlashChar
	def includeGravatar
	def shortCommitIdLength
    def commitCount = 0
    def commands
    def writer = new StringWriter();
    def builder = new MarkupBuilder(writer)

    def writeStyle() {
        builder.style(type:"text/css", '''
    .table td {
        vertical-align: middle;
    }
    tr.noborder td {
        border: none;
        padding-top: 0px;
    }
    .gravatar-column {
        width: 5%; 
    }
    .author-column {
        width: 20%; 
    }
    .commit-column {
        width: 5%; 
    }
    .status-column {
        width: 10%;
    }
    .table-disable-hover.table tbody tr:hover td,
    .table-disable-hover.table tbody tr:hover th {
        background-color: inherit;
    }
    .table-disable-hover.table-striped tbody tr:nth-child(odd):hover td,
    .table-disable-hover.table-striped tbody tr:nth-child(odd):hover th {
      background-color: #f9f9f9;
    }
    ''')
    }

    def writeBranchTitle(type, name, action, number) {
        builder.div('class' : 'pageTitle') {
			builder.span('class':'project') {
				mkp.yield "$type "
				span('class': 'repository', name )
				if (number > 0) {
					mkp.yield " $action ($number commits)"
				} else {
					mkp.yield " $action"
				}
			}
        }
    }

    def writeBranchDeletedTitle(type, name) {
		builder.div('class' : 'pageTitle', 'style':'color:red') {
			builder.span('class':'project') {
				mkp.yield "$type "
				span('class': 'repository', name )
				mkp.yield " deleted"
			}
		}
    }

    def commitUrl(RevCommit commit) {
        "${baseCommitUrl}$commit.id.name"
    }

    def commitDiffUrl(RevCommit commit) {
        "${baseCommitDiffUrl}$commit.id.name"
    }

    def encoded(String path) {
        path.replace('/', forwardSlashChar).replace('/', '%2F')
    }

    def blobDiffUrl(objectId, path) {
        if (mountParameters) {
            // REST style
            "${baseBlobDiffUrl}${objectId.name()}/${encoded(path)}"
        } else {
            "${baseBlobDiffUrl}${objectId.name()}&f=${path}"
        }

    }

    def writeCommitTable(commits, includeChangedPaths=true) {
        // Write commits table
        builder.table('class':"table table-disable-hover") {
            thead {
                tr {
					th(colspan: includeGravatar ? 2 : 1, "Author")
                    th( "Commit" )
                    th( "Message" )
                }
            }
            tbody() {

                // Write all the commits
                for (commit in commits) {
                    writeCommit(commit)

					if (includeChangedPaths) {
						// Write detail on that particular commit
						tr('class' : 'noborder') {
							td (colspan: includeGravatar ? 3 : 2)
							td (colspan:2) { writeStatusTable(commit) }
						}
					}
                }
            }
        }
    }

    def writeCommit(commit) {
        def abbreviated = repository.newObjectReader().abbreviate(commit.id, shortCommitIdLength).name()
        def author = commit.authorIdent.name
        def email = commit.authorIdent.emailAddress
        def message = commit.shortMessage
        builder.tr {
			if (includeGravatar) {
				td('class':"gravatar-column") {
					img(src:gravatarUrl(email), 'class':"gravatar")
				}
			}
            td('class':"author-column", author)
            td('class':"commit-column") {
                a(href:commitUrl(commit)) {
                    span('class':"label label-info",  abbreviated )
                }
            }
            td {
                mkp.yield message
                a('class':'link', href:commitDiffUrl(commit), " [commitdiff]" )
            }
        }
    }

    def writeStatusLabel(style, tooltip) {
        builder.span('class' : style,  'title' : tooltip )
    }

    def writeAddStatusLine(ObjectId id, FileHeader header) {		
        builder.td('class':'changeType') {
            writeStatusLabel("addition", "addition")
        }
        builder.td {
            a(href:blobDiffUrl(id, header.newPath), header.newPath)
        }
    }

    def writeCopyStatusLine(ObjectId id, FileHeader header) {
        builder.td('class':'changeType') {
            writeStatusLabel("rename", "rename")
        }
        builder.td() {
            a(href:blobDiffUrl(id, header.newPath), header.oldPath + " copied to " + header.newPath)
        }
    }

    def writeDeleteStatusLine(ObjectId id, FileHeader header) {
        builder.td('class':'changeType') {
            writeStatusLabel("deletion", "deletion")
        }
        builder.td() {
            a(href:blobDiffUrl(id, header.oldPath), header.oldPath)
        }
    }

    def writeModifyStatusLine(ObjectId id, FileHeader header) {
        builder.td('class':'changeType') {
			writeStatusLabel("modification", "modification")
        }
        builder.td() {
            a(href:blobDiffUrl(id, header.oldPath), header.oldPath)
        }
    }

    def writeRenameStatusLine(ObjectId id, FileHeader header) {
        builder.td('class':'changeType') {
             writeStatusLabel("rename", "rename")
        }
        builder.td() {
            mkp.yield header.oldPath
			mkp.yieldUnescaped "<b> -&gt; </b>"
			a(href:blobDiffUrl(id, header.newPath),  header.newPath)
        }
    }

    def writeStatusLine(ObjectId id, FileHeader header) {
        builder.tr {
            switch (header.changeType) {
                case ChangeType.ADD:
                    writeAddStatusLine(id, header)
                    break;
                case ChangeType.COPY:
                    writeCopyStatusLine(id, header)
                    break;
                case ChangeType.DELETE:
                    writeDeleteStatusLine(id, header)
                    break;
                case ChangeType.MODIFY:
                    writeModifyStatusLine(id, header)
                    break;
                case ChangeType.RENAME:
                    writeRenameStatusLine(id, header)
                    break;
            }
        }
    }

    def writeStatusTable(RevCommit commit) {
        DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)
        formatter.setRepository(repository)
        formatter.setDetectRenames(true)
        formatter.setDiffComparator(RawTextComparator.DEFAULT);

        def diffs
		RevWalk rw = new RevWalk(repository)
        if (commit.parentCount > 0) {
			RevCommit parent = rw.parseCommit(commit.parents[0].id)
            diffs = formatter.scan(parent.tree, commit.tree)
        } else {
            diffs = formatter.scan(new EmptyTreeIterator(),
                                   new CanonicalTreeParser(null, rw.objectReader, commit.tree))
        }
		rw.dispose()
        // Write status table
        builder.table('class':"plain") {
            tbody() {
                for (DiffEntry entry in diffs) {
                    FileHeader header = formatter.toFileHeader(entry)
                    writeStatusLine(commit.id, header)
                }
            }
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

    def writeNavbar() {
        builder.div('class':"navbar navbar-fixed-top") {
            div('class':"navbar-inner") {
                div('class':"container") {
                    a('class':"brand", href:"${url}", title:"GitBlit") {
                        img(src:"${url}/gitblt_25_white.png",
                            width:"79",
                            height:"25",
                            'class':"logo")
                    }
                }
            }
        }
    }

    def write() {
        builder.html {
            head {
                link(rel:"stylesheet", href:"${url}/bootstrap/css/bootstrap.css")
                link(rel:"stylesheet", href:"${url}/gitblit.css")
				link(rel:"stylesheet", href:"${url}/bootstrap/css/bootstrap-responsive.css")
                writeStyle()
            }
            body {

                writeNavbar()

				div('class':"container") {

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
							if (refType == 'Branch') {
								// new branch
								writeBranchTitle(refType, ref, "created", commits.size())
								writeCommitTable(commits, true)
							} else {
								// new tag
								writeBranchTitle(refType, ref, "created", 0)
								writeCommitTable(commits, false)
							}
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
        }
        writer.toString()
    }

}

def mailWriter = new HtmlMailWriter()
mailWriter.repository = r
mailWriter.baseCommitUrl = baseCommitUrl
mailWriter.baseBlobDiffUrl = baseBlobDiffUrl
mailWriter.baseCommitDiffUrl = baseCommitDiffUrl
mailWriter.forwardSlashChar = forwardSlashChar
mailWriter.commands = commands
mailWriter.url = url
mailWriter.mountParameters = gitblit.getBoolean(Keys.web.mountParameters, true)
mailWriter.includeGravatar = gitblit.getBoolean(Keys.web.allowGravatar, true)
mailWriter.shortCommitIdLength = gitblit.getInteger(Keys.web.shortCommitIdLength, 8)

def content = mailWriter.write()

// close the repository reference
r.close()

// tell Gitblit to send the message (Gitblit filters duplicate addresses)
def repositoryName = repository.name.substring(0, repository.name.length() - 4)
gitblit.sendHtmlMail("${emailprefix} ${userModel.displayName} pushed ${mailWriter.commitCount} commits => $repositoryName",
                     content,
                     toAddresses)
