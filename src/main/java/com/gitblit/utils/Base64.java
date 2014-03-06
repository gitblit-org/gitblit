//
//  NOTE: The following source code is heavily derived from the
//  iHarder.net public domain Base64 library.  See the original at
//  http://iharder.sourceforge.net/current/java/base64/
//

package com.gitblit.utils;

import java.io.UnsupportedEncodingException;
import java.text.MessageFormat;
import java.util.Arrays;

/**
 * Encodes and decodes to and from Base64 notation.
 * <p>
 * I am placing this code in the Public Domain. Do with it as you will. This
 * software comes with no guarantees or warranties but with plenty of
 * well-wishing instead! Please visit <a
 * href="http://iharder.net/base64">http://iharder.net/base64</a> periodically
 * to check for updates or to contribute improvements.
 * </p>
 *
 * @author Robert Harder
 * @author rob@iharder.net
 * @version 2.1, stripped to minimum feature set used by JGit.
 */
public class Base64 {
	/** The equals sign (=) as a byte. */
	private final static byte EQUALS_SIGN = (byte) '=';

	/** Indicates equals sign in encoding. */
	private final static byte EQUALS_SIGN_DEC = -1;

	/** Indicates white space in encoding. */
	private final static byte WHITE_SPACE_DEC = -2;

	/** Indicates an invalid byte during decoding. */
	private final static byte INVALID_DEC = -3;

	/** Preferred encoding. */
	private final static String UTF_8 = "UTF-8";

	/** The 64 valid Base64 values. */
	private final static byte[] ENC;

	/**
	 * Translates a Base64 value to either its 6-bit reconstruction value or a
	 * negative number indicating some other meaning. The table is only 7 bits
	 * wide, as the 8th bit is discarded during decoding.
	 */
	private final static byte[] DEC;

	static {
		try {
			ENC = ("ABCDEFGHIJKLMNOPQRSTUVWXYZ" //
					+ "abcdefghijklmnopqrstuvwxyz" //
					+ "0123456789" //
					+ "+/" //
			).getBytes(UTF_8);
		} catch (UnsupportedEncodingException uee) {
			throw new RuntimeException(uee.getMessage(), uee);
		}

		DEC = new byte[128];
		Arrays.fill(DEC, INVALID_DEC);

		for (int i = 0; i < 64; i++)
			DEC[ENC[i]] = (byte) i;
		DEC[EQUALS_SIGN] = EQUALS_SIGN_DEC;

		DEC['\t'] = WHITE_SPACE_DEC;
		DEC['\n'] = WHITE_SPACE_DEC;
		DEC['\r'] = WHITE_SPACE_DEC;
		DEC[' '] = WHITE_SPACE_DEC;
	}

	/** Defeats instantiation. */
	private Base64() {
		// Suppress empty block warning.
	}

	/**
	 * Encodes up to three bytes of the array <var>source</var> and writes the
	 * resulting four Base64 bytes to <var>destination</var>. The source and
	 * destination arrays can be manipulated anywhere along their length by
	 * specifying <var>srcOffset</var> and <var>destOffset</var>. This method
	 * does not check to make sure your arrays are large enough to accommodate
	 * <var>srcOffset</var> + 3 for the <var>source</var> array or
	 * <var>destOffset</var> + 4 for the <var>destination</var> array. The
	 * actual number of significant bytes in your array is given by
	 * <var>numSigBytes</var>.
	 *
	 * @param source
	 *            the array to convert
	 * @param srcOffset
	 *            the index where conversion begins
	 * @param numSigBytes
	 *            the number of significant bytes in your array
	 * @param destination
	 *            the array to hold the conversion
	 * @param destOffset
	 *            the index where output will be put
	 */
	private static void encode3to4(byte[] source, int srcOffset, int numSigBytes,
			byte[] destination, int destOffset) {
		// We have to shift left 24 in order to flush out the 1's that appear
		// when Java treats a value as negative that is cast from a byte.

		int inBuff = 0;
		switch (numSigBytes) {
		case 3:
			inBuff |= (source[srcOffset + 2] << 24) >>> 24;
			//$FALL-THROUGH$

		case 2:
			inBuff |= (source[srcOffset + 1] << 24) >>> 16;
			//$FALL-THROUGH$

		case 1:
			inBuff |= (source[srcOffset] << 24) >>> 8;
		}

		switch (numSigBytes) {
		case 3:
			destination[destOffset] = ENC[(inBuff >>> 18)];
			destination[destOffset + 1] = ENC[(inBuff >>> 12) & 0x3f];
			destination[destOffset + 2] = ENC[(inBuff >>> 6) & 0x3f];
			destination[destOffset + 3] = ENC[(inBuff) & 0x3f];
			break;

		case 2:
			destination[destOffset] = ENC[(inBuff >>> 18)];
			destination[destOffset + 1] = ENC[(inBuff >>> 12) & 0x3f];
			destination[destOffset + 2] = ENC[(inBuff >>> 6) & 0x3f];
			destination[destOffset + 3] = EQUALS_SIGN;
			break;

		case 1:
			destination[destOffset] = ENC[(inBuff >>> 18)];
			destination[destOffset + 1] = ENC[(inBuff >>> 12) & 0x3f];
			destination[destOffset + 2] = EQUALS_SIGN;
			destination[destOffset + 3] = EQUALS_SIGN;
			break;
		}
	}

