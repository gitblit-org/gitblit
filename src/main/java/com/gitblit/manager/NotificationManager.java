/*
 * Copyright 2013 gitblit.com.
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
package com.gitblit.manager;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.service.MailService;

/**
 * The notification manager dispatches notifications.  Currently, email is the
 * only supported transport, however there is no reason why other transports
 * could be supported (tweets, irc, sms, etc).
 *
 * @author James Moger
 *
 */
public class NotificationManager implements INotificationManager {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);

	private final IStoredSettings settings;

	private final MailService mailExecutor;

	public NotificationManager(IStoredSettings settings) {
		this.settings = settings;
		this.mailExecutor = new MailService(settings);
	}

	@Override
	public NotificationManager start() {
		if (mailExecutor.isReady()) {
			int period = 2;
			logger.info("Mail service will process the queue every {} minutes.", period);
			scheduledExecutor.scheduleAtFixedRate(mailExecutor, 1, period, TimeUnit.MINUTES);
		} else {
			logger.warn("Mail service disabled.");
		}
		return this;
	}

	@Override
	public NotificationManager stop() {
		scheduledExecutor.shutdownNow();
		return this;
	}

	/**
	 * Notify the administrators by email.
	 *
	 * @param subject
	 * @param message
	 */
	@Override
	public void sendMailToAdministrators(String subject, String message) {
		List<String> toAddresses = settings.getStrings(Keys.mail.adminAddresses);
		sendMail(subject, message, toAddresses);
	}

	/**
	 * Notify users by email of something.
	 *
	 * @param subject
	 * @param message
	 * @param toAddresses
	 */
	@Override
	public void sendMail(String subject, String message, Collection<String> toAddresses) {
		this.sendMail(subject, message, toAddresses.toArray(new String[0]));
	}

	/**
	 * Notify users by email of something.
	 *
	 * @param subject
	 * @param message
	 * @param toAddresses
	 */
	@Override
	public void sendMail(String subject, String message, String... toAddresses) {
		if (toAddresses == null || toAddresses.length == 0) {
			logger.debug(MessageFormat.format("Dropping message {0} because there are no recipients", subject));
			return;
		}
		try {
			Message mail = mailExecutor.createMessage(toAddresses);
			if (mail != null) {
				mail.setSubject(subject);

				MimeBodyPart messagePart = new MimeBodyPart();
				messagePart.setText(message, "utf-8");
				messagePart.setHeader("Content-Type", "text/plain; charset=\"utf-8\"");
				messagePart.setHeader("Content-Transfer-Encoding", "quoted-printable");

				MimeMultipart multiPart = new MimeMultipart();
				multiPart.addBodyPart(messagePart);
				mail.setContent(multiPart);

				mailExecutor.queue(mail);
			}
		} catch (MessagingException e) {
			logger.error("Messaging error", e);
		}
	}

	/**
	 * Notify users by email of something.
	 *
	 * @param subject
	 * @param message
	 * @param toAddresses
	 */
	@Override
	public void sendHtmlMail(String subject, String message, Collection<String> toAddresses) {
		this.sendHtmlMail(subject, message, toAddresses.toArray(new String[0]));
	}

	/**
	 * Notify users by email of something.
	 *
	 * @param subject
	 * @param message
	 * @param toAddresses
	 */
	@Override
	public void sendHtmlMail(String subject, String message, String... toAddresses) {
		if (toAddresses == null || toAddresses.length == 0) {
			logger.debug(MessageFormat.format("Dropping message {0} because there are no recipients", subject));
			return;
		}
		try {
			Message mail = mailExecutor.createMessage(toAddresses);
			if (mail != null) {
				mail.setSubject(subject);

				MimeBodyPart messagePart = new MimeBodyPart();
				messagePart.setText(message, "utf-8");
				messagePart.setHeader("Content-Type", "text/html; charset=\"utf-8\"");
				messagePart.setHeader("Content-Transfer-Encoding", "quoted-printable");

				MimeMultipart multiPart = new MimeMultipart();
				multiPart.addBodyPart(messagePart);
				mail.setContent(multiPart);

				mailExecutor.queue(mail);
			}
		} catch (MessagingException e) {
			logger.error("Messaging error", e);
		}
	}

}
