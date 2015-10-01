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
package com.gitblit.service;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.models.Mailing;
import com.gitblit.utils.StringUtils;

/**
 * The mail service handles sending email messages asynchronously from a queue.
 *
 * @author James Moger
 *
 */
public class MailService implements Runnable {

	private final Logger logger = LoggerFactory.getLogger(MailService.class);

	private final Queue<Message> queue = new ConcurrentLinkedQueue<Message>();

	private final Session session;

	private final IStoredSettings settings;

	public MailService(IStoredSettings settings) {
		this.settings = settings;

		final String mailUser = settings.getString(Keys.mail.username, null);
		final String mailPassword = settings.getString(Keys.mail.password, null);
		final boolean smtps = settings.getBoolean(Keys.mail.smtps, false);
		final boolean starttls = settings.getBoolean(Keys.mail.starttls, false);
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
		props.setProperty("mail.smtp.starttls.enable", String.valueOf(starttls));

		if (isGMail || smtps) {
			props.setProperty("mail.smtp.starttls.enable", "true");
			props.put("mail.smtp.socketFactory.port", String.valueOf(port));
			props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
			props.put("mail.smtp.socketFactory.fallback", "false");
		}

		if (!StringUtils.isEmpty(mailUser) && !StringUtils.isEmpty(mailPassword)) {
			// SMTP requires authentication
			session = Session.getInstance(props, new Authenticator() {
				@Override
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
	 * Create a message.
	 *
	 * @param mailing
	 * @return a message
	 */
	public Message createMessage(Mailing mailing) {
		if (mailing.subject == null) {
			mailing.subject = "";
		}

		if (mailing.content == null) {
			mailing.content = "";
		}

		Message message = new MailMessageImpl(session, mailing.id);
		try {
			String fromAddress = settings.getString(Keys.mail.fromAddress, null);
			if (StringUtils.isEmpty(fromAddress)) {
				fromAddress = "gitblit@gitblit.com";
			}
			InternetAddress from = new InternetAddress(fromAddress, mailing.from == null ? "Gitblit" : mailing.from);
			message.setFrom(from);

			Pattern validEmail = Pattern
					.compile("^([a-zA-Z0-9_\\-\\.]+)@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.)|(([a-zA-Z0-9\\-]+\\.)+))([a-zA-Z]{2,4}|[0-9]{1,3})(\\]?)$");

			// validate & add TO recipients
			List<InternetAddress> to = new ArrayList<InternetAddress>();
			for (String address : mailing.toAddresses) {
				if (StringUtils.isEmpty(address)) {
					continue;
				}
				if (validEmail.matcher(address).find()) {
					try {
						to.add(new InternetAddress(address));
					} catch (Throwable t) {
					}
				}
			}

			// validate & add CC recipients
			List<InternetAddress> cc = new ArrayList<InternetAddress>();
			for (String address : mailing.ccAddresses) {
				if (StringUtils.isEmpty(address)) {
					continue;
				}
				if (validEmail.matcher(address).find()) {
					try {
						cc.add(new InternetAddress(address));
					} catch (Throwable t) {
					}
				}
			}

			if (settings.getBoolean(Keys.web.showEmailAddresses, true)) {
				// full disclosure of recipients
				if (to.size() > 0) {
					message.setRecipients(Message.RecipientType.TO,
							to.toArray(new InternetAddress[to.size()]));
				}
				if (cc.size() > 0) {
					message.setRecipients(Message.RecipientType.CC,
							cc.toArray(new InternetAddress[cc.size()]));
				}
			} else {
				// everyone is bcc'd
				List<InternetAddress> bcc = new ArrayList<InternetAddress>();
				bcc.addAll(to);
				bcc.addAll(cc);
				message.setRecipients(Message.RecipientType.BCC,
						bcc.toArray(new InternetAddress[bcc.size()]));
			}

			message.setSentDate(new Date());
			// UTF-8 encode
			message.setSubject(MimeUtility.encodeText(mailing.subject, "utf-8", "B"));

			MimeBodyPart messagePart = new MimeBodyPart();
			messagePart.setText(mailing.content, "utf-8");
			//messagePart.setHeader("Content-Transfer-Encoding", "quoted-printable");

			if (Mailing.Type.html == mailing.type) {
				messagePart.setHeader("Content-Type", "text/html; charset=\"utf-8\"");
			} else {
				messagePart.setHeader("Content-Type", "text/plain; charset=\"utf-8\"");
			}

			MimeMultipart multiPart = new MimeMultipart();
			multiPart.addBodyPart(messagePart);

			// handle attachments
			if (mailing.hasAttachments()) {
				for (File file : mailing.attachments) {
					if (file.exists()) {
						MimeBodyPart filePart = new MimeBodyPart();
						FileDataSource fds = new FileDataSource(file);
						filePart.setDataHandler(new DataHandler(fds));
						filePart.setFileName(fds.getName());
						multiPart.addBodyPart(filePart);
					}
				}
			}

			message.setContent(multiPart);

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

	public void sendNow(Message message) throws Exception {
		Transport.send(message);
	}

	private static class MailMessageImpl extends MimeMessage {

		final String id;

		MailMessageImpl(Session session, String id) {
			super(session);
			this.id = id;
		}

		@Override
		protected void updateMessageID() throws MessagingException {
			if (!StringUtils.isEmpty(id)) {
				String hostname = "gitblit.com";
				String refid = "<" + id + "@" + hostname + ">";
				String mid = "<" + UUID.randomUUID().toString() + "@" + hostname + ">";
				setHeader("References", refid);
				setHeader("In-Reply-To", refid);
				setHeader("Message-Id", mid);
			}
		}
	}
}
