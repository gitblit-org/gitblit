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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.utils.StringUtils;

/**
 * The mail executor handles sending email messages asynchronously from queue.
 * 
 * @author James Moger
 * 
 */
public class MailExecutor implements Runnable {

	private final Logger logger = LoggerFactory.getLogger(MailExecutor.class);

	private final Queue<Message> queue = new ConcurrentLinkedQueue<Message>();

	private final Session session;

	private final IStoredSettings settings;

	public MailExecutor(IStoredSettings settings) {
		this.settings = settings;

		final String mailUser = settings.getString(Keys.mail.username, null);
		final String mailPassword = settings.getString(Keys.mail.password, null);
		boolean authenticate = !StringUtils.isEmpty(mailUser) && !StringUtils.isEmpty(mailPassword);
		String server = settings.getString(Keys.mail.server, "");
		if (StringUtils.isEmpty(server)) {
			session = null;
			return;
		}
		int port = settings.getInteger(Keys.mail.port, 25);
		boolean isGMail = false;
		if (server.equals("smtp.gmail.com")) {
			port = 465;
			isGMail = true;
		}

		Properties props = new Properties();
		props.setProperty("mail.smtp.host", server);
		props.setProperty("mail.smtp.port", String.valueOf(port));
		props.setProperty("mail.smtp.auth", String.valueOf(authenticate));
		props.setProperty("mail.smtp.auths", String.valueOf(authenticate));

		if (isGMail) {
			props.setProperty("mail.smtp.starttls.enable", "true");
			props.put("mail.smtp.socketFactory.port", String.valueOf(port));
			props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
			props.put("mail.smtp.socketFactory.fallback", "false");
		}

		if (!StringUtils.isEmpty(mailUser) && !StringUtils.isEmpty(mailPassword)) {
			// SMTP requires authentication
			session = Session.getInstance(props, new Authenticator() {
				protected PasswordAuthentication getPasswordAuthentication() {
					PasswordAuthentication passwordAuthentication = new PasswordAuthentication(
							mailUser, mailPassword);
					return passwordAuthentication;
				}
			});
		} else {
			// SMTP does not require authentication
			session = Session.getInstance(props);
		}
	}

	/**
	 * Indicates if the mail executor can send emails.
	 * 
	 * @return true if the mail executor is ready to send emails
	 */
	public boolean isReady() {
		return session != null;
	}

	/**
	 * Creates a message for the administrators.
	 * 
	 * @returna message
	 */
	public Message createMessageForAdministrators() {
		List<String> toAddresses = settings.getStrings(Keys.mail.adminAddresses);
		if (toAddresses.size() == 0) {
			logger.warn("Can not notify administrators because no email addresses are defined!");
			return null;
		}
		return createMessage(toAddresses);
	}

	/**
	 * Create a message.
	 * 
	 * @param toAddresses
	 * @return a message
	 */
	public Message createMessage(String... toAddresses) {
		return createMessage(Arrays.asList(toAddresses));
	}

	/**
	 * Create a message.
	 * 
	 * @param toAddresses
	 * @return a message
	 */
	public Message createMessage(List<String> toAddresses) {
		MimeMessage message = new MimeMessage(session);
		try {
			String fromAddress = settings.getString(Keys.mail.fromAddress, null);
			if (StringUtils.isEmpty(fromAddress)) {
				fromAddress = "gitblit@gitblit.com";
			}
			InternetAddress from = new InternetAddress(fromAddress, "Gitblit");
			message.setFrom(from);

			// determine unique set of addresses
			Set<String> uniques = new HashSet<String>();
			for (String address : toAddresses) {
				uniques.add(address.toLowerCase());
			}
			
			Pattern validEmail = Pattern
					.compile("^([a-zA-Z0-9_\\-\\.]+)@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.)|(([a-zA-Z0-9\\-]+\\.)+))([a-zA-Z]{2,4}|[0-9]{1,3})(\\]?)$");
			List<InternetAddress> tos = new ArrayList<InternetAddress>();
			for (String address : uniques) {
				if (StringUtils.isEmpty(address)) {
					continue;
				}
				if (validEmail.matcher(address).find()) {
					try {
						tos.add(new InternetAddress(address));
					} catch (Throwable t) {
					}
				}
			}			
			message.setRecipients(Message.RecipientType.BCC,
					tos.toArray(new InternetAddress[tos.size()]));
			message.setSentDate(new Date());
		} catch (Exception e) {
			logger.error("Failed to properly create message", e);
		}
		return message;
	}

	/**
	 * Returns the status of the mail queue.
	 * 
	 * @return true, if the queue is empty
	 */
	public boolean hasEmptyQueue() {
		return queue.isEmpty();
	}

	/**
	 * Queue's an email message to be sent.
	 * 
	 * @param message
	 * @return true if the message was queued
	 */
	public boolean queue(Message message) {
		if (!isReady()) {
			return false;
		}
		try {
			message.saveChanges();
		} catch (Throwable t) {
			logger.error("Failed to save changes to message!", t);
		}
		queue.add(message);
		return true;
	}

	@Override
	public void run() {
		if (!queue.isEmpty()) {
			if (session != null) {
				// send message via mail server
				List<Message> failures = new ArrayList<Message>();
				Message message = null;
				while ((message = queue.poll()) != null) {
					try {
						if (settings.getBoolean(Keys.mail.debug, false)) {
							logger.info("send: " + StringUtils.trimString(message.getSubject(), 60));
						}
						Transport.send(message);
					} catch (Throwable e) {
						logger.error("Failed to send message", e);
						failures.add(message);
					}
				}
				
				// push the failures back onto the queue for the next cycle
				queue.addAll(failures);
			}
		}
	}
}
