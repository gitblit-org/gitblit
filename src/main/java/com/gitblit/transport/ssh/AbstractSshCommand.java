/*
 * Copyright 2014 gitblit.com.
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
package com.gitblit.transport.ssh;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SessionAware;
import org.apache.sshd.server.session.ServerSession;

import com.google.common.base.Charsets;

/**
 *
 * @author Eric Myrhe
 *
 */
public abstract class AbstractSshCommand implements Command, SessionAware {

	protected InputStream in;

	protected OutputStream out;

	protected OutputStream err;

	protected ExitCallback exit;

	protected ServerSession session;

	@Override
	public void setInputStream(InputStream in) {
		this.in = in;
	}

	@Override
	public void setOutputStream(OutputStream out) {
		this.out = out;
	}

	@Override
	public void setErrorStream(OutputStream err) {
		this.err = err;
	}

	@Override
	public void setExitCallback(ExitCallback exit) {
		this.exit = exit;
	}

	@Override
	public void setSession(final ServerSession session) {
		this.session = session;
	}

	@Override
	public void destroy() {}

    protected static PrintWriter toPrintWriter(final OutputStream o) {
        return new PrintWriter(new BufferedWriter(new OutputStreamWriter(o, Charsets.UTF_8)));
    }

	@Override
	public abstract void start(Environment env) throws IOException;
}
