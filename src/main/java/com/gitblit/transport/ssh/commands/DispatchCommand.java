// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.gitblit.transport.ssh.commands;

import java.io.IOException;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;

import com.gitblit.git.GitblitReceivePackFactory;
import com.gitblit.git.GitblitUploadPackFactory;
import com.gitblit.git.RepositoryResolver;
import com.gitblit.transport.ssh.AbstractGitCommand;
import com.gitblit.transport.ssh.CommandMetaData;
import com.gitblit.transport.ssh.PublicKeyAuthenticator;
import com.gitblit.transport.ssh.SshDaemonClient;
import com.gitblit.utils.cli.SubcommandHandler;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class DispatchCommand extends BaseCommand {

  @Argument(index = 0, required = false, metaVar = "COMMAND", handler = SubcommandHandler.class)
  private String commandName;

  @Argument(index = 1, multiValued = true, metaVar = "ARG")
  private List<String> args = new ArrayList<String>();

  private Set<Class<? extends Command>> commands;
  private Map<String, Class<? extends Command>> map;
  private Map<String, Command> root;

  public DispatchCommand() {
	  commands = new HashSet<Class<? extends Command>>();
  }

  public void registerDispatcher(String name, Command cmd) {
	  if (root == null) {
		  root = Maps.newHashMap();
	  }
	  root.put(name, cmd);
  }

  public void registerCommand(Class<? extends Command> cmd) {
	  if (!cmd.isAnnotationPresent(CommandMetaData.class)) {
		  throw new RuntimeException(MessageFormat.format("{0} must be annotated with {1}!",
				  cmd.getName(), CommandMetaData.class.getName()));
	  }
	  commands.add(cmd);
  }

  private Map<String, Class<? extends Command>> getMap() {
    if (map == null) {
      map = Maps.newHashMapWithExpectedSize(commands.size());
      for (Class<? extends Command> cmd : commands) {
        CommandMetaData meta = cmd.getAnnotation(CommandMetaData.class);
        map.put(meta.name(), cmd);
      }
    }
    return map;
  }

  @Override
  public void start(Environment env) throws IOException {
    try {
      parseCommandLine();
      if (Strings.isNullOrEmpty(commandName)) {
        StringWriter msg = new StringWriter();
        msg.write(usage());
        throw new UnloggedFailure(1, msg.toString());
      }

      Command cmd = getCommand();
      if (cmd.getClass().isAnnotationPresent(CommandMetaData.class)) {
    	  CommandMetaData meta = cmd.getClass().getAnnotation(CommandMetaData.class);
    	  if (meta.admin() && !ctx.getClient().getUser().canAdmin()) {
    		  throw new UnloggedFailure(1, MessageFormat.format("{0} requires admin permissions", commandName));
    	  }
      }
      if (cmd instanceof BaseCommand) {
        BaseCommand bc = (BaseCommand) cmd;
        if (getName().isEmpty()) {
          bc.setName(commandName);
        } else {
          bc.setName(getName() + " " + commandName);
        }
        bc.setArguments(args.toArray(new String[args.size()]));
      }

      provideBaseStateTo(cmd);
      provideGitState(cmd);
      reset();
      //atomicCmd.set(cmd);
      cmd.start(env);

    } catch (UnloggedFailure e) {
      String msg = e.getMessage();
      if (!msg.endsWith("\n")) {
        msg += "\n";
      }
      err.write(msg.getBytes(Charsets.UTF_8));
      err.flush();
      exit.onExit(e.exitCode);
    }
  }

  private Command getCommand() throws UnloggedFailure {
	if (root != null && root.containsKey(commandName)) {
		return root.get(commandName);
	}
	final Class<? extends Command> c = getMap().get(commandName);
      if (c == null) {
        String msg =
            (getName().isEmpty() ? "Gitblit" : getName()) + ": "
                + commandName + ": not found";
        throw new UnloggedFailure(1, msg);
      }

      Command cmd = null;
      try {
    	  cmd = c.newInstance();
      } catch (Exception e) {
    	  throw new UnloggedFailure(1, MessageFormat.format("Failed to instantiate {0} command", commandName));
      }
	return cmd;
  }

  @Override
  protected String usage() {
    final StringBuilder usage = new StringBuilder();
    usage.append("Available commands");
    if (!getName().isEmpty()) {
      usage.append(" of ");
      usage.append(getName());
    }
    usage.append(" are:\n");
    usage.append("\n");

    int maxLength = -1;
    Map<String, Class<? extends Command>> m = getMap();
    for (String name : m.keySet()) {
      maxLength = Math.max(maxLength, name.length());
    }
    String format = "%-" + maxLength + "s   %s";
    for (String name : Sets.newTreeSet(m.keySet())) {
      final Class<? extends Command> c = m.get(name);
      CommandMetaData meta = c.getAnnotation(CommandMetaData.class);
      if (meta != null) {
        if (meta.admin() && !ctx.getClient().getUser().canAdmin()) {
      	  continue;
        }
        if (meta.hidden()) {
        	continue;
        }
        usage.append("   ");
        usage.append(String.format(format, name,
            Strings.nullToEmpty(meta.description())));
      }
      usage.append("\n");
    }
    usage.append("\n");

    usage.append("See '");
    if (getName().indexOf(' ') < 0) {
      usage.append(getName());
      usage.append(' ');
    }
    usage.append("COMMAND --help' for more information.\n");
    usage.append("\n");
    return usage.toString();
  }

  // This is needed because we are not using provider or
  // clazz.newInstance() for DispatchCommand
  private void reset() {
	  args = new ArrayList<String>();
  }

  private void provideGitState(Command cmd) {
	  if (cmd instanceof AbstractGitCommand) {
		AbstractGitCommand a = (AbstractGitCommand) cmd;
		a.setRepositoryResolver(repositoryResolver);
		a.setUploadPackFactory(gitblitUploadPackFactory);
		a.setReceivePackFactory(gitblitReceivePackFactory);
	  } else if (cmd instanceof DispatchCommand) {
		DispatchCommand d = (DispatchCommand)cmd;
		d.setRepositoryResolver(repositoryResolver);
		d.setUploadPackFactory(gitblitUploadPackFactory);
		d.setReceivePackFactory(gitblitReceivePackFactory);
		d.setAuthenticator(authenticator);
	  } else if (cmd instanceof BaseKeyCommand) {
		  BaseKeyCommand k = (BaseKeyCommand)cmd;
		  k.setAuthenticator(authenticator);
	  }
  }

  private RepositoryResolver<SshDaemonClient> repositoryResolver;
  public void setRepositoryResolver(RepositoryResolver<SshDaemonClient> repositoryResolver) {
	  this.repositoryResolver = repositoryResolver;
  }

  private GitblitUploadPackFactory<SshDaemonClient> gitblitUploadPackFactory;
  public void setUploadPackFactory(GitblitUploadPackFactory<SshDaemonClient> gitblitUploadPackFactory) {
	  this.gitblitUploadPackFactory = gitblitUploadPackFactory;
  }

  private GitblitReceivePackFactory<SshDaemonClient> gitblitReceivePackFactory;
  public void setReceivePackFactory(GitblitReceivePackFactory<SshDaemonClient> gitblitReceivePackFactory) {
	  this.gitblitReceivePackFactory = gitblitReceivePackFactory;
  }

  private PublicKeyAuthenticator authenticator;
  public void setAuthenticator(PublicKeyAuthenticator authenticator) {
	this.authenticator = authenticator;
  }
}
