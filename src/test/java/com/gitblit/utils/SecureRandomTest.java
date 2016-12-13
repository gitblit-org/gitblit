package com.gitblit.utils;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.Test;

public class SecureRandomTest {

	@Test
	public void testRandomBytes() {
		SecureRandom sr = new SecureRandom();
		byte[] bytes1 = sr.randomBytes(10);
		assertEquals(10, bytes1.length);
		byte[] bytes2 = sr.randomBytes(10);
		assertEquals(10, bytes2.length);
		assertFalse(Arrays.equals(bytes1, bytes2));

		assertEquals(0, sr.randomBytes(0).length);
		assertEquals(200, sr.randomBytes(200).length);
	}

	@Test
	public void testNextBytes() {
		SecureRandom sr = new SecureRandom();
		byte[] bytes1 = new byte[32];
		sr.nextBytes(bytes1);
		byte[] bytes2 = new byte[32];
		sr.nextBytes(bytes2);
		assertFalse(Arrays.equals(bytes1, bytes2));
	}
}
