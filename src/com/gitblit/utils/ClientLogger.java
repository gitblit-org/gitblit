package com.gitblit.utils;

import org.eclipse.jgit.transport.ReceivePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to log messages to the pushing client.  Intended to be used by
 * the Groovy Hooks.
 * 
 * @author jcrygier
 *
 */
public class ClientLogger {
	
	static final Logger logger = LoggerFactory.getLogger(ClientLogger.class);	
	private ReceivePack rp;
	
	public ClientLogger(ReceivePack rp) {
		this.rp = rp;
	}
	
	/**
	 * Sends a message to the git client.  Useful for sending INFO / WARNING messages.
	 * 
	 * @param message
	 */
	public void sendMessage(String message) {
		rp.sendMessage(message);
	}
	
}
