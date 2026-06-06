/*
 * Copyright (C) 2009 The Android Open Source Project
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
package com.gitblit.transport.ssh.commands;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.sshd.common.SshException;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.session.ServerSessionAware;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Keys;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.WorkQueue;
import com.gitblit.utils.WorkQueue.CancelableRunnable;
import com.gitblit.utils.cli.CmdLineParser;
import com.google.common.base.Charsets;
import com.google.common.util.concurrent.Atomics;

public abstract class BaseCommand implements Command, ServerSessionAware {

    private static final Logger log = LoggerFactory.getLogger(BaseCommand.class);
    private static final int PRIVATE_STATUS = 1 << 30;

    public static final int STATUS_CANCEL = PRIVATE_STATUS | 1;
    public static final int STATUS_NOT_FOUND = PRIVATE_STATUS | 2;
    public static final int STATUS_NOT_ADMIN = PRIVATE_STATUS | 3;

    protected InputStream in;
    protected OutputStream out;
    protected OutputStream err;
    protected ExitCallback exit;
    protected ServerSession session;

    private SshCommandContext ctx;
    private String commandName = "";
    private String[] argv;
    private final AtomicReference<Future<?>> task;
    private WorkQueue workQueue;

    public BaseCommand() {
        task = Atomics.newReference();
    }

    @Override
    public void setSession(ServerSession session) {
        this.session = session;
    }

    public void destroy() {
        log.debug("destroying " + getClass().getName());
        Future<?> future = task.getAndSet(null);
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
        session = null;
        ctx = null;
    }

    @Override
    public final void destroy(ChannelSession channel) {
        destroy();
    }

    protected static PrintWriter toPrintWriter(OutputStream o) {
        return new PrintWriter(new BufferedWriter(new OutputStreamWriter(o, Charsets.UTF_8)));
    }

    @Override
    public final void start(ChannelSession channel, Environment env) throws IOException {
        start(env);
    }

    public abstract void start(Environment env) throws IOException;

    protected void provideStateTo(BaseCommand cmd) {
        cmd.setContext(ctx);
        cmd.setWorkQueue(workQueue);
        cmd.setInputStream(in);
        cmd.setOutputStream(out);
        cmd.setErrorStream(err);
        cmd.setExitCallback(exit);
    }

    public WorkQueue getWorkQueue() {
        return workQueue;
    }

    public void setWorkQueue(WorkQueue workQueue) {
        this.workQueue = workQueue;
    }

    public void setContext(SshCommandContext ctx) {
        this.ctx = ctx;
    }

    public SshCommandContext getContext() {
        return ctx;
    }

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
    public void setExitCallback(ExitCallback callback) {
        this.exit = callback;
    }

    protected String getName() {
        return commandName;
    }

    void setName(String prefix) {
        this.commandName = prefix;
    }

    public String[] getArguments() {
        return argv;
    }

    public void setArguments(String[] argv) {
        this.argv = argv;
    }

    protected void parseCommandLine() throws UnloggedFailure {
        parseCommandLine(this);
    }

    protected void parseCommandLine(Object options) throws UnloggedFailure {
        final CmdLineParser clp = newCmdLineParser(options);
        try {
            clp.parseArgument(argv);
        } catch (IllegalArgumentException err) {
            if (!clp.wasHelpRequestedByOption()) {
                throw new UnloggedFailure(1, "fatal: " + err.getMessage());
            }
        } catch (CmdLineException err) {
            if (!clp.wasHelpRequestedByOption()) {
                throw new UnloggedFailure(1, "fatal: " + err.getMessage());
            }
        }

        if (clp.wasHelpRequestedByOption()) {
            CommandMetaData meta = getClass().getAnnotation(CommandMetaData.class);
            String title = meta.name().toUpperCase() + ": " + meta.description();
            String b = com.gitblit.utils.StringUtils.leftPad("", title.length() + 2, '═');
            StringWriter msg = new StringWriter();
            msg.write('\n');
            msg.write(b);
            msg.write('\n');
            msg.write(' ');
            msg.write(title);
            msg.write('\n');
            msg.write(b);
            msg.write("\n\n");
            msg.write("USAGE\n");
            msg.write("─────\n");
            msg.write(' ');
            msg.write(commandName);
            msg.write('\n');
            msg.write("  ");
            clp.printSingleLineUsage(msg, null);
            msg.write("\n\n");
            String txt = getUsageText();
            if (!StringUtils.isEmpty(txt)) {
                msg.write(txt);
                msg.write("\n\n");
            }
            msg.write("ARGUMENTS & OPTIONS\n");
            msg.write("───────────────────\n");
            clp.printUsage(msg, null);
            msg.write('\n');
            String examples = usage().trim();
            if (!StringUtils.isEmpty(examples)) {
                msg.write('\n');
                msg.write("EXAMPLES\n");
                msg.write("────────\n");
                msg.write(examples);
                msg.write('\n');
            }

            throw new UnloggedFailure(1, msg.toString());
        }
    }

    protected CmdLineParser newCmdLineParser(Object options) {
        return new CmdLineParser(options);
    }

    public String usage() {
        Class<? extends BaseCommand> clazz = getClass();
        if (clazz.isAnnotationPresent(UsageExamples.class)) {
            return examples(clazz.getAnnotation(UsageExamples.class).examples());
        } else if (clazz.isAnnotationPresent(UsageExample.class)) {
            return examples(clazz.getAnnotation(UsageExample.class));
        }
        return "";
    }

    protected String getUsageText() {
        return "";
    }

    protected String examples(UsageExample... examples) {
        int sshPort = getContext().getGitblit().getSettings().getInteger(Keys.git.sshPort, 29418);
        String username = getContext().getClient().getUsername();
        String hostname = "localhost";
        String ssh = String.format("ssh -l %s -p %d %s", username, sshPort, hostname);

        StringBuilder sb = new StringBuilder();
        for (UsageExample example : examples) {
            sb.append(example.description()).append("\n\n");
            String syntax = example.syntax();
            syntax = syntax.replace("${ssh}", ssh);
            syntax = syntax.replace("${username}", username);
            syntax = syntax.replace("${cmd}", commandName);
            sb.append("   ").append(syntax).append("\n\n");
        }
        return sb.toString();
    }

    protected void showHelp() throws UnloggedFailure {
        argv = new String[] { "--help" };
        parseCommandLine();
    }

    private final class TaskThunk implements CancelableRunnable {
        private final CommandRunnable thunk;
        private final String taskName;

        private TaskThunk(CommandRunnable thunk) {
            this.thunk = thunk;
            this.taskName = ctx.getCommandLine();
        }

        @Override
        public void cancel() {
            synchronized (this) {
                try {
                    onExit(STATUS_CANCEL);
                } finally {
                    ctx = null;
                }
            }
        }

        @Override
        public void run() {
            synchronized (this) {
                final Thread thisThread = Thread.currentThread();
                final String thisName = thisThread.getName();
                int rc = 0;
                try {
                    thisThread.setName("SSH " + taskName);
                    thunk.run();

                    out.flush();
                    err.flush();
                } catch (Throwable e) {
                    try {
                        out.flush();
                    } catch (Throwable e2) {
                    }
                    try {
                        err.flush();
                    } catch (Throwable e2) {
                    }
                    rc = handleError(e);
                } finally {
                    try {
                        onExit(rc);
                    } finally {
                        thisThread.setName(thisName);
                    }
                }
            }
        }

        @Override
        public String toString() {
            return taskName;
        }
    }

    public interface CommandRunnable {
        void run() throws Exception;
    }

    public interface RepositoryCommandRunnable extends CommandRunnable {
        String getRepository();
    }

    protected void startThread(final Runnable thunk) {
        startThread(new CommandRunnable() {
            @Override
            public void run() throws Exception {
                thunk.run();
            }
        });
    }

    protected void onExit(final int rc) {
        exit.onExit(rc);
    }

    private int handleError(final Throwable e) {
        if ((e.getClass() == IOException.class && "Pipe closed".equals(e.getMessage()))
                || (e.getClass() == SshException.class && "Already closed".equals(e.getMessage()))
                || e.getClass() == InterruptedIOException.class) {
            return 127;
        }

        if (!(e instanceof UnloggedFailure)) {
            final StringBuilder m = new StringBuilder();
            m.append("Internal server error");
            String user = ctx.getClient().getUsername();
            if (user != null) {
                m.append(" (user ");
                m.append(user);
                m.append(")");
            }
            m.append(" during ");
            m.append(ctx.getCommandLine());
            log.error(m.toString(), e);
        }

        if (e instanceof Failure) {
            final Failure f = (Failure) e;
            try {
                err.write((f.getMessage() + "\n").getBytes(Charsets.UTF_8));
                err.flush();
            } catch (IOException e2) {
            } catch (Throwable e2) {
                log.warn("Cannot send failure message to client", e2);
            }
            return f.exitCode;
        }

        try {
            err.write("fatal: internal server error\n".getBytes(Charsets.UTF_8));
            err.flush();
        } catch (IOException e2) {
        } catch (Throwable e2) {
            log.warn("Cannot send internal server error message to client", e2);
        }
        return 128;
    }

    protected void startThread(final CommandRunnable thunk) {
        final TaskThunk tt = new TaskThunk(thunk);
        task.set(workQueue.getDefaultQueue().submit(tt));
    }

    public static class Failure extends Exception {
        private static final long serialVersionUID = 1L;

        final int exitCode;

        public Failure(final int exitCode, final String msg) {
            this(exitCode, msg, null);
        }

        public Failure(final int exitCode, final String msg, final Throwable why) {
            super(msg, why);
            this.exitCode = exitCode;
        }
    }

    public static class UnloggedFailure extends Failure {
        private static final long serialVersionUID = 1L;

        public UnloggedFailure(final String msg) {
            this(1, msg);
        }

        public UnloggedFailure(final int exitCode, final String msg) {
            this(exitCode, msg, null);
        }

        public UnloggedFailure(final int exitCode, final String msg, final Throwable why) {
            super(exitCode, msg, why);
        }
    }
}
