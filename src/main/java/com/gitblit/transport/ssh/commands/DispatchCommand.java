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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Provider;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;

import com.gitblit.transport.ssh.CommandMetaData;
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

  private Set<Provider<Command>> commands;
  private Map<String, Provider<Command>> map;

  public DispatchCommand() {}

  public DispatchCommand(Map<String, Provider<Command>> map) {
    this.map = map;
  }

  public void setMap(Map<String, Provider<Command>> m) {
    map = m;
  }

  public DispatchCommand(Set<Provider<Command>> commands) {
    this.commands = commands;
  }

  private Map<String, Provider<Command>> getMap() {
    if (map == null) {
      map = Maps.newHashMapWithExpectedSize(commands.size());
      for (Provider<Command> cmd : commands) {
        CommandMetaData meta = cmd.get().getClass().getAnnotation(CommandMetaData.class);
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

      final Provider<Command> p = getMap().get(commandName);
      if (p == null) {
        String msg =
            (getName().isEmpty() ? "Gitblit" : getName()) + ": "
                + commandName + ": not found";
        throw new UnloggedFailure(1, msg);
      }

      final Command cmd = p.get();
      if (cmd instanceof BaseCommand) {
        BaseCommand bc = (BaseCommand) cmd;
        if (getName().isEmpty()) {
          bc.setName(commandName);
        } else {
          bc.setName(getName() + " " + commandName);
        }
        bc.setArguments(args.toArray(new String[args.size()]));
      } else if (!args.isEmpty()) {
        throw new UnloggedFailure(1, commandName + " does not take arguments");
      }

      provideStateTo(cmd);
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
    Map<String, Provider<Command>> m = getMap();
    for (String name : m.keySet()) {
      maxLength = Math.max(maxLength, name.length());
    }
    String format = "%-" + maxLength + "s   %s";
    for (String name : Sets.newTreeSet(m.keySet())) {
      final Provider<Command> p = m.get(name);
      usage.append("   ");
      CommandMetaData meta = p.get().getClass().getAnnotation(CommandMetaData.class);
      if (meta != null) {
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
}
