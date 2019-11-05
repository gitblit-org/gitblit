/*
 * Copyright 2017 gitblit.com.
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

package com.gitblit.utils;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * @author Florian Zschocke
 *
 */
public class PasswordHashTest {

	static final String MD5_PASSWORD_0 = "password";
	static final String MD5_HASHED_ENTRY_0 = "MD5:5F4DCC3B5AA765D61D8327DEB882CF99";
	static final String MD5_PASSWORD_1 = "This is a test password";
	static final String MD5_HASHED_ENTRY_1 = "md5:8e1901831af502c0f842d4efb9083bcf";
	static final String CMD5_USERNAME_0 = "Jane Doe";
	static final String CMD5_PASSWORD_0 = "password";
	static final String CMD5_HASHED_ENTRY_0 = "CMD5:DB9639A6E5F21457F9DFD7735FAFA68B";
	static final String CMD5_USERNAME_1 = "Joe Black";
	static final String CMD5_PASSWORD_1 = "ThisIsAWeirdScheme.Weird";
	static final String CMD5_HASHED_ENTRY_1 = "cmd5:5c154768287e32fa605656b98894da89";
	static final String PBKDF2_PASSWORD_0 = "password";
	static final String PBKDF2_HASHED_ENTRY_0 = "PBKDF2:70617373776f726450415353574f524470617373776f726450415353574f52440f17d16621b32ae1bb2b1041fcb19e294b35d514d361c08eed385766e38f6f3a";
	static final String PBKDF2_PASSWORD_1 = "A REALLY better scheme than MD5";
	static final String PBKDF2_HASHED_ENTRY_1 = "PBKDF2:$0$46726573682066726f6d207468652053414c54206d696e65206f6620446f6f6de8e50b035679b25ce8b6ab41440938b7b1f97fc0c797fcf59302c2916f6c8fef";
	static final String PBKDF2_PASSWORD_2 = "passwordPASSWORDpassword";
	static final String PBKDF2_HASHED_ENTRY_2 = "pbkdf2:$0$73616c7453414c5473616c7453414c5473616c7453414c5473616c7453414c54560d0f02b565e37695da15141044506d54cb633a5a70b41c574069ea50a1247a";
	static final String PBKDF2_PASSWORD_3 = "foo";
	static final String PBKDF2_HASHED_ENTRY_3 = "PBKDF2WITHHMACSHA256:2d7d3ccaa277787f288e9f929247361bfc83607c6a8447bf496267512e360ba0a97b3114937213b23230072517d65a2e00695a1cbc47a732510840817f22c1bc";



	/**
	 * Test method for {@link com.gitblit.utils.PasswordHash#instanceOf(java.lang.String)} for MD5.
	 */
	@Test
	public void testInstanceOfMD5() {

		PasswordHash pwdh = PasswordHash.instanceOf("md5");
		assertNotNull(pwdh);
		assertEquals(PasswordHash.Type.MD5, pwdh.type);
		assertTrue("Failed to match " +MD5_HASHED_ENTRY_1, pwdh.matches(MD5_HASHED_ENTRY_1, MD5_PASSWORD_1.toCharArray(), null));

		pwdh = PasswordHash.instanceOf("MD5");
		assertNotNull(pwdh);
		assertEquals(PasswordHash.Type.MD5, pwdh.type);
		assertTrue("Failed to match " +MD5_HASHED_ENTRY_0, pwdh.matches(MD5_HASHED_ENTRY_0, MD5_PASSWORD_0.toCharArray(), null));

		pwdh = PasswordHash.instanceOf("mD5");
		assertNotNull(pwdh);
		assertEquals(PasswordHash.Type.MD5, pwdh.type);
		assertTrue("Failed to match " +MD5_HASHED_ENTRY_1, pwdh.matches(MD5_HASHED_ENTRY_1, MD5_PASSWORD_1.toCharArray(), null));


		pwdh = PasswordHash.instanceOf("CMD5");
		assertNotNull(pwdh);
		assertNotEquals(PasswordHash.Type.MD5, pwdh.type);
		assertFalse("Failed to match " +MD5_HASHED_ENTRY_1, pwdh.matches(MD5_HASHED_ENTRY_1, MD5_PASSWORD_1.toCharArray(), null));
	}



