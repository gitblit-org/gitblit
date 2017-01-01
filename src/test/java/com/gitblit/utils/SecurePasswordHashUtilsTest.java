package com.gitblit.utils;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class SecurePasswordHashUtilsTest {

	private static final String STORED_PASSWORD = "PBKDF2WITHHMACSHA256:2d7d3ccaa277787f288e9f929247361bfc83607c6a8447bf496267512e360ba0a97b3114937213b23230072517d65a2e00695a1cbc47a732510840817f22c1bc";
	private static final byte[] STORED_SALT_BYTES = new byte[]{45, 125, 60, -54, -94, 119, 120, 127, 40, -114, -97, -110, -110, 71, 54, 27, -4, -125, 96, 124, 106, -124, 71, -65, 73, 98, 103, 81, 46, 54, 11, -96};
	private static final byte[] STORED_HASH_BYTES = new byte[]{-87, 123, 49, 20, -109, 114, 19, -78, 50, 48, 7, 37, 23, -42, 90, 46, 0, 105, 90, 28, -68, 71, -89, 50, 81, 8, 64, -127, 127, 34, -63, -68};
	
	private SecurePasswordHashUtils utils;
	
	@Before
	public void init(){
		utils = SecurePasswordHashUtils.get();
	}
	
	@Test
	public void testGetNextSalt() {
		assertEquals(32, utils.getNextSalt().length);
	}

	@Test
	public void testHash() {
		byte[] hash = utils.hash("foo".toCharArray(), STORED_SALT_BYTES);
		assertArrayEquals(STORED_HASH_BYTES, hash);
	}

	@Test
	public void testIsPasswordCorrectCharArrayByteArrayByteArray() {
		assertTrue(utils.isPasswordCorrect("foo".toCharArray(), STORED_SALT_BYTES, STORED_HASH_BYTES));
		assertFalse(utils.isPasswordCorrect("bar".toCharArray(), STORED_SALT_BYTES, STORED_HASH_BYTES));
	}

	@Test
	public void testCreateNewStorableHashFromPassword() {
		String newPwHash = utils.createStoredPasswordFromPassword("foo");
		assertTrue(newPwHash.startsWith(SecurePasswordHashUtils.PBKDF2WITHHMACSHA256_TYPE));
	}

	@Test
	public void testGetSaltFromStoredPassword() {
		byte[] saltFromStoredPassword = utils.getSaltFromStoredPassword(STORED_PASSWORD);
		assertArrayEquals(STORED_SALT_BYTES, saltFromStoredPassword);
		
	}

	@Test
	public void testGetHashFromStoredPassword() {
		byte[] hashFromStoredPassword = utils.getHashFromStoredPassword(STORED_PASSWORD);
		assertArrayEquals(STORED_HASH_BYTES, hashFromStoredPassword);
	}

	@Test
	public void testIsPasswordCorrectCharArrayString() {
		assertTrue(utils.isPasswordCorrect("foo".toCharArray(), STORED_PASSWORD));
		assertFalse(utils.isPasswordCorrect("bar".toCharArray(), STORED_PASSWORD));
	}

}
