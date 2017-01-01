package com.gitblit.utils;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

/**
 * The Class SecurePasswordHashUtils provides methods to create and validate secure hashes from user passwords.
 * 
 * It uses the concept proposed by OWASP - Hashing Java: https://www.owasp.org/index.php/Hashing_Java
 */
public class SecurePasswordHashUtils {

	public static final String PBKDF2WITHHMACSHA256_TYPE = "PBKDF2WITHHMACSHA256:";

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int ITERATIONS = 10000;
	private static final int KEY_LENGTH = 256;
	private static final int SALT_LENGTH = 32;

	/** The instance. */
	private static SecurePasswordHashUtils instance;

	/**
	 * Instantiates a new secure password hash utils.
	 */
	private SecurePasswordHashUtils() {
	}

	/**
	 * Gets an instance of {@link SecurePasswordHashUtils}.
	 *
	 * @return the secure password hash utils
	 */
	public static SecurePasswordHashUtils get() {
		if (instance == null) {
			instance = new SecurePasswordHashUtils();
		}
		return instance;
	}

	/**
	 * Gets the next salt.
	 *
	 * @return the next salt
	 */
	protected byte[] getNextSalt() {
		byte[] salt = new byte[SALT_LENGTH];
		RANDOM.nextBytes(salt);
		return salt;
	}

	/**
	 * Hash.
	 *
	 * @param password
	 *            the password
	 * @param salt
	 *            the salt
	 * @return the byte[]
	 */
	protected byte[] hash(char[] password, byte[] salt) {
		PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
		Arrays.fill(password, Character.MIN_VALUE);
		try {
			SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
			return skf.generateSecret(spec).getEncoded();
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
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
	protected boolean isPasswordCorrect(char[] passwordToCheck, byte[] salt, byte[] expectedHash) {
		byte[] hashToCheck = hash(passwordToCheck, salt);
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
	 * Creates the new secure hash from a password and formats it properly to be
	 * stored in a file.
	 *
	 * @param password
	 *            the password to be hashed
	 * @return the sting to be stored in a file (users.conf)
	 */
	public String createStoredPasswordFromPassword(String password) {
		byte[] salt = getNextSalt();
		return String.format("%s%s%s", SecurePasswordHashUtils.PBKDF2WITHHMACSHA256_TYPE, StringUtils.toHex(salt), StringUtils.toHex(hash(password.toCharArray(), salt)));
	}

	/**
	 * Gets the salt from stored password.
	 *
	 * @param storedPassword
	 *            the stored password
	 * @return the salt from stored password
	 */
	protected byte[] getSaltFromStoredPassword(String storedPassword) {
		byte[] pw = getStoredHashWithStrippedPrefix(storedPassword);
		return Arrays.copyOfRange(pw, 0, SALT_LENGTH);
	}

	/**
	 * Gets the hash from stored password.
	 *
	 * @param storedPassword
	 *            the stored password
	 * @return the hash from stored password
	 */
	protected byte[] getHashFromStoredPassword(String storedPassword) {
		byte[] pw = getStoredHashWithStrippedPrefix(storedPassword);
		return Arrays.copyOfRange(pw, SALT_LENGTH, pw.length);
	}

	/**
	 * Strips the prefix ({@link #PBKDF2WITHHMACSHA256_TYPE}) from a stored
	 * password and returns the decoded hash
	 *
	 * @param storedPassword
	 *            the stored password
	 * @return the stored hash with stripped prefix
	 */
	protected byte[] getStoredHashWithStrippedPrefix(String storedPassword) {
		String saltAndHash = storedPassword.substring(PBKDF2WITHHMACSHA256_TYPE.length());
		try {
			return Hex.decodeHex(saltAndHash.toCharArray());
		} catch (DecoderException e) {
			throw new IllegalStateException("Error while reading stored credentials", e);
		}
	}

	/**
	 * Checks if is password correct.
	 *
	 * @param password
	 *            the password
	 * @param storedPassword
	 *            the stored password
	 * @return true, if is password correct
	 */
	public boolean isPasswordCorrect(char[] password, String storedPassword) {
		byte[] storedSalt = getSaltFromStoredPassword(storedPassword);
		byte[] storedHash = getHashFromStoredPassword(storedPassword);
		return isPasswordCorrect(password, storedSalt, storedHash);
	}
}
