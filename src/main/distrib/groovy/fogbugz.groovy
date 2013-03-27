import org.eclipse.jgit.revwalk.RevCommit;

/*
 * Copyright 2013 gitblit.com.
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
import com.sun.org.apache.xalan.internal.xsltc.compiler.Import;

import java.text.SimpleDateFormat
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.Config
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.transport.ReceiveCommand
import org.eclipse.jgit.transport.ReceiveCommand.Result
import org.slf4j.Logger

import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.util.Set;
import java.util.HashSet;

/**
 * Sample Gitblit Post-Receive Hook: fogbugz
 *
 * The purpose of this script is to invoke the Fogbugz API and update a case when
 * push is received based.
 * 
 * Example URL - http://bugs.yourdomain.com/fogbugz/cvsSubmit.asp?ixBug=bugID&sFile=file&sPrev=x&sNew=y&ixRepository=206
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
 * Cusom Fileds Used by This script
 *   fogbugzUrl - base URL to Fogbugz (ie. https://bugs.yourdomain.com/fogbugz/)
 *   fogbugzRepositoryId - (ixRepository value from Fogbugz Source Control configuration screen)
 *   fogbugzCommitMessageRegex - regex pattern used to match on bug id
 */

// Indicate we have started the script
logger.info("fogbugz hook triggered by ${user.username} for ${repository.name}")

/*
 * Primitive email notification.
 * This requires the mail settings to be properly configured in Gitblit.
 */

Repository r = gitblit.getRepository(repository.name)

// pull custom fields from repository specific values
// groovy.customFields = "fogbugzUrl=Fogbugz Base URL" "fogbugzRepositoryId=Fogbugz Repository ID" "fogbugzCommitMessageRegex="Fogbugz Commit Message Regular Expression"
def fogbugzUrl = repository.customFields.fogbugzUrl
def fogbugzRepositoryId = repository.customFields.fogbugzRepositoryId
def bugIdRegex = repository.customFields.fogbugzCommitMessageRegex

for (command in commands) {

	for( commit in JGitUtils.getRevLog(r, command.oldId.name, command.newId.name).reverse() ) {
		// Example URL - http://bugs.salsalabs.com/fogbugz/cvsSubmit.asp?ixBug=bugID&sFile=file&sPrev=x&sNew=y&ixRepository=206
		def bugIds = [];
		// Grab the second matcher and then filter out each numeric ID and add it to array
        (commit.getFullMessage() =~ bugIdRegex).each{ (it[1] =~ "\\d+").each {bugIds.add(it)} }
		
		for( file in getFiles(r, commit) ) {
			for( bugId in bugIds ) {
				def url = "${fogbugzUrl}/cvsSubmit.asp?ixBug=${bugId}&sFile=${file}&sPrev=${command.oldId.name}&sNew=${command.newId.name}&ixRepository=${fogbugzRepositoryId}"
				logger.info( url );
				// Hit the page and make sure we get an "OK" response
				def responseString = new URL(url).getText()
				if( !"OK".equals(responseString) ) {
					throw new Exception( "Problem posting ${url} - ${responseString}" );
				}
			}
		}
	}
}
// close the repository reference
r.close()

/**
 * For a given commit, find all files part of it.
 */
def Set<String> getFiles(Repository r, RevCommit commit) {
	DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)
	formatter.setRepository(r)
	formatter.setDetectRenames(true)
	formatter.setDiffComparator(RawTextComparator.DEFAULT);

	def diffs
	RevWalk rw = new RevWalk(r)
	if (commit.parentCount > 0) {
		RevCommit parent = rw.parseCommit(commit.parents[0].id)
		diffs = formatter.scan(parent.tree, commit.tree)
	} else {
		diffs = formatter.scan(new EmptyTreeIterator(),
							   new CanonicalTreeParser(null, rw.objectReader, commit.tree))
	}
	rw.dispose()
	
	// Grab each filepath
	Set<String> fileNameSet = new HashSet<String>( diffs.size() );
	for (DiffEntry entry in diffs) {
		FileHeader header = formatter.toFileHeader(entry)
		fileNameSet.add( header.newPath )
	}
	return fileNameSet;
}