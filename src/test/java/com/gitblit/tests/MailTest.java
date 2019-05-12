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
package com.gitblit.tests;

import static org.junit.Assert.*;

import javax.mail.Message;

import org.junit.Test;

import com.gitblit.FileSettings;
import com.gitblit.Keys;
import com.gitblit.models.Mailing;
import com.gitblit.service.MailService;

public class MailTest {

	@Test
	public void testSendMail() throws Exception {
		FileSettings settings = new FileSettings("mailtest.properties");
		MailService mail = new MailService(settings);
		Mailing mailing = Mailing.newPlain();
		mailing.setRecipients(settings.getStrings(Keys.mail.adminAddresses));
		Message message = mail.createMessage(mailing);
		message.setSubject("Test");
		message.setText("﻿Lägger till andra stycket i ny fil. UTF-8 encoded");
		mail.queue(message);
		mail.run();

		assertTrue("mail queue is not empty!", mail.hasEmptyQueue());
	}
}
