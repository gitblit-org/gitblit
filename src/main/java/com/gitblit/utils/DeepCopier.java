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
package com.gitblit.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DeepCopier {

	/**
	 * Utility method to calculate the checksum of an object.
	 * @param sourceObject The object from which to establish the checksum.
	 * @return The checksum
	 * @throws IOException
	 */
	public static BigInteger checksum(Object sourceObject) {

	    if (sourceObject == null) {
	      return BigInteger.ZERO;
	    }

	    try {
		    ByteArrayOutputStream baos = new ByteArrayOutputStream();
		    ObjectOutputStream oos = new ObjectOutputStream(baos);
		    oos.writeObject(sourceObject);
		    oos.close();

	    	MessageDigest m = MessageDigest.getInstance("SHA-1");
	    	m.update(baos.toByteArray());
	    	return new BigInteger(1, m.digest());
	    } catch (IOException e) {
	    	throw new RuntimeException(e);
	    } catch (NoSuchAlgorithmException e) {
	    	// impossible
	    }

	    return BigInteger.ZERO;
	}

	/**
	 * Produce a deep copy of the given object. Serializes the entire object to
	 * a byte array in memory. Recommended for relatively small objects.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T copy(T original) {
		T o = null;
		try {
			ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(byteOut);
			oos.writeObject(original);
			ByteArrayInputStream byteIn = new ByteArrayInputStream(byteOut.toByteArray());
			ObjectInputStream ois = new ObjectInputStream(byteIn);
			try {
				o = (T) ois.readObject();
			} catch (ClassNotFoundException cex) {
				// actually can not happen in this instance
			}
		} catch (IOException iox) {
			// doesn't seem likely to happen as these streams are in memory
			throw new RuntimeException(iox);
		}
		return o;
	}

	/**
	 * This conserves heap memory!!!!! Produce a deep copy of the given object.
	 * Serializes the object through a pipe between two threads. Recommended for
	 * very large objects. The current thread is used for serializing the
	 * original object in order to respect any synchronization the caller may
	 * have around it, and a new thread is used for deserializing the copy.
	 *
	 */
	public static <T> T copyParallel(T original) {
		try {
			PipedOutputStream outputStream = new PipedOutputStream();
			PipedInputStream inputStream = new PipedInputStream(outputStream);
			ObjectOutputStream ois = new ObjectOutputStream(outputStream);
			Receiver<T> receiver = new Receiver<T>(inputStream);
			try {
				ois.writeObject(original);
			} finally {
				ois.close();
			}
			return receiver.getResult();
		} catch (IOException iox) {
			// doesn't seem likely to happen as these streams are in memory
			throw new RuntimeException(iox);
		}
	}

	private static class Receiver<T> extends Thread {

		private final InputStream inputStream;
		private volatile T result;
		private volatile Throwable throwable;

		public Receiver(InputStream inputStream) {
			this.inputStream = inputStream;
			start();
		}

		@Override
		@SuppressWarnings("unchecked")
		public void run() {

			try {
				ObjectInputStream ois = new ObjectInputStream(inputStream);
				try {
					result = (T) ois.readObject();
					try {
						// Some serializers may write more than they actually
						// need to deserialize the object, but if we don't
						// read it all the PipedOutputStream will choke.
						while (inputStream.read() != -1) {
						}
					} catch (IOException e) {
						// The object has been successfully deserialized, so
						// ignore problems at this point (for example, the
						// serializer may have explicitly closed the inputStream
						// itself, causing this read to fail).
					}
				} finally {
					ois.close();
				}
			} catch (Throwable t) {
				throwable = t;
			}
		}

		public T getResult() throws IOException {
			try {
				join();
			} catch (InterruptedException e) {
				throw new RuntimeException("Unexpected InterruptedException", e);
			}
			// join() guarantees that all shared memory is synchronized between
			// the two threads
			if (throwable != null) {
				if (throwable instanceof ClassNotFoundException) {
					// actually can not happen in this instance
				}
				throw new RuntimeException(throwable);
			}
			return result;
		}
	}
}