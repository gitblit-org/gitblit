package com.gitblit.transport.ssh;

import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.apache.sshd.server.Command;

import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class CommandDispatcher extends DispatchCommand {

  Provider<Command> repo;
  Provider<Command> version;

  @Inject
  public CommandDispatcher(final @Named("create-repository") Provider<Command> repo,
      final @Named("version") Provider<Command> version) {
    this.repo = repo;
    this.version = version;
  }

  public DispatchCommand get() {
    DispatchCommand root = new DispatchCommand();
    Map<String, Provider<Command>> origin = Maps.newHashMapWithExpectedSize(2);
    origin.put("gitblit", new Provider<Command>() {
      @Override
      public Command get() {
        Set<Provider<Command>> gitblit = Sets.newHashSetWithExpectedSize(2);
        gitblit.add(repo);
        gitblit.add(version);
        Command cmd = new DispatchCommand(gitblit);
        return cmd;
      }
    });
    root.setMap(origin);
    return root;
  }
}
