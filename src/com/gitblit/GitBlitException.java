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
package com.gitblit;

import java.io.IOException;

/**
 * GitBlitException is a marginally useful class. :)
 * 
 * @author James Moger
 * 
 */
public class GitBlitException extends IOException {

	private static final long serialVersionUID = 1L;

	public GitBlitException(String message) {
		super(message);
	}

	public GitBlitException(Throwable cause) {
		super(cause);
	}

	/**
	 * Exception to indicate that the client should prompt for credentials
	 * because the requested action requires authentication.
	 */
	public static class UnauthorizedException extends GitBlitException {

		private static final long serialVersionUID = 1L;

		public UnauthorizedException(String message) {
			super(message);
		}
	}

	/**
	 * Exception to indicate that the requested action can not be executed by
	 * the specified user.
	 */
	public static class ForbiddenException extends GitBlitException {

		private static final long serialVersionUID = 1L;

		public ForbiddenException(String message) {
			super(message);
		}
	}

	/**
	 * Exception to indicate that the requested action has been disabled on the
	 * Gitblit server.
	 */
	public static class NotAllowedException extends GitBlitException {

		private static final long serialVersionUID = 1L;

		public NotAllowedException(String message) {
			super(message);
		}
	}

	/**
	 * Exception to indicate that the requested action can not be executed by
	 * the server because it does not recognize the request type.
	 */
	public static class UnknownRequestException extends GitBlitException {

		private static final long serialVersionUID = 1L;

		public UnknownRequestException(String message) {
			super(message);
		}
	}
}
