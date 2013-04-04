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
package com.gitblit.git;

import java.io.File;
import java.net.InetSocketAddress;

import org.eclipse.jgit.transport.Daemon;
import org.eclipse.jgit.transport.DaemonClient;

import com.gitblit.utils.StringUtils;

/**
 * Gitblit's Git Daemon ignores any and all per-repository daemon settings
 * and integrates into Gitblit's security model.
 * 
 * @author James Moger
 *
 */
public class GitDaemon extends Daemon {

	/**
	 * Construct the Gitblit Git daemon.
	 * 
	 * @param bindInterface
	 *            the ip address of the interface to bind
	 * @param port
	 *            the port to serve on
	 * @param folder
	 *            the folder to serve from
	 */
	public GitDaemon(String bindInterface, int port, File folder) {
		super(StringUtils.isEmpty(bindInterface) ? new InetSocketAddress(port) : new InetSocketAddress(bindInterface, port));
		
		// set the repository resolver and pack factories
		setRepositoryResolver(new RepositoryResolver<DaemonClient>(folder));
		setUploadPackFactory(new GitblitUploadPackFactory<DaemonClient>());
		setReceivePackFactory(new GitblitReceivePackFactory<DaemonClient>());
		
		// configure the git daemon to ignore the per-repository settings,
		// daemon.uploadpack and daemon.receivepack
		getService("git-upload-pack").setOverridable(false);
		getService("git-receive-pack").setOverridable(false);
		
		// enable both the upload and receive services and let the resolver,
		// pack factories, and receive hook handle security
		getService("git-upload-pack").setEnabled(true);
		getService("git-receive-pack").setEnabled(true);
	}
	
}
