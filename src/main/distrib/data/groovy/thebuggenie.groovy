/*
 * Copyright 2011 Wolfgang Gassler gassler.org
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
import org.eclipse.jgit.lib.IndexDiff
import org.eclipse.jgit.lib.Constants
import com.gitblit.utils.DiffUtils

/**
 * Gitblit Post-Receive Hook: thebuggenie
 * www.thebuggenie.com
 * 
 * Submit the commit information to thebuggenie bug tracker by calling thebuggenie client tool
 *  
 * Config of the Script:
 * 
 * Setup a custom gitblit field in the proprties file of gitblit by adding the following line
 *   groovy.customFields = "thebuggenieProjectId=TheBugGennie project id (used for thebuggenie hoocks)"
 * This field allows to specify the project id of thebuggenie project in the edit section of gitblit
 * 
 * Furthermore you need to set the path to thebuggenie client tool by adding the following property to
 * the gitblit properties file
 *   thebuggenie.tbg_cli = /var/www/thebuggenie_root/tbg_cli
 */

// Indicate we have started the script
logger.info("thebuggenie hook triggered by ${user.username} for ${repository.name}")

//fetch the repository data
Repository r = gitblit.getRepository(repository.name)

//get project id which is defined in the git repo metadata
def tbgProjectId = repository.customFields.thebuggenieProjectId
//get path to the thebuggenie client tool which is defined in the gitblit proprties files
def tbgCliPath = gitblit.getString('thebuggenie.tbg_cli', '/var/www/thebuggenie/tbg_cli')
def tbgCliDirPath = new File(tbgCliPath).getParent()

for(command in commands) {
	//fetch all pushed commits
	def commits = JGitUtils.getRevLog(r, command.oldId.name, command.newId.name).reverse()
	for (commit in commits) {
		//get hashes and author data of commit
		def oldhash = commit.getParent(0).getId().getName()
		def newhash = commit.getId().getName()
		def authorIdent = commit.getAuthorIdent()
		def author = "${authorIdent.name} <${authorIdent.emailAddress}>"
		//fetch all changed files of the commit
		def files = JGitUtils.getFilesInCommit(r,commit)
		def changedFiles = ""
		for (f in files) {
			//transform file data to the format which is needed by thebuggenie
			changedFiles += f.changeType.toString().substring(0,1)+"\t${f.path}\n"
		}
		//ok let's submit all information to thebuggenie by calling the client tool
//		def shc = "$tbgCliPath vcs_integration:report_commit $tbgProjectId \"$author\" $newhash  \"${commit.fullMessage}\" \"$changedFiles\" $oldhash ${commit.commitTime}"
		def shc = [tbgCliPath, "vcs_integration:report_commit", tbgProjectId, author, newhash, commit.getFullMessage(), changedFiles, oldhash, commit.getCommitTime()];
		logger.info("executing in path " + tbgCliDirPath + ": "+shc)
		shc.execute(null, new File(tbgCliDirPath))
	}
}

// close the repository reference
r.close()
