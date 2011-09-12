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

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
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

	private final Set<Message> failures = Collections.synchronizedSet(new HashSet<Message>());

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
			InternetAddress from = new InternetAddress(settings.getString(Keys.mail.fromAddress,
					"gitblit@gitblit.com"), "Gitblit");
			message.setFrom(from);

			InternetAddress[] tos = new InternetAddress[toAddresses.size()];
			for (int i = 0; i < toAddresses.size(); i++) {
				tos[i] = new InternetAddress(toAddresses.get(i));
			}
			message.setRecipients(Message.RecipientType.TO, tos);
			message.setSentDate(new Date());
		} catch (Exception e) {
			logger.error("Failed to properly create message", e);
		}
		return message;
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
				Message message = null;
				while ((message = queue.peek()) != null) {
					try {
						if (settings.getBoolean(Keys.mail.debug, false)) {
							logger.info("send: "
									+ StringUtils.trimString(
											message.getSubject()
													+ " => "
													+ message.getRecipients(RecipientType.TO)[0]
															.toString(), 60));
						}
						Transport.send(message);
						queue.remove();
						failures.remove(message);
					} catch (Throwable e) {
						if (!failures.contains(message)) {
							logger.error("Failed to send message", e);
							failures.add(message);
						}
					}
				}
			}
		} else {
			// log message to console and drop
			if (!queue.isEmpty()) {
				Message message = null;
				while ((message = queue.peek()) != null) {
					try {
						logger.info("drop: "
								+ StringUtils.trimString(
										(message.getSubject())
												+ " => "
												+ message.getRecipients(RecipientType.TO)[0]
														.toString(), 60));
						queue.remove();
						failures.remove(message);
					} catch (Throwable e) {
						if (!failures.contains(message)) {
							logger.error("Failed to remove message from queue");
							failures.add(message);
						}
					}
				}
			}
		}
	}
}