	/**
	 * Encodes a byte array into Base64 notation.
	 *
	 * @param source
	 *            The data to convert
	 * @return encoded base64 representation of source.
	 */
	public static String encodeBytes(byte[] source) {
		return encodeBytes(source, 0, source.length);
	}

	/**
	 * Encodes a byte array into Base64 notation.
	 *
	 * @param source
	 *            The data to convert
	 * @param off
	 *            Offset in array where conversion should begin
	 * @param len
	 *            Length of data to convert
	 * @return encoded base64 representation of source.
	 */
	public static String encodeBytes(byte[] source, int off, int len) {
		final int len43 = len * 4 / 3;

		byte[] outBuff = new byte[len43 + ((len % 3) > 0 ? 4 : 0)];
		int d = 0;
		int e = 0;
		int len2 = len - 2;

		for (; d < len2; d += 3, e += 4)
			encode3to4(source, d + off, 3, outBuff, e);

		if (d < len) {
			encode3to4(source, d + off, len - d, outBuff, e);
			e += 4;
		}

		try {
			return new String(outBuff, 0, e, UTF_8);
		} catch (UnsupportedEncodingException uue) {
			return new String(outBuff, 0, e);
		}
	}

	/**
	 * Decodes four bytes from array <var>source</var> and writes the resulting
	 * bytes (up to three of them) to <var>destination</var>. The source and
	 * destination arrays can be manipulated anywhere along their length by
	 * specifying <var>srcOffset</var> and <var>destOffset</var>. This method
	 * does not check to make sure your arrays are large enough to accommodate
	 * <var>srcOffset</var> + 4 for the <var>source</var> array or
	 * <var>destOffset</var> + 3 for the <var>destination</var> array. This
	 * method returns the actual number of bytes that were converted from the
	 * Base64 encoding.
	 *
	 * @param source
	 *            the array to convert
	 * @param srcOffset
	 *            the index where conversion begins
	 * @param destination
	 *            the array to hold the conversion
	 * @param destOffset
	 *            the index where output will be put
	 * @return the number of decoded bytes converted
	 */
	private static int decode4to3(byte[] source, int srcOffset, byte[] destination, int destOffset) {
		// Example: Dk==
		if (source[srcOffset + 2] == EQUALS_SIGN) {
			int outBuff = ((DEC[source[srcOffset]] & 0xFF) << 18)
					| ((DEC[source[srcOffset + 1]] & 0xFF) << 12);
			destination[destOffset] = (byte) (outBuff >>> 16);
			return 1;
		}

		// Example: DkL=
		else if (source[srcOffset + 3] == EQUALS_SIGN) {
			int outBuff = ((DEC[source[srcOffset]] & 0xFF) << 18)
					| ((DEC[source[srcOffset + 1]] & 0xFF) << 12)
					| ((DEC[source[srcOffset + 2]] & 0xFF) << 6);
			destination[destOffset] = (byte) (outBuff >>> 16);
			destination[destOffset + 1] = (byte) (outBuff >>> 8);
			return 2;
		}

		// Example: DkLE
		else {
			int outBuff = ((DEC[source[srcOffset]] & 0xFF) << 18)
					| ((DEC[source[srcOffset + 1]] & 0xFF) << 12)
					| ((DEC[source[srcOffset + 2]] & 0xFF) << 6)
					| ((DEC[source[srcOffset + 3]] & 0xFF));

			destination[destOffset] = (byte) (outBuff >> 16);
			destination[destOffset + 1] = (byte) (outBuff >> 8);
			destination[destOffset + 2] = (byte) (outBuff);

			return 3;
		}
	}

	/**
	 * Low-level decoding ASCII characters from a byte array.
	 *
	 * @param source
	 *            The Base64 encoded data
	 * @param off
	 *            The offset of where to begin decoding
	 * @param len
	 *            The length of characters to decode
	 * @return decoded data
	 * @throws IllegalArgumentException
	 *             the input is not a valid Base64 sequence.
	 */
	public static byte[] decode(byte[] source, int off, int len) {
		byte[] outBuff = new byte[len * 3 / 4]; // Upper limit on size of output
		int outBuffPosn = 0;

		byte[] b4 = new byte[4];
		int b4Posn = 0;

		for (int i = off; i < off + len; i++) {
			byte sbiCrop = (byte) (source[i] & 0x7f);
			byte sbiDecode = DEC[sbiCrop];

			if (EQUALS_SIGN_DEC <= sbiDecode) {
				b4[b4Posn++] = sbiCrop;
				if (b4Posn > 3) {
					outBuffPosn += decode4to3(b4, 0, outBuff, outBuffPosn);
					b4Posn = 0;

					// If that was the equals sign, break out of 'for' loop
					if (sbiCrop == EQUALS_SIGN)
						break;
				}

			} else if (sbiDecode != WHITE_SPACE_DEC)
				throw new IllegalArgumentException(MessageFormat.format(
						"bad base64 input character {1} at {0}", i, source[i] & 0xff));
		}

		if (outBuff.length == outBuffPosn)
			return outBuff;

		byte[] out = new byte[outBuffPosn];
		System.arraycopy(outBuff, 0, out, 0, outBuffPosn);
		return out;
	}

	/**
	 * Decodes data from Base64 notation.
	 *
	 * @param s
	 *            the string to decode
	 * @return the decoded data
	 */
	public static byte[] decode(String s) {
		byte[] bytes;
		try {
			bytes = s.getBytes(UTF_8);
		} catch (UnsupportedEncodingException uee) {
			bytes = s.getBytes();
		}
		return decode(bytes, 0, bytes.length);
	}
}