	/**
	 * Test method for {@link com.gitblit.utils.PasswordHash#instanceOf(java.lang.String)} for combined MD5.
	 */
	@Test
	public void testInstanceOfCombinedMD5() {

		PasswordHash pwdh = PasswordHash.instanceOf("cmd5");
		assertNotNull(pwdh);
		assertEquals(PasswordHash.Type.CMD5, pwdh.type);
		assertTrue("Failed to match " +CMD5_HASHED_ENTRY_1, pwdh.matches(CMD5_HASHED_ENTRY_1, CMD5_PASSWORD_1.toCharArray(), CMD5_USERNAME_1));

		pwdh = PasswordHash.instanceOf("cMD5");
		assertNotNull(pwdh);
		assertEquals(PasswordHash.Type.CMD5, pwdh.type);
		assertTrue("Failed to match " +CMD5_HASHED_ENTRY_1, pwdh.matches(CMD5_HASHED_ENTRY_1, CMD5_PASSWORD_1.toCharArray(), CMD5_USERNAME_1));

		pwdh = PasswordHash.instanceOf("CMD5");
		assertNotNull(pwdh);
		assertEquals(PasswordHash.Type.CMD5, pwdh.type);
		assertTrue("Failed to match " +CMD5_HASHED_ENTRY_0, pwdh.matches(CMD5_HASHED_ENTRY_0, CMD5_PASSWORD_0.toCharArray(), CMD5_USERNAME_0));


		pwdh = PasswordHash.instanceOf("MD5");
		assertNotNull(pwdh);
		assertNotEquals(PasswordHash.Type.CMD5, pwdh.type);
		assertFalse("Failed to match " +CMD5_HASHED_ENTRY_1, pwdh.matches(CMD5_HASHED_ENTRY_1, CMD5_PASSWORD_1.toCharArray(), CMD5_USERNAME_1));
	}



	/**
	 * Test method for {@link com.gitblit.utils.PasswordHash#instanceOf(java.lang.String)} for PBKDF2.
	 */
	@Test
	public void testInstanceOfPBKDF2() {
		PasswordHash pwdh = PasswordHash.instanceOf("PBKDF2");
		assertNotNull(pwdh);
		assertEquals(PasswordHash.Type.PBKDF2, pwdh.type);
		assertTrue("Failed to match " +PBKDF2_HASHED_ENTRY_0, pwdh.matches(PBKDF2_HASHED_ENTRY_0, PBKDF2_PASSWORD_0.toCharArray(), null));

		pwdh = PasswordHash.instanceOf("pbkdf2");
		assertNotNull(pwdh);
		assertEquals(PasswordHash.Type.PBKDF2, pwdh.type);
		assertTrue("Failed to match " +PBKDF2_HASHED_ENTRY_1, pwdh.matches(PBKDF2_HASHED_ENTRY_1, PBKDF2_PASSWORD_1.toCharArray(), null));

		pwdh = PasswordHash.instanceOf("pbKDF2");
		assertNotNull(pwdh);
		assertEquals(PasswordHash.Type.PBKDF2, pwdh.type);
		assertTrue("Failed to match " +PBKDF2_HASHED_ENTRY_1, pwdh.matches(PBKDF2_HASHED_ENTRY_1, PBKDF2_PASSWORD_1.toCharArray(), null));


		pwdh = PasswordHash.instanceOf("md5");
		assertNotNull(pwdh);
		assertNotEquals(PasswordHash.Type.PBKDF2, pwdh.type);
		assertFalse("Failed to match " +PBKDF2_HASHED_ENTRY_1, pwdh.matches(PBKDF2_HASHED_ENTRY_1, PBKDF2_PASSWORD_1.toCharArray(), null));
	}




	/**
	 * Test that no instance is returned for plaintext or unknown or not
	 * yet implemented hashing schemes.
	 */
	@Test
	public void testNoInstanceOf() {
		PasswordHash pwdh = PasswordHash.instanceOf("plain");
		assertNull(pwdh);

		pwdh = PasswordHash.instanceOf("PLAIN");
		assertNull(pwdh);

		pwdh = PasswordHash.instanceOf("Plain");
		assertNull(pwdh);

		pwdh = PasswordHash.instanceOf("scrypt");
		assertNull(pwdh);

		pwdh = PasswordHash.instanceOf("bCrypt");
		assertNull(pwdh);

		pwdh = PasswordHash.instanceOf("BCRYPT");
		assertNull(pwdh);

		pwdh = PasswordHash.instanceOf("nixe");
		assertNull(pwdh);

		pwdh = PasswordHash.instanceOf(null);
		assertNull(pwdh);
	}



