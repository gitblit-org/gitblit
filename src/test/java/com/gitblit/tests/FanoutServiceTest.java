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
package com.gitblit.tests;

import static org.junit.Assert.*;

import java.text.MessageFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.gitblit.fanout.FanoutClient;
import com.gitblit.fanout.FanoutClient.FanoutAdapter;
import com.gitblit.fanout.FanoutNioService;
import com.gitblit.fanout.FanoutService;
import com.gitblit.fanout.FanoutSocketService;

public class FanoutServiceTest {

	int fanoutPort = FanoutService.DEFAULT_PORT;

	@Test
	public void testNioPubSub() throws Exception {
		testPubSub(new FanoutNioService(fanoutPort));
	}

	@Test
	public void testSocketPubSub() throws Exception {
		testPubSub(new FanoutSocketService(fanoutPort));
	}

	@Test
	public void testNioDisruptionAndRecovery() throws Exception {
		testDisruption(new FanoutNioService(fanoutPort));
	}

	@Test
	public void testSocketDisruptionAndRecovery() throws Exception {
		testDisruption(new FanoutSocketService(fanoutPort));
	}

	protected void testPubSub(FanoutService service) throws Exception {
		System.out.println(MessageFormat.format("\n\n========================================\nPUBSUB TEST {0}\n========================================\n\n", service.toString()));
		service.startSynchronously();

		final Map<String, String> announcementsA = new ConcurrentHashMap<String, String>();
		FanoutClient clientA = new FanoutClient("localhost", fanoutPort);
		clientA.addListener(new FanoutAdapter() {

			@Override
			public void announcement(String channel, String message) {
				announcementsA.put(channel, message);
			}
		});

		clientA.startSynchronously();

		final Map<String, String> announcementsB = new ConcurrentHashMap<String, String>();
		FanoutClient clientB = new FanoutClient("localhost", fanoutPort);
		clientB.addListener(new FanoutAdapter() {
			@Override
			public void announcement(String channel, String message) {
				announcementsB.put(channel, message);
			}
		});
		clientB.startSynchronously();


		// subscribe clients A and B to the channels
		clientA.subscribe("a");
		clientA.subscribe("b");
		clientA.subscribe("c");

		clientB.subscribe("a");
		clientB.subscribe("b");
		clientB.subscribe("c");

		// give async messages a chance to be delivered
		Thread.sleep(1000);

		clientA.announce("a", "apple");
		clientA.announce("b", "banana");
		clientA.announce("c", "cantelope");

		clientB.announce("a", "avocado");
		clientB.announce("b", "beet");
		clientB.announce("c", "carrot");

		// give async messages a chance to be delivered
		Thread.sleep(2000);

		// confirm that client B received client A's announcements
		assertEquals("apple", announcementsB.get("a"));
		assertEquals("banana", announcementsB.get("b"));
		assertEquals("cantelope", announcementsB.get("c"));

		// confirm that client A received client B's announcements
		assertEquals("avocado", announcementsA.get("a"));
		assertEquals("beet", announcementsA.get("b"));
		assertEquals("carrot", announcementsA.get("c"));

		clientA.stop();
		clientB.stop();
		service.stop();
	}

	protected void testDisruption(FanoutService service) throws Exception  {
		System.out.println(MessageFormat.format("\n\n========================================\nDISRUPTION TEST {0}\n========================================\n\n", service.toString()));
		service.startSynchronously();

		final AtomicInteger pongCount = new AtomicInteger(0);
		FanoutClient client = new FanoutClient("localhost", fanoutPort);
		client.addListener(new FanoutAdapter() {
			@Override
			public void pong(Date timestamp) {
				pongCount.incrementAndGet();
			}
		});
		client.startSynchronously();

		// ping and wait for pong
		client.ping();
		Thread.sleep(500);

		// restart client
		client.stop();
		Thread.sleep(1000);
		client.startSynchronously();

		// ping and wait for pong
		client.ping();
		Thread.sleep(500);

		assertEquals(2, pongCount.get());

		// now disrupt service
		service.stop();
		Thread.sleep(2000);
		service.startSynchronously();

		// wait for reconnect
		Thread.sleep(2000);

		// ping and wait for pong
		client.ping();
		Thread.sleep(500);

		// kill all
		client.stop();
		service.stop();

		// confirm expected pong count
		assertEquals(3, pongCount.get());
	}
}