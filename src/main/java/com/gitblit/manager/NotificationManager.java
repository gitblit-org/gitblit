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

import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.mail.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.models.Mailing;
import com.gitblit.service.MailService;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * The notification manager dispatches notifications.  Currently, email is the
 * only supported transport, however there is no reason why other transports
 * could be supported (tweets, irc, sms, etc).
 *
 * @author James Moger
 *
 */
@Singleton
public class NotificationManager implements INotificationManager {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);

	private final IStoredSettings settings;

	private final MailService mailService;

	@Inject
	public NotificationManager(IStoredSettings settings) {
		this.settings = settings;
		this.mailService = new MailService(settings);
	}

	@Override
	public NotificationManager start() {
		if (mailService.isReady()) {
			int period = 2;
			logger.info("Mail service will process the queue every {} minutes.", period);
			scheduledExecutor.scheduleAtFixedRate(mailService, 1, period, TimeUnit.MINUTES);
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

	@Override
	public boolean isSendingMail() {
		return mailService.isReady();
	}

	/**
	 * Notify the administrators by email.
	 *
	 * @param subject
	 * @param message
	 */
	@Override
	public void sendMailToAdministrators(String subject, String message) {
		Mailing mail = Mailing.newPlain();
		mail.subject = subject;
		mail.content = message;
		mail.setRecipients(settings.getStrings(Keys.mail.adminAddresses));
		send(mail);
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
		Mailing mail = Mailing.newPlain();
		mail.subject = subject;
		mail.content = message;
		mail.setRecipients(toAddresses);
		send(mail);
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
		Mailing mail = Mailing.newHtml();
		mail.subject = subject;
		mail.content = message;
		mail.setRecipients(toAddresses);
		send(mail);
	}

	/**
	 * Notify users by email of something.
	 *
	 * @param mailing
	 */
	@Override
	public void send(Mailing mailing) {
		if (!mailing.hasRecipients()) {
			logger.debug("Dropping message {} because there are no recipients", mailing.subject);
			return;
		}
		Message msg = mailService.createMessage(mailing);
		if (msg != null) {
			mailService.queue(msg);
		}
	}

}