	/**
	 * Test that for all known hash types an instance is created for a hashed entry
	 * that can verify the known password.
	 *
	 * Test method for {@link com.gitblit.utils.PasswordHash#instanceFor(java.lang.String)}.
	 */
	@Test
	public void testInstanceFor() {
		PasswordHash pwdh = PasswordHash.instanceFor(MD5_HASHED_ENTRY_0);
		assertNotNull(pwdh);
		assertEquals(PasswordHash.Type.MD5, pwdh.type);
		assertTrue("Failed to match " +MD5_HASHED_ENTRY_0, pwdh.matches(MD5_HASHED_ENTRY_0, MD5_PASSWORD_0.toCharArray(), null));

		pwdh = PasswordHash.instanceFor(MD5_HASHED_ENTRY_1);
		assertNotNull(pwdh);
		assertEquals(PasswordHash.Type.MD5, pwdh.type);
		assertTrue("Failed to match " +MD5_HASHED_ENTRY_1, pwdh.matches(MD5_HASHED_ENTRY_1, MD5_PASSWORD_1.toCharArray(), null));


		pwdh = PasswordHash.instanceFor(CMD5_HASHED_ENTRY_0);
		assertNotNull(pwdh);
		assertEquals(PasswordHash.Type.CMD5, pwdh.type);
		assertTrue("Failed to match " +CMD5_HASHED_ENTRY_0, pwdh.matches(CMD5_HASHED_ENTRY_0, CMD5_PASSWORD_0.toCharArray(), CMD5_USERNAME_0));

		pwdh = PasswordHash.instanceFor(CMD5_HASHED_ENTRY_1);
		assertNotNull(pwdh);
		assertEquals(PasswordHash.Type.CMD5, pwdh.type);
		assertTrue("Failed to match " +CMD5_HASHED_ENTRY_1, pwdh.matches(CMD5_HASHED_ENTRY_1, CMD5_PASSWORD_1.toCharArray(), CMD5_USERNAME_1));


		pwdh = PasswordHash.instanceFor(PBKDF2_HASHED_ENTRY_0);
		assertNotNull(pwdh);
		assertEquals(PasswordHash.Type.PBKDF2, pwdh.type);
		assertTrue("Failed to match " +PBKDF2_HASHED_ENTRY_0, pwdh.matches(PBKDF2_HASHED_ENTRY_0, PBKDF2_PASSWORD_0.toCharArray(), null));

		pwdh = PasswordHash.instanceFor(PBKDF2_HASHED_ENTRY_1);
		assertNotNull(pwdh);
		assertEquals(PasswordHash.Type.PBKDF2, pwdh.type);
		assertTrue("Failed to match " +PBKDF2_HASHED_ENTRY_1, pwdh.matches(PBKDF2_HASHED_ENTRY_1, PBKDF2_PASSWORD_1.toCharArray(), null));

		pwdh = PasswordHash.instanceFor(PBKDF2_HASHED_ENTRY_3);
		assertNotNull(pwdh);
		assertEquals(PasswordHash.Type.PBKDF2, pwdh.type);
		assertTrue("Failed to match " +PBKDF2_HASHED_ENTRY_3, pwdh.matches(PBKDF2_HASHED_ENTRY_3, PBKDF2_PASSWORD_3.toCharArray(), null));
	}

	/**
	 * Test that for no instance is returned for plaintext or unknown or
	 * not yet implemented hashing schemes.
	 *
	 * Test method for {@link com.gitblit.utils.PasswordHash#instanceFor(java.lang.String)}.
	 */
	@Test
	public void testInstanceForNaught() {
		PasswordHash pwdh = PasswordHash.instanceFor("password");
		assertNull(pwdh);

		pwdh = PasswordHash.instanceFor("top secret");
		assertNull(pwdh);

		pwdh = PasswordHash.instanceFor("pass:word");
		assertNull(pwdh);

		pwdh = PasswordHash.instanceFor("PLAIN:password");
		assertNull(pwdh);

		pwdh = PasswordHash.instanceFor("SCRYPT:1232rwv12w");
		assertNull(pwdh);

		pwdh = PasswordHash.instanceFor("BCRYPT:urbvahiaufbvhabaiuevuzggubsbliue");
		assertNull(pwdh);

		pwdh = PasswordHash.instanceFor("");
		assertNull(pwdh);

		pwdh = PasswordHash.instanceFor(null);
		assertNull(pwdh);
	}


	/**
	 * Test method for {@link com.gitblit.utils.PasswordHash#isHashedEntry(java.lang.String)}.
	 */
	@Test
	public void testIsHashedEntry() {
		assertTrue(MD5_HASHED_ENTRY_0, PasswordHash.isHashedEntry(MD5_HASHED_ENTRY_0));
		assertTrue(MD5_HASHED_ENTRY_1, PasswordHash.isHashedEntry(MD5_HASHED_ENTRY_1));
		assertTrue(CMD5_HASHED_ENTRY_0, PasswordHash.isHashedEntry(CMD5_HASHED_ENTRY_0));
		assertTrue(CMD5_HASHED_ENTRY_1, PasswordHash.isHashedEntry(CMD5_HASHED_ENTRY_1));
		assertTrue(PBKDF2_HASHED_ENTRY_0, PasswordHash.isHashedEntry(PBKDF2_HASHED_ENTRY_0));
		assertTrue(PBKDF2_HASHED_ENTRY_1, PasswordHash.isHashedEntry(PBKDF2_HASHED_ENTRY_1));
		assertTrue(PBKDF2_HASHED_ENTRY_2, PasswordHash.isHashedEntry(PBKDF2_HASHED_ENTRY_2));
		assertTrue(PBKDF2_HASHED_ENTRY_3, PasswordHash.isHashedEntry(PBKDF2_HASHED_ENTRY_3));

		assertFalse(MD5_PASSWORD_1, PasswordHash.isHashedEntry(MD5_PASSWORD_1));
		assertFalse("topsecret", PasswordHash.isHashedEntry("topsecret"));
		assertFalse("top:secret", PasswordHash.isHashedEntry("top:secret"));
		assertFalse("secret Password", PasswordHash.isHashedEntry("secret Password"));
		assertFalse("Empty string", PasswordHash.isHashedEntry(""));
		assertFalse("Null", PasswordHash.isHashedEntry(null));
	}

	/**
	 * Test that hashed entry detection is not case sensitive on the hash type identifier.
	 *
	 * Test method for {@link com.gitblit.utils.PasswordHash#isHashedEntry(java.lang.String)}.
	 */
	@Test
	public void testIsHashedEntryCaseInsenitive() {
		assertTrue(MD5_HASHED_ENTRY_1.toLowerCase(), PasswordHash.isHashedEntry(MD5_HASHED_ENTRY_1.toLowerCase()));
		assertTrue(CMD5_HASHED_ENTRY_1.toLowerCase(), PasswordHash.isHashedEntry(CMD5_HASHED_ENTRY_1.toLowerCase()));
		assertTrue(PBKDF2_HASHED_ENTRY_1.toLowerCase(), PasswordHash.isHashedEntry(PBKDF2_HASHED_ENTRY_1.toLowerCase()));
		assertTrue(PBKDF2_HASHED_ENTRY_3.toLowerCase(), PasswordHash.isHashedEntry(PBKDF2_HASHED_ENTRY_3.toLowerCase()));
	}

	/**
	 * Test that unknown or not yet implemented hashing schemes are not detected as hashed entries.
	 *
	 * Test method for {@link com.gitblit.utils.PasswordHash#isHashedEntry(java.lang.String)}.
	 */
	@Test
	public void testIsHashedEntryUnknown() {
		assertFalse("BCRYPT:thisismypassword", PasswordHash.isHashedEntry("BCRYPT:thisismypassword"));
		assertFalse("TSTHSH:asdchabufzuzfbhbakrzburzbcuzkuzcbajhbcasjdhbckajsbc", PasswordHash.isHashedEntry("TSTHSH:asdchabufzuzfbhbakrzburzbcuzkuzcbajhbcasjdhbckajsbc"));
	}




	/**
	 * Test creating a hashed entry for scheme MD5. In this scheme there is no salt, so a direct
	 * comparison to a constant value is possible.
	 *
	 * Test method for {@link PasswordHash#toHashedEntry(String, String)} for MD5.
	 */
	@Test
	public void testToHashedEntryMD5() {
		PasswordHash pwdh = PasswordHash.instanceOf("MD5");
		String hashedEntry = pwdh.toHashedEntry(MD5_PASSWORD_1, null);
		assertTrue(MD5_HASHED_ENTRY_1.equalsIgnoreCase(hashedEntry));

		hashedEntry = pwdh.toHashedEntry(MD5_PASSWORD_1, "charlie");
		assertTrue(MD5_HASHED_ENTRY_1.equalsIgnoreCase(hashedEntry));

		hashedEntry = pwdh.toHashedEntry("badpassword", "charlie");
		assertFalse(MD5_HASHED_ENTRY_1.equalsIgnoreCase(hashedEntry));

		hashedEntry = pwdh.toHashedEntry("badpassword", null);
		assertFalse(MD5_HASHED_ENTRY_1.equalsIgnoreCase(hashedEntry));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testToHashedEntryMD5NullPassword() {
		PasswordHash pwdh = PasswordHash.instanceOf("MD5");
		pwdh.toHashedEntry((String)null, null);
	}


	/**
	 * Test creating a hashed entry for scheme Combined-MD5. In this scheme there is no salt, so a direct
	 * comparison to a constant value is possible.
	 *
	 * Test method for {@link PasswordHash#toHashedEntry(String, String)} for CMD5.
	 */
	@Test
	public void testToHashedEntryCMD5() {
		PasswordHash pwdh = PasswordHash.instanceOf("CMD5");
		String hashedEntry = pwdh.toHashedEntry(CMD5_PASSWORD_1, CMD5_USERNAME_1);
		assertTrue(CMD5_HASHED_ENTRY_1.equalsIgnoreCase(hashedEntry));

		hashedEntry = pwdh.toHashedEntry(CMD5_PASSWORD_1, "charlie");
		assertFalse(CMD5_HASHED_ENTRY_1.equalsIgnoreCase(hashedEntry));

		hashedEntry = pwdh.toHashedEntry("badpassword", "charlie");
		assertFalse(MD5_HASHED_ENTRY_1.equalsIgnoreCase(hashedEntry));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testToHashedEntryCMD5NullPassword() {
		PasswordHash pwdh = PasswordHash.instanceOf("CMD5");
		pwdh.toHashedEntry((String)null, CMD5_USERNAME_1);
	}

	/**
	 * Test creating a hashed entry for scheme Combined-MD5, when no user is given.
	 * This should never happen in the application, so we expect an exception to be thrown.
	 *
	 * Test method for {@link PasswordHash#toHashedEntry(String, String)} for broken CMD5.
	 */
	@Test
	public void testToHashedEntryCMD5NoUsername() {
		PasswordHash pwdh = PasswordHash.instanceOf("CMD5");
		try {
			String hashedEntry = pwdh.toHashedEntry(CMD5_PASSWORD_1, "");
			fail("CMD5 cannot work with an empty '' username. Got: " + hashedEntry);
		}
		catch (IllegalArgumentException ignored) { /*success*/ }

		try {
			String hashedEntry = pwdh.toHashedEntry(CMD5_PASSWORD_1, "   ");
			fail("CMD5 cannot work with an empty '   ' username. Got: " + hashedEntry);
		}
		catch (IllegalArgumentException ignored) { /*success*/ }

		try {
			String hashedEntry = pwdh.toHashedEntry(CMD5_PASSWORD_1, "	");
			fail("CMD5 cannot work with an empty ' ' username. Got: " + hashedEntry);
		}
		catch (IllegalArgumentException ignored) { /*success*/ }

		try {
			String hashedEntry = pwdh.toHashedEntry(CMD5_PASSWORD_1, null);
			fail("CMD5 cannot work with a null username. Got: " + hashedEntry);
		}
		catch (IllegalArgumentException ignored) { /*success*/ }
	}

	/**
	 * Test creating a hashed entry for scheme PBKDF2.
	 * Since this scheme uses a salt, we test by running a match. This is a bit backwards,
	 * but recreating the PBKDF2 here seems a little overkill.
	 *
	 * Test method for {@link PasswordHash#toHashedEntry(String, String)} for PBKDF2.
	 */
	@Test
	public void testToHashedEntryPBKDF2() {
		PasswordHash pwdh = PasswordHash.instanceOf("PBKDF2");
		String hashedEntry = pwdh.toHashedEntry(PBKDF2_PASSWORD_1, null);
		assertTrue("Type identifier is incorrect.", hashedEntry.startsWith(PasswordHash.Type.PBKDF2.name()));
		PasswordHash pwdhverify = PasswordHash.instanceFor(hashedEntry);
		assertNotNull(pwdhverify);
		assertTrue(PBKDF2_PASSWORD_1, pwdhverify.matches(hashedEntry, PBKDF2_PASSWORD_1.toCharArray(), null));

		hashedEntry = pwdh.toHashedEntry(PBKDF2_PASSWORD_2, "");
		assertTrue("Type identifier is incorrect.", hashedEntry.startsWith(PasswordHash.Type.PBKDF2.name()));
		pwdhverify = PasswordHash.instanceFor(hashedEntry);
		assertNotNull(pwdhverify);
		assertTrue(PBKDF2_PASSWORD_2, pwdhverify.matches(hashedEntry, PBKDF2_PASSWORD_2.toCharArray(), null));

		hashedEntry = pwdh.toHashedEntry(PBKDF2_PASSWORD_0, "alpha");
		assertTrue("Type identifier is incorrect.", hashedEntry.startsWith(PasswordHash.Type.PBKDF2.name()));
		pwdhverify = PasswordHash.instanceFor(hashedEntry);
		assertNotNull(pwdhverify);
		assertTrue(PBKDF2_PASSWORD_0, pwdhverify.matches(hashedEntry, PBKDF2_PASSWORD_0.toCharArray(), null));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testToHashedEntryPBKDF2NullPassword() {
		PasswordHash pwdh = PasswordHash.instanceOf("PBKDF2");
		pwdh.toHashedEntry((String)null, null);
	}


	/**
	 * Test method for {@link com.gitblit.utils.PasswordHash#matches(String, char[], String)} for MD5.
	 */
	@Test
	public void testMatchesMD5() {
		PasswordHash pwdh = PasswordHash.instanceOf("MD5");

		assertTrue("PWD0, Null user", pwdh.matches(MD5_HASHED_ENTRY_0, MD5_PASSWORD_0.toCharArray(), null));
		assertTrue("PWD0, Empty user", pwdh.matches(MD5_HASHED_ENTRY_0, MD5_PASSWORD_0.toCharArray(), ""));
		assertTrue("PWD0, With user", pwdh.matches(MD5_HASHED_ENTRY_0, MD5_PASSWORD_0.toCharArray(), "maxine"));

		assertTrue("PWD1, Null user", pwdh.matches(MD5_HASHED_ENTRY_1, MD5_PASSWORD_1.toCharArray(), null));
		assertTrue("PWD1, Empty user", pwdh.matches(MD5_HASHED_ENTRY_1, MD5_PASSWORD_1.toCharArray(), ""));
		assertTrue("PWD1, With user", pwdh.matches(MD5_HASHED_ENTRY_1, MD5_PASSWORD_1.toCharArray(), "maxine"));



		assertFalse("Matched wrong password", pwdh.matches(MD5_HASHED_ENTRY_1, "wrongpassword".toCharArray(), null));
		assertFalse("Matched wrong password, with empty user", pwdh.matches(MD5_HASHED_ENTRY_1, "wrongpassword".toCharArray(), "  "));
		assertFalse("Matched wrong password, with user", pwdh.matches(MD5_HASHED_ENTRY_1, "wrongpassword".toCharArray(), "someuser"));

		assertFalse("Matched empty password", pwdh.matches(MD5_HASHED_ENTRY_1, "".toCharArray(), null));
		assertFalse("Matched empty password, with empty user", pwdh.matches(MD5_HASHED_ENTRY_1, " ".toCharArray(), "  "));
		assertFalse("Matched empty password, with user", pwdh.matches(MD5_HASHED_ENTRY_1, "	".toCharArray(), "someuser"));

		assertFalse("Matched null password", pwdh.matches(MD5_HASHED_ENTRY_1, null, null));
		assertFalse("Matched null password, with empty user", pwdh.matches(MD5_HASHED_ENTRY_1, null, "  "));
		assertFalse("Matched null password, with user", pwdh.matches(MD5_HASHED_ENTRY_1, null, "someuser"));


		assertFalse("Matched wrong hashed entry", pwdh.matches(MD5_HASHED_ENTRY_1, MD5_PASSWORD_0.toCharArray(), null));
		assertFalse("Matched wrong hashed entry, with empty user", pwdh.matches(MD5_HASHED_ENTRY_1, MD5_PASSWORD_0.toCharArray(), ""));
		assertFalse("Matched wrong hashed entry, with user", pwdh.matches(MD5_HASHED_ENTRY_1, MD5_PASSWORD_0.toCharArray(), "someuser"));

		assertFalse("Matched empty hashed entry", pwdh.matches("", MD5_PASSWORD_0.toCharArray(), null));
		assertFalse("Matched empty hashed entry, with empty user", pwdh.matches("  ", MD5_PASSWORD_0.toCharArray(), ""));
		assertFalse("Matched empty hashed entry, with user", pwdh.matches("	", MD5_PASSWORD_0.toCharArray(), "someuser"));

		assertFalse("Matched null entry", pwdh.matches(null, MD5_PASSWORD_0.toCharArray(), null));
		assertFalse("Matched null entry, with empty user", pwdh.matches(null, MD5_PASSWORD_0.toCharArray(), ""));
		assertFalse("Matched null entry, with user", pwdh.matches(null, MD5_PASSWORD_0.toCharArray(), "someuser"));


		assertFalse("Matched wrong scheme", pwdh.matches(CMD5_HASHED_ENTRY_0, MD5_PASSWORD_0.toCharArray(), null));
		assertFalse("Matched wrong scheme", pwdh.matches(PBKDF2_HASHED_ENTRY_0, MD5_PASSWORD_0.toCharArray(), ""));
		assertFalse("Matched wrong scheme", pwdh.matches(CMD5_HASHED_ENTRY_0, CMD5_PASSWORD_0.toCharArray(), CMD5_USERNAME_0));
	}

	/**
	 * Test method for {@link com.gitblit.utils.PasswordHash#matches(String, char[], String)} for Combined-MD5.
	 */
	@Test
	public void testMatchesCombinedMD5() {
		PasswordHash pwdh = PasswordHash.instanceOf("CMD5");

		assertTrue("PWD0", pwdh.matches(CMD5_HASHED_ENTRY_0, CMD5_PASSWORD_0.toCharArray(), CMD5_USERNAME_0));
		assertTrue("Empty user", pwdh.matches(CMD5_HASHED_ENTRY_1, CMD5_PASSWORD_1.toCharArray(), CMD5_USERNAME_1));



		assertFalse("Matched wrong password", pwdh.matches(CMD5_HASHED_ENTRY_1, "wrongpassword".toCharArray(), CMD5_USERNAME_1));
		assertFalse("Matched wrong password", pwdh.matches(CMD5_HASHED_ENTRY_1, CMD5_PASSWORD_0.toCharArray(), CMD5_USERNAME_1));

		assertFalse("Matched wrong user", pwdh.matches(CMD5_HASHED_ENTRY_1, CMD5_PASSWORD_1.toCharArray(), CMD5_USERNAME_0));
		assertFalse("Matched wrong user", pwdh.matches(CMD5_HASHED_ENTRY_1, CMD5_PASSWORD_1.toCharArray(), "Samantha Jones"));

		assertFalse("Matched empty user", pwdh.matches(CMD5_HASHED_ENTRY_1, CMD5_PASSWORD_1.toCharArray(), ""));
		assertFalse("Matched empty user", pwdh.matches(CMD5_HASHED_ENTRY_1, CMD5_PASSWORD_1.toCharArray(), "    "));
		assertFalse("Matched null user", pwdh.matches(CMD5_HASHED_ENTRY_1, CMD5_PASSWORD_1.toCharArray(), null));

		assertFalse("Matched empty hashed entry", pwdh.matches("", CMD5_PASSWORD_0.toCharArray(), CMD5_USERNAME_0));
		assertFalse("Matched empty hashed entry, with empty user", pwdh.matches("  ", CMD5_PASSWORD_1.toCharArray(), ""));
		assertFalse("Matched empty hashed entry, with null user", pwdh.matches("	", CMD5_PASSWORD_0.toCharArray(), null));

		assertFalse("Matched null entry, with user", pwdh.matches(null, CMD5_PASSWORD_1.toCharArray(), CMD5_USERNAME_1));
		assertFalse("Matched null entry, with empty user", pwdh.matches(null, CMD5_PASSWORD_1.toCharArray(), ""));
		assertFalse("Matched null entry, with null user", pwdh.matches(null, CMD5_PASSWORD_1.toCharArray(), null));


		assertFalse("Matched wrong scheme", pwdh.matches(MD5_HASHED_ENTRY_0, CMD5_PASSWORD_0.toCharArray(), null));
		assertFalse("Matched wrong scheme", pwdh.matches(PBKDF2_HASHED_ENTRY_0, CMD5_PASSWORD_0.toCharArray(), ""));
		assertFalse("Matched wrong scheme", pwdh.matches(MD5_HASHED_ENTRY_0, CMD5_PASSWORD_0.toCharArray(), CMD5_USERNAME_0));
		assertFalse("Matched wrong scheme", pwdh.matches(MD5_HASHED_ENTRY_0, MD5_PASSWORD_0.toCharArray(), CMD5_USERNAME_0));
	}



	/**
	 * Test method for {@link com.gitblit.utils.PasswordHash#matches(String, char[], String)} for PBKDF2.
	 */
	@Test
	public void testMatchesPBKDF2() {
		PasswordHash pwdh = PasswordHash.instanceOf("PBKDF2");

		assertTrue("PWD0, Null user", pwdh.matches(PBKDF2_HASHED_ENTRY_0, PBKDF2_PASSWORD_0.toCharArray(), null));
		assertTrue("PWD0, Empty user", pwdh.matches(PBKDF2_HASHED_ENTRY_0, PBKDF2_PASSWORD_0.toCharArray(), ""));
		assertTrue("PWD0, With user", pwdh.matches(PBKDF2_HASHED_ENTRY_0, PBKDF2_PASSWORD_0.toCharArray(), "maxine"));

		assertTrue("PWD1, Null user", pwdh.matches(PBKDF2_HASHED_ENTRY_1, PBKDF2_PASSWORD_1.toCharArray(), null));
		assertTrue("PWD1, Empty user", pwdh.matches(PBKDF2_HASHED_ENTRY_1, PBKDF2_PASSWORD_1.toCharArray(), ""));
		assertTrue("PWD1, With user", pwdh.matches(PBKDF2_HASHED_ENTRY_1, PBKDF2_PASSWORD_1.toCharArray(), "Maxim Gorki"));

		assertTrue("PWD2, Null user", pwdh.matches(PBKDF2_HASHED_ENTRY_2, PBKDF2_PASSWORD_2.toCharArray(), null));
		assertTrue("PWD2, Empty user", pwdh.matches(PBKDF2_HASHED_ENTRY_2, PBKDF2_PASSWORD_2.toCharArray(), ""));
		assertTrue("PWD2, With user", pwdh.matches(PBKDF2_HASHED_ENTRY_2, PBKDF2_PASSWORD_2.toCharArray(), "Epson"));



		assertFalse("Matched wrong password", pwdh.matches(PBKDF2_HASHED_ENTRY_1, "wrongpassword".toCharArray(), null));
		assertFalse("Matched wrong password, with empty user", pwdh.matches(PBKDF2_HASHED_ENTRY_1, "wrongpassword".toCharArray(), "  "));
		assertFalse("Matched wrong password, with user", pwdh.matches(PBKDF2_HASHED_ENTRY_1, "wrongpassword".toCharArray(), "someuser"));

		assertFalse("Matched empty password", pwdh.matches(PBKDF2_HASHED_ENTRY_2, "".toCharArray(), null));
		assertFalse("Matched empty password, with empty user", pwdh.matches(PBKDF2_HASHED_ENTRY_2, " ".toCharArray(), "  "));
		assertFalse("Matched empty password, with user", pwdh.matches(PBKDF2_HASHED_ENTRY_2, "	".toCharArray(), "someuser"));

		assertFalse("Matched null password", pwdh.matches(PBKDF2_HASHED_ENTRY_0, null, null));
		assertFalse("Matched null password, with empty user", pwdh.matches(PBKDF2_HASHED_ENTRY_0, null, "  "));
		assertFalse("Matched null password, with user", pwdh.matches(PBKDF2_HASHED_ENTRY_0, null, "someuser"));


		assertFalse("Matched wrong hashed entry", pwdh.matches(PBKDF2_HASHED_ENTRY_1, PBKDF2_PASSWORD_0.toCharArray(), null));
		assertFalse("Matched wrong hashed entry, with empty user", pwdh.matches(PBKDF2_HASHED_ENTRY_1, PBKDF2_PASSWORD_0.toCharArray(), ""));
		assertFalse("Matched wrong hashed entry, with user", pwdh.matches(PBKDF2_HASHED_ENTRY_1, PBKDF2_PASSWORD_0.toCharArray(), "someuser"));

		assertFalse("Matched empty hashed entry", pwdh.matches("", PBKDF2_PASSWORD_0.toCharArray(), null));
		assertFalse("Matched empty hashed entry, with empty user", pwdh.matches("  ", PBKDF2_PASSWORD_0.toCharArray(), ""));
		assertFalse("Matched empty hashed entry, with user", pwdh.matches("	", PBKDF2_PASSWORD_0.toCharArray(), "someuser"));

		assertFalse("Matched null entry", pwdh.matches(null, PBKDF2_PASSWORD_0.toCharArray(), null));
		assertFalse("Matched null entry, with empty user", pwdh.matches(null, PBKDF2_PASSWORD_0.toCharArray(), ""));
		assertFalse("Matched null entry, with user", pwdh.matches(null, PBKDF2_PASSWORD_0.toCharArray(), "someuser"));


		assertFalse("Matched wrong scheme", pwdh.matches(CMD5_HASHED_ENTRY_0, PBKDF2_PASSWORD_0.toCharArray(), null));
		assertFalse("Matched wrong scheme", pwdh.matches(MD5_HASHED_ENTRY_0, PBKDF2_PASSWORD_0.toCharArray(), ""));
		assertFalse("Matched wrong scheme", pwdh.matches(CMD5_HASHED_ENTRY_0, PBKDF2_PASSWORD_0.toCharArray(), CMD5_USERNAME_0));
	}


	/**
	 * Test method for {@link com.gitblit.utils.PasswordHash#matches(String, char[], String)}
	 * for old existing entries with the "PBKDF2WITHHMACSHA256" type identifier.
	 */
	@Test
	public void testMatchesPBKDF2Compat() {
		PasswordHash pwdh = PasswordHash.instanceOf("PBKDF2");

		assertTrue("PWD3, Null user", pwdh.matches(PBKDF2_HASHED_ENTRY_3, PBKDF2_PASSWORD_3.toCharArray(), null));
		assertTrue("PWD3, Empty user", pwdh.matches(PBKDF2_HASHED_ENTRY_3, PBKDF2_PASSWORD_3.toCharArray(), ""));
		assertTrue("PWD3, With user", pwdh.matches(PBKDF2_HASHED_ENTRY_3, PBKDF2_PASSWORD_3.toCharArray(), "maxine"));


		assertFalse("Matched wrong password", pwdh.matches(PBKDF2_HASHED_ENTRY_3, "bar".toCharArray(), null));
		assertFalse("Matched wrong password, with empty user", pwdh.matches(PBKDF2_HASHED_ENTRY_3, "bar".toCharArray(), "  "));
		assertFalse("Matched wrong password, with user", pwdh.matches(PBKDF2_HASHED_ENTRY_3, "bar".toCharArray(), "someuser"));

		assertFalse("Matched empty password", pwdh.matches(PBKDF2_HASHED_ENTRY_3, "".toCharArray(), null));
		assertFalse("Matched empty password, with empty user", pwdh.matches(PBKDF2_HASHED_ENTRY_3, " ".toCharArray(), "  "));
		assertFalse("Matched empty password, with user", pwdh.matches(PBKDF2_HASHED_ENTRY_3, "	".toCharArray(), "someuser"));

		assertFalse("Matched null password", pwdh.matches(PBKDF2_HASHED_ENTRY_3, null, null));
		assertFalse("Matched null password, with empty user", pwdh.matches(PBKDF2_HASHED_ENTRY_3, null, "  "));
		assertFalse("Matched null password, with user", pwdh.matches(PBKDF2_HASHED_ENTRY_3, null, "someuser"));


		assertFalse("Matched wrong hashed entry", pwdh.matches(PBKDF2_HASHED_ENTRY_3, PBKDF2_PASSWORD_0.toCharArray(), null));
		assertFalse("Matched wrong hashed entry, with empty user", pwdh.matches(PBKDF2_HASHED_ENTRY_3, PBKDF2_PASSWORD_0.toCharArray(), ""));
		assertFalse("Matched wrong hashed entry, with user", pwdh.matches(PBKDF2_HASHED_ENTRY_3, PBKDF2_PASSWORD_0.toCharArray(), "someuser"));
	}
}
