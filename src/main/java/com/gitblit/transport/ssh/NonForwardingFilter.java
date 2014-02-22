package com.gitblit.transport.ssh;

import java.net.InetSocketAddress;

import org.apache.sshd.server.ForwardingFilter;
import org.apache.sshd.server.session.ServerSession;

public class NonForwardingFilter implements ForwardingFilter {
	@Override
	public boolean canForwardAgent(ServerSession session) {
		return false;
	}

	@Override
	public boolean canForwardX11(ServerSession session) {
		return false;
	}

	@Override
	public boolean canConnect(InetSocketAddress address, ServerSession session) {
		return false;
	}

	@Override
	public boolean canListen(InetSocketAddress address, ServerSession session) {
		return false;
	}
}
