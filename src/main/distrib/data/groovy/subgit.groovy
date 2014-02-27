/*
 * Copyright 2014 TMate Software <support@subgit.com>
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

import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.transport.ReceiveCommand
import org.eclipse.jgit.transport.ReceiveCommand.Result
import org.eclipse.jgit.util.FS
import org.slf4j.Logger

/**
 * Sample Gitblit Pre-Receive Hook: subgit
 * http://subgit.com
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
 *
 * Usage:
 *
 * 1. Create Git repository in GitBlit;
 * 2. Register subgit pre-receive groovy hook in repository settings;
 * 3. Install SubGit into created repository:
 *    subgit configure --svn-url <SVN_URL> <GITBLIT_HOME>/data/git/<GIT_REPO>
 *    subgit install <GITBLIT_HOME>/data/git/<GIT_REPO>
 *
 * You can enable SubGit pre-receive groovy hook for all repositories: this shouldn't
 * cause any problems as the hook skips those repositories with no SubGit installed.
 *
 */

def JAVA_OPTIONS = ['-noverify', '-Djava.awt.headless=true']
def MAIN_CLASS = 'org.tmatesoft.translator.SubGitHook'

def repositoryRoot = gitblit.getRepository(repository.name).directory
def classpath = getClasspath(repositoryRoot)
if (classpath != null) {
	def input = commands.collect {command -> "${command.oldId.name()} ${command.newId.name()} ${command.refName}\n"}.join()
	def commandLine = [javaExecutable] + JAVA_OPTIONS + ['-cp', classpath, MAIN_CLASS, 'pre-receive', repositoryRoot.absolutePath]
	logger.info("Running SubGit pre-receive hook:\n${commandLine}\n${input}")
	def process = new ProcessBuilder(commandLine).directory(repositoryRoot).start()
	writeInput(process, input)
	readOutput(process)
	if (process.exitValue() != 0) {
		commands.each {command -> command.setResult(Result.REJECTED_OTHER_REASON, 'SubGit: failed to synchronize changes with SVN server')}
	}
}

def getClasspath(repositoryRoot) {
	def configFile = new File(repositoryRoot, 'subgit/.run/config')
	def config = new FileBasedConfig(configFile, FS.DETECTED)
	config.load()
	def daemonClasspath = config.getString('daemon', null, 'classpath')
	if (daemonClasspath == null) {
		return null
	}
	def binariesDirectory = new File(daemonClasspath)
	if (!binariesDirectory.absolute) {
		binariesDirectory = new File(repositoryRoot, daemonClasspath)
	}
	binariesDirectory.listFiles({dir, name -> name ==~ /.*.jar/ } as FilenameFilter).join(File.pathSeparator)
}

def getJavaExecutable() {
	def javaHome = System.properties['java.home']
	def isWindows = System.properties['os.name'].toLowerCase().contains('windows')
	def executableName = isWindows ? 'java.exe' : 'java'
	"${javaHome}/bin/${executableName}".toString()
}

def writeInput(process, input) {
	Thread.start {
		process.out << input
		process.out.close()
	}
}

def readOutput(process) {
	try {
		def outReader = Thread.start {
			process.inputStream.eachLine {line -> clientLogger.info(line)}
		}
		def errReader = Thread.start {
			process.errorStream.eachLine {line -> clientLogger.info(line)}
		}
		process.waitFor()
		outReader.join()
		errReader.join()
	} finally {
		process.destroy()
	}
}