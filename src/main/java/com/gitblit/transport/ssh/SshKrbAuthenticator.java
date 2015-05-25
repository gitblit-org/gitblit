package com.gitblit.transport.ssh;

import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.models.UserModel;
import java.util.Locale;
import org.apache.sshd.server.auth.gss.GSSAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SshKrbAuthenticator extends GSSAuthenticator {
	
	protected final Logger log = LoggerFactory.getLogger(getClass());
	protected final IAuthenticationManager authManager;

	public SshKrbAuthenticator(IAuthenticationManager authManager) {
		this.authManager = authManager;
		log.info("registry  {}", authManager);
	}

	public boolean validateIdentity(ServerSession session, String identity) {
		log.info("identify with kerberos {}", identity);
		SshDaemonClient client = (SshDaemonClient)session.getAttribute(SshDaemonClient.KEY);
		if (client.getUser() != null) {
			log.info("{} has already authenticated!", identity);
			return true;
		}
		String username = identity.toLowerCase(Locale.US);
		UserModel user = authManager.authenticate(username);
		if (user != null) {
			client.setUser(user);
			return true;
		}
		log.warn("could not authenticate {} for SSH", username);
		return false;
	}
}
