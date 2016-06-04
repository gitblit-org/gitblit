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
import java.text.SimpleDateFormat
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.Config
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
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.util.Set;
import java.util.HashSet;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.protocol.*;
import org.apache.http.client.protocol.*;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.*;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.util.EntityUtils;


/**
 * GitBlit Post-Receive Hook for YouTrack
 *
 * The purpose of this script is to invoke the YouTrack API and update a case when
 * push is received based.
 * 
 * The Post-Receive hook is executed AFTER the pushed commits have been applied
 * to the Git repository.  This is the appropriate point to trigger an
 * integration build or to send a notification.
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
 * 
 * Custom Fileds Used by This script
 *   youtrackProjectID - Project ID in YouTrack
 *
 * Make sure to add the following to your gitblit.properties file:
 *   groovy.customFields = "youtrackProjectID=YouTrack Project ID" 
 *   youtrack.host = example.myjetbrains.com
 *   youtrack.user = ytUser
 *   youtrack.pass = insecurep@sswordsRus
 */

// Indicate we have started the script
logger.info("youtrack hook triggered in ${url} by ${user.username} for ${repository.name}")

Repository r = gitblit.getRepository(repository.name)

// pull custom fields from repository specific values
def youtrackProjectID = repository.customFields.youtrackProjectID

if(youtrackProjectID == null || youtrackProjectID.length() == 0) return true;

def youtrackHost = gitblit.getString('youtrack.host', 'nohost')
def bugIdRegex = gitblit.getString('youtrack.commitMessageRegex', "#${youtrackProjectID}-([0-9]+)")
def youtrackUser = gitblit.getString('youtrack.user', 'nouser')
def youtrackPass = gitblit.getString('youtrack.pass', 'nopassword')

HttpHost target = new HttpHost(youtrackHost, 80, "http");
CredentialsProvider credsProvider = new BasicCredentialsProvider();
credsProvider.setCredentials(
    new AuthScope(target.getHostName(), target.getPort()),
    new UsernamePasswordCredentials(youtrackUser, youtrackPass));
def httpclient = new DefaultHttpClient();

httpclient.setCredentialsProvider(credsProvider);

try {

    AuthCache authCache = new BasicAuthCache();
    BasicScheme basicAuth = new BasicScheme();
    authCache.put(target, basicAuth);
    BasicHttpContext localcontext = new BasicHttpContext();
    localcontext.setAttribute(ClientContext.AUTH_CACHE, authCache);
    
    
    for (command in commands) {
        for( commit in JGitUtils.getRevLog(r, command.oldId.name, command.newId.name).reverse() ) {
            def bugIds = new java.util.HashSet()
            def longMsg = commit.getFullMessage()
            // Grab the second match group and then filter out each numeric ID and add it to array
            (longMsg =~ bugIdRegex).each{ (it[1] =~ "\\d+").each { bugIds.add(it)} }

            if(bugIds.size() > 0) {
                def comment = createIssueComment(command, commit)

                logger.debug("Submitting youtrack comment:\n" + comment)

                def encoded = URLEncoder.encode(comment)
                for(bugId in bugIds ) {
                    def baseURL = "http://${youtrackHost}/youtrack/rest/issue/${youtrackProjectID}-${bugId}/execute?command=&comment=" + encoded
                    def post = new HttpPost(baseURL);
            
                    clientLogger.info("Executing request " + post.getRequestLine() + " to target " + target);
                    def response = httpclient.execute(target, post, localcontext);
                    logger.debug(response.getStatusLine().toString());
                    EntityUtils.consume(response.getEntity());
                }
            }
        }
    }
}
finally {
    r.close()
}

def createIssueComment(command, commit) {
    def commits = [commit] // Borrowed code expects a collection. 
    Repository r = gitblit.getRepository(repository.name)
    // define the summary and commit urls
    def repo = repository.name
    def summaryUrl
    def commitUrl
    if (gitblit.getBoolean(Keys.web.mountParameters, true)) {
        repo = repo.replace('/', gitblit.getString(Keys.web.forwardSlashCharacter, '/')).replace('/', '%2F')
        summaryUrl = url + "/summary/$repo"
        commitUrl = url + "/commit/$repo/"
    } else {
        summaryUrl = url + "/summary?r=$repo"
        commitUrl = url + "/commit?r=$repo&h="
    }
    
    // construct a simple text summary of the changes contained in the push
    def commitBreak = '\n'
    def commitCount = 0
    def changes = ''

    SimpleDateFormat df = new SimpleDateFormat(gitblit.getString(Keys.web.datetimestampLongFormat, 'EEEE, MMMM d, yyyy h:mm a z'))

    def table = {
        def shortSha = it.id.name.substring(0, 8)
        "* [$commitUrl$it.id.name ${shortSha}] by *${it.authorIdent.name}* on ${df.format(JGitUtils.getCommitDate(it))}\n" +
            "  {cut $it.shortMessage}\n{noformat}$it.fullMessage{noformat}{cut}"
    }

    def ref = command.refName
    def refType = 'branch'
    if (ref.startsWith('refs/heads/')) {
        ref  = command.refName.substring('refs/heads/'.length())
    } else if (ref.startsWith('refs/tags/')) {
        ref  = command.refName.substring('refs/tags/'.length())
        refType = 'tag'
    }
		
    switch (command.type) {
    case ReceiveCommand.Type.CREATE:
    // new branch
    changes += "''new $refType $ref created''\n"
    changes += commits.collect(table).join(commitBreak)
    changes += '\n'
    break
    case ReceiveCommand.Type.UPDATE:
    // fast-forward branch commits table
    changes += "''$ref $refType updated''\n"
    changes += commits.collect(table).join(commitBreak)
    changes += '\n'
    break
    case ReceiveCommand.Type.UPDATE_NONFASTFORWARD:
    // non-fast-forward branch commits table
    changes += "''$ref $refType updated [NON fast-forward]''"
    changes += commits.collect(table).join(commitBreak)
    changes += '\n'
    break
    case ReceiveCommand.Type.DELETE:
    // deleted branch/tag
    changes += "''$ref $refType deleted''"
    break
    default:
    break
    }
    
    return "$user.username pushed commits to [$summaryUrl $repository.name]\n$changes"
}
