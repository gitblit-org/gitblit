package com.gitblit.utils;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

/**
 * The class PasswordHashPbkdf2 implements password hashing and validation
 * with PBKDF2
 *
 * It uses the concept proposed by OWASP - Hashing Java:
 * https://www.owasp.org/index.php/Hashing_Java
 */
class PasswordHashPbkdf2 extends PasswordHash
{

	private static final Logger LOGGER = LoggerFactory.getLogger(PasswordHashPbkdf2.class);

	/**
	 * The PBKDF has some parameters that define security and workload.
	 * The Configuration class keeps these parameters.
	 */
	private static class Configuration
	{
		private final String algorithm;
		private final int iterations;
		private final int keyLen;
		private final int saltLen;

		private Configuration(String algorithm, int iterations, int keyLen, int saltLen) {
			this.algorithm = algorithm;
			this.iterations = iterations;
			this.keyLen = keyLen;
			this.saltLen = saltLen;
		}
	}


	private static final SecureRandom RANDOM = new SecureRandom();
	/**
	 * A list of Configurations is created to list the configurations supported by
	 * this implementation. The configuration id is stored in the hashed entry,
	 * identifying the Configuration in this array.
	 * When adding a new variant with different values for these parameters, add
	 * it to this array.
	 * The code uses the last configuration in the array as the most secure, to be used
	 * when creating new hashes when no configuration is specified.
	 */
	private static final Configuration[] configurations = {
			// Configuration 0, also default when none is specified in the stored hashed entry.
			new Configuration("PBKDF2WithHmacSHA256", 10000, 256, 32)
	};


	PasswordHashPbkdf2() {
		super(Type.PBKDF2);
	}


	/*
	 * We return a hashed entry, where the hash part (salt+hash) itself is prefixed
	 * again by the configuration id of the configuration that was used for the PBKDF,
	 * enclosed in '$':
	 * PBKDF2:$0$thesaltThehash
	 */
	@Override
	public String toHashedEntry(char[] password, String username) {
		if (password == null) {
			LOGGER.warn("The password argument may not be null when hashing a password.");
			throw new IllegalArgumentException("The password argument may not be null when hashing a password.");
		}

		int configId = getLatestConfigurationId();
		Configuration config = configurations[configId];

		byte[] salt = new byte[config.saltLen];
		RANDOM.nextBytes(salt);
		byte[] hash = hash(password, salt, config);

		return type.name() + ":"
				+ "$" + configId + "$"
				+ StringUtils.toHex(salt)
				+ StringUtils.toHex(hash);
	}

	@Override
	public boolean matches(String hashedEntry, char[] password, String username) {
		if (hashedEntry == null || type != PasswordHash.getEntryType(hashedEntry)) return false;
		if (password == null) return false;

		String hashedPart = getEntryValue(hashedEntry);
		int configId = getConfigIdFromStoredPassword(hashedPart);

		return isPasswordCorrect(password, hashedPart, configurations[configId]);
	}








	/**
	 * Return the id of the most updated configuration of parameters for the PBKDF.
	 * New password hashes should be generated with this one.
	 *
	 * @return An index into the configurations array for the latest configuration.
	 */
	private int getLatestConfigurationId() {
		return configurations.length-1;
	}


	/**
	 * Get the configuration id from the stored hashed password, that was used when the
	 * hash was created. The configuration id is the index into the configuration array,
	 * and is stored in the format $Id$ after the type identifier: TYPE:$Id$....
	 * If there is no identifier in the stored entry, id 0 is used, to keep backward
	 * compatibility.
	 * If an id is found that is not in the range of the declared configurations,
	 * 0 is returned. This may fail password validation. As of now there is only one
	 * configuration and even if there were more, chances are slim that anything else
	 * was used. So we try at least the first one instead of failing with an exception
	 * as the probability of success is high enough to save the user from a bad experience
	 * and to risk some hassle for the admin finding out in the logs why a login failed,
	 * when it does.
	 *
	 * @param hashPart
	 * 			The hash part of the stored entry, i.e. the part after the TYPE:
	 * @return The configuration id, or
	 *         0 if none was found.
	 */
	private static int getConfigIdFromStoredPassword(String hashPart) {
		String[] parts = hashPart.split("\\$", 3);
		// If there are not two parts, there is no '$'-enclosed part and we have no configuration information stored.
		// Return default 0.
		if (parts.length <= 2) return 0;

		// The first string wil be empty. Even if it isn't we ignore it because it doesn't contain our information.
		try {
			int configId = Integer.parseInt(parts[1]);
			if (configId < 0 || configId >= configurations.length) {
				LOGGER.warn("A user table password entry contains a configuration id that is not valid: {}." +
						"Assuming PBKDF configuration 0. This may fail to validate the password.", configId);
				return 0;
			}
			return configId;
		}
		catch (NumberFormatException e) {
			LOGGER.warn("A user table password entry contains a configuration id that is not a parsable number ({}${}$...)." +
					"Assuming PBKDF configuration 0. This may fail to validate the password.", parts[0], parts[1], e);
			return 0;
		}
	}





	/**
	 * Hash.
	 *
	 * @param password
	 *          the password
	 * @param salt
	 *          the salt
	 * @param config
	 * 			Parameter configuration to use for the PBKDF
	 * @return Hashed result
	 */
	private static byte[] hash(char[] password, byte[] salt, Configuration config) {
		PBEKeySpec spec = new PBEKeySpec(password, salt, config.iterations, config.keyLen);
		Arrays.fill(password, Character.MIN_VALUE);
		try {
			SecretKeyFactory skf = SecretKeyFactory.getInstance(config.algorithm);
			return skf.generateSecret(spec).getEncoded();
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			LOGGER.warn("Error while hashing password.", e);
			throw new IllegalStateException("Error while hashing password", e);
		} finally {
			spec.clearPassword();
		}
	}

	/**
	 * Checks if is password correct.
	 *
	 * @param passwordToCheck
	 *            the password to check
	 * @param salt
	 *            the salt
	 * @param expectedHash
	 *            the expected hash
	 * @return true, if is password correct
	 */
	private static boolean isPasswordCorrect(char[] passwordToCheck, byte[] salt, byte[] expectedHash, Configuration config) {
		byte[] hashToCheck = hash(passwordToCheck, salt, config);
		Arrays.fill(passwordToCheck, Character.MIN_VALUE);
		if (hashToCheck.length != expectedHash.length) {
			return false;
		}
		for (int i = 0; i < hashToCheck.length; i++) {
			if (hashToCheck[i] != expectedHash[i]) {
				return false;
			}
		}
		return true;
	}


	/**
	 * Gets the salt from stored password.
	 *
	 * @param storedPassword
	 *            the stored password
	 * @return the salt from stored password
	 */
	private static byte[] getSaltFromStoredPassword(String storedPassword, Configuration config) {
		byte[] pw = getStoredHashWithStrippedPrefix(storedPassword);
		return Arrays.copyOfRange(pw, 0, config.saltLen);
	}

	/**
	 * Gets the hash from stored password.
	 *
	 * @param storedPassword
	 *            the stored password
	 * @return the hash from stored password
	 */
	private static byte[] getHashFromStoredPassword(String storedPassword, Configuration config) {
		byte[] pw = getStoredHashWithStrippedPrefix(storedPassword);
		return Arrays.copyOfRange(pw, config.saltLen, pw.length);
	}

	/**
	 * Strips the configuration id prefix ($Id$) from a stored
	 * password and returns the decoded hash
	 *
	 * @param storedPassword
	 *            the stored password
	 * @return the stored hash with stripped prefix
	 */
	private static byte[] getStoredHashWithStrippedPrefix(String storedPassword) {
		String[] strings = storedPassword.split("\\$", 3);
		String saltAndHash = strings[strings.length -1];
		try {
			return Hex.decodeHex(saltAndHash.toCharArray());
		} catch (DecoderException e) {
			LOGGER.warn("Failed to decode stored password entry from hex to string.", e);
			throw new IllegalStateException("Error while reading stored credentials", e);
		}
	}

	/**
	 * Checks if password is correct.
	 *
	 * @param password
	 *            the password to validate
	 * @param storedPassword
	 *            the stored password, i.e. the password entry value, without the leading TYPE:
	 * @return true, if password is correct, false otherwise
	 */
	private static boolean isPasswordCorrect(char[] password, String storedPassword, Configuration config) {
		byte[] storedSalt = getSaltFromStoredPassword(storedPassword, config);
		byte[] storedHash = getHashFromStoredPassword(storedPassword, config);
		return isPasswordCorrect(password, storedSalt, storedHash, config);
	}
}
