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

import static org.junit.Assert.assertTrue;

import javax.mail.Message;

import org.junit.Test;

import com.gitblit.FileSettings;
import com.gitblit.MailExecutor;

public class MailTest {

	@Test
	public void testSendMail() throws Exception {
		FileSettings settings = new FileSettings("mailtest.properties");
		MailExecutor mail = new MailExecutor(settings);
		Message message = mail.createMessageForAdministrators();
		message.setSubject("Test");
		message.setText("this is a test");
		mail.queue(message);
		mail.run();

		assertTrue("mail queue is not empty!", mail.hasEmptyQueue());
	}
}
