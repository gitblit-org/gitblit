package com.gitblit.transport.ssh;

import org.apache.sshd.common.ForwardingFilter;
import org.apache.sshd.common.Session;
import org.apache.sshd.common.SshdSocketAddress;

public class NonForwardingFilter implements ForwardingFilter {
	@Override
	public boolean canConnect(SshdSocketAddress address, Session session) {
		return false;
	}

	@Override
	public boolean canForwardAgent(Session session) {
		return false;
	}

	@Override
	public boolean canForwardX11(Session session) {
		return false;
	}

	@Override
	public boolean canListen(SshdSocketAddress address, Session session) {
		return false;
	}
}
