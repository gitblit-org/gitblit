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
package com.gitblit.git;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefFilter;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;

import com.gitblit.Constants;
import com.gitblit.GitBlit;
import com.gitblit.models.UserModel;

/**
 * The upload pack factory creates an upload pack which controls what refs are
 * advertised to cloning/pulling clients.
 *
 * @author James Moger
 *
 * @param <X> the connection type
 */
public class GitblitUploadPackFactory<X> implements UploadPackFactory<X> {

	@Override
	public UploadPack create(X req, Repository db)
			throws ServiceNotEnabledException, ServiceNotAuthorizedException {

		UserModel user = UserModel.ANONYMOUS;
		int timeout = 0;

		if (req instanceof HttpServletRequest) {
			// http/https request may or may not be authenticated
			user = GitBlit.self().authenticate((HttpServletRequest) req);
			if (user == null) {
				user = UserModel.ANONYMOUS;
			}
		} else if (req instanceof GitDaemonClient) {
			// git daemon request is always anonymous
			GitDaemonClient client = (GitDaemonClient) req;
			// set timeout from Git daemon
			timeout = client.getDaemon().getTimeout();
		}

		RefFilter refFilter = new UserRefFilter(user);
		UploadPack up = new UploadPack(db);
		up.setRefFilter(refFilter);
		up.setTimeout(timeout);

		return up;
	}

	/**
	 * Restricts advertisement of certain refs based on the permission of the
	 * requesting user.
	 */
	public static class UserRefFilter implements RefFilter {

		final UserModel user;

		public UserRefFilter(UserModel user) {
			this.user = user;
		}

		@Override
		public Map<String, Ref> filter(Map<String, Ref> refs) {
			if (user.canAdmin()) {
				// admins can see all refs
				return refs;
			}

			// normal users can not clone any gitblit refs
			// JGit's RefMap is custom and does not support iterator removal :(
			List<String> toRemove = new ArrayList<String>();
			for (String ref : refs.keySet()) {
				if (ref.startsWith(Constants.R_GITBLIT)) {
					toRemove.add(ref);
				}
			}
			for (String ref : toRemove) {
				refs.remove(ref);
			}
			return refs;
		}
	}
}