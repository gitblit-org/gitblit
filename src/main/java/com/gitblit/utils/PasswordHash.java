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

import java.util.Arrays;

/**
 * This is the superclass for classes responsible for handling password hashing.
 * 
 * It provides a factory-like interface to create an instance of a class that
 * is responsible for the mechanics of a specific password hashing method.
 * It also provides the common interface, leaving implementation specifics
 * to subclasses of itself, which are the factory products.
 * 
 * @author Florian Zschocke
 * @since 1.9.0
 */
public abstract class PasswordHash {

	/**
	 * The types of implemented password hashing schemes.
	 */
	public enum Type {
		MD5,
		CMD5,
		PBKDF2;

		static Type fromName(String name) {
			if (name == null) return null;
			for (Type type : Type.values()) {
				if (type.name().equalsIgnoreCase(name)) return type;
			}
			// Compatibility with type id "PBKDF2WITHHMACSHA256", which is also handled by PBKDF2 type.
			if (name.equalsIgnoreCase("PBKDF2WITHHMACSHA256")) return Type.PBKDF2;

			// Recognise the name used for CMD5 in the settings file.
			if (name.equalsIgnoreCase("combined-md5")) return Type.CMD5;

			return null;
		}
	}

	/**
	 * The hashing scheme type handled by an instance of a subclass
	 */
	final Type type;


	/**
	 * Constructor for subclasses to initialize the final type field.
	 * @param type
	 * 			Type of hashing scheme implemented by this instance.
	 */
	PasswordHash(Type type) {
		this.type = type;
	}


	/**
	 * When no hash type is specified, this determines the default that should be used.
	 */
	public static Type getDefaultType() {
		return Type.PBKDF2;
	}


	/**
	 * Create an instance of a password hashing class for the given hash type.
	 *
	 * @param type
	 * 			Type of hash to be used.
	 * @return	A class that can calculate the given hash type and verify a user password,
	 * 			or null if the given hash type is not a valid one.
	 */
	public static PasswordHash instanceOf(String type) {
		Type hashType = Type.fromName(type);
		if (hashType == null) return null;
		switch (hashType) {
			case MD5:
				return new PasswordHashMD5();
			case CMD5:
				return new PasswordHashCombinedMD5();
			case PBKDF2:
				return new PasswordHashPbkdf2();
			default:
				return null;
		}
	}

	/**
	 * Create an instance of a password hashing class of the correct type for a given
	 * hashed password from the user password table. The stored hashed password needs
	 * to be prefixed with the hash type identifier.
	 *
	 * @param hashedEntry
	 * 			Hashed password string from the user table.
	 * @return
	 * 			A class that can calculate the given hash type and verify a user password,
	 * 			or null if no instance can be created for the hashed user password.
	 */
	public static PasswordHash instanceFor(String hashedEntry) {
		Type type = getEntryType(hashedEntry);
		if (type != null) return instanceOf(type.name());
		return null;
	}

	/**
	 * Test if a given string is a hashed password entry. This method simply checks if the
	 * given string is prefixed by a known hash type identifier.
	 *
	 * @param storedPassword
	 * 			A stored user password.
	 * @return	True if the given string is detected to be hashed with a known hash type,
	 * 			false otherwise.
	 */
	public static boolean isHashedEntry(String storedPassword) {
		return null != getEntryType(storedPassword);
	}


	/**
	 * Some hash methods are considered more secure than others. This method determines for a certain type
	 * of password hash if it is inferior than a given other type and stored passwords should be
	 * upgraded to the given hashing method.
	 *
	 * @param algorithm
	 * 			Password hashing type to be checked if it is superior than the one of this instance.
	 * @return	True, if the given type in parameter {@code algorithm} is better and stored passwords should be upgraded to it,
	 *          false, otehrwise.
	 */
	public boolean needsUpgradeTo(String algorithm) {
		Type hashType = Type.fromName(algorithm);
		if (hashType == null) return false;
		if (this.type == hashType) return false;

		// Right now we keep it really simple. With the existing types, only PBKDF2 is considered secure,
		// everything else is inferior. This will need to be updated once more secure hashing algorithms
		// are implemented, or the workload/parameters of the PBKDF2 are changed.
		return hashType == Type.PBKDF2;
	}


	/**
	 * Convert the given password to a hashed password entry to be stored in the user table.
	 * The resulting string is prefixed by the hashing scheme type followed by a colon:
	 * TYPE:theactualhashinhex
	 *
	 * @param password
	 * 			Password to be hashed.
	 * @param username
	 * 			User name, only used for the Combined-MD5 (user+MD5) hashing type.
	 * @return
	 * 			Hashed password entry to be stored in the user table.
	 */
	abstract public String toHashedEntry(char[] password, String username);

	/**
	 * Convert the given password to a hashed password entry to be stored in the user table.
	 * The resulting string is prefixed by the hashing scheme type followed by a colon:
	 * TYPE:theactualhashinhex
	 *
	 * @param password
	 * 			Password to be hashed.
	 * @param username
	 * 			User name, only used for the Combined-MD5 (user+MD5) hashing type.
	 * @return
	 * 			Hashed password entry to be stored in the user table.
	 */
	public String toHashedEntry(String password, String username) {
		if (password == null) throw new IllegalArgumentException("The password argument may not be null when hashing a password.");
		return toHashedEntry(password.toCharArray(), username);
	}

	/**
	 * Test if a given password (and user name) match a hashed password.
	 * The instance of the password hash class has to be created with
	 * {code instanceFor}, so that it matches the type of the hashed password
	 * entry to test against.
	 *
	 *
	 * @param hashedEntry
	 * 			The hashed password entry from the user password table.
	 * @param password
	 * 			Clear text password to test against the hashed one.
	 * @param username
	 * 			User name, needed for the MD5+USER hash type.
	 * @return	True, if the password (and username) match the hashed password,
	 * 			false, otherwise.
	 */
	public boolean matches(String hashedEntry, char[] password, String username) {
		if (hashedEntry == null || type != PasswordHash.getEntryType(hashedEntry)) return false;
		if (password == null) return false;

		String hashed = toHashedEntry(password, username);
		Arrays.fill(password, Character.MIN_VALUE);
		return hashed.equalsIgnoreCase(hashedEntry);
	}





	static Type getEntryType(String hashedEntry) {
		if (hashedEntry == null) return null;
		int indexOfSeparator = hashedEntry.indexOf(':');
		if (indexOfSeparator <= 0) return null;
		String typeId = hashedEntry.substring(0, indexOfSeparator);
		return Type.fromName(typeId);
	}


	static String getEntryValue(String hashedEntry) {
		if (hashedEntry == null) return null;
		int indexOfSeparator = hashedEntry.indexOf(':');
		return hashedEntry.substring(indexOfSeparator +1);
	}





	/**************************************      Implementations      *************************************************/

	private static class PasswordHashMD5 extends PasswordHash
	{
		PasswordHashMD5() {
			super(Type.MD5);
		}

		// To keep the handling identical to how it was before, and therefore not risk invalidating stored passwords,
		// for MD5 the (String,String) variant of the method is the one implementing the hashing.
		@Override
		public String toHashedEntry(char[] password, String username) {
			if (password == null) throw new IllegalArgumentException("The password argument may not be null when hashing a password.");
			return toHashedEntry(new String(password), username);
		}

		@Override
		public String toHashedEntry(String password, String username) {
			if (password == null) throw new IllegalArgumentException("The password argument may not be null when hashing a password.");
			return type.name() + ":"
					+ StringUtils.getMD5(password);
		}
	}




	private static class PasswordHashCombinedMD5 extends PasswordHash
	{
		PasswordHashCombinedMD5() {
			super(Type.CMD5);
		}

		// To keep the handling identical to how it was before, and therefore not risk invalidating stored passwords,
		// for Combined-MD5 the (String,String) variant of the method is the one implementing the hashing.
		@Override
		public String toHashedEntry(char[] password, String username) {
			if (password == null) throw new IllegalArgumentException("The password argument may not be null when hashing a password.");
			return toHashedEntry(new String(password), username);
		}
		@Override
		public String toHashedEntry(String password, String username) {
			if (password == null) throw new IllegalArgumentException("The password argument may not be null when hashing a password.");
			if (username == null) throw new IllegalArgumentException("The username argument may not be null when hashing a password with Combined-MD5.");
			if (StringUtils.isEmpty(username)) throw new IllegalArgumentException("The username argument may not be empty when hashing a password with Combined-MD5.");
			return type.name() + ":"
					+ StringUtils.getMD5(username.toLowerCase() + password);
		}

		@Override
		public boolean matches(String hashedEntry, char[] password, String username) {
			if (username == null || StringUtils.isEmpty(username)) return false;
			return super.matches(hashedEntry, password, username);
		}

	}
}
