// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.gitblit.utils;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/** Simple class to produce 4 billion keys randomly distributed. */
public class IdGenerator {
  /** Format an id created by this class as a hex string. */
  public static String format(int id) {
    final char[] r = new char[8];
    for (int p = 7; 0 <= p; p--) {
      final int h = id & 0xf;
      r[p] = h < 10 ? (char) ('0' + h) : (char) ('a' + (h - 10));
      id >>= 4;
    }
    return new String(r);
  }

  private final AtomicInteger gen;

  public IdGenerator() {
    gen = new AtomicInteger(new Random().nextInt());
  }

  /** Produce the next identifier. */
  public int next() {
    return mix(gen.getAndIncrement());
  }

  private static final int salt = 0x9e3779b9;

  static int mix(int in) {
    return mix(salt, in);
  }

  /** A very simple bit permutation to mask a simple incrementer. */
  public static int mix(final int salt, final int in) {
    short v0 = hi16(in);
    short v1 = lo16(in);
    v0 += ((v1 << 2) + 0 ^ v1) + (salt ^ (v1 >>> 3)) + 1;
    v1 += ((v0 << 2) + 2 ^ v0) + (salt ^ (v0 >>> 3)) + 3;
    return result(v0, v1);
  }

  /* For testing only. */
  static int unmix(final int in) {
    short v0 = hi16(in);
    short v1 = lo16(in);
    v1 -= ((v0 << 2) + 2 ^ v0) + (salt ^ (v0 >>> 3)) + 3;
    v0 -= ((v1 << 2) + 0 ^ v1) + (salt ^ (v1 >>> 3)) + 1;
    return result(v0, v1);
  }

  private static short hi16(final int in) {
    return (short) ( //
    ((in >>> 24 & 0xff)) | //
    ((in >>> 16 & 0xff) << 8) //
    );
  }

  private static short lo16(final int in) {
    return (short) ( //
    ((in >>> 8 & 0xff)) | //
    ((in & 0xff) << 8) //
    );
  }

  private static int result(final short v0, final short v1) {
    return ((v0 & 0xff) << 24) | //
        (((v0 >>> 8) & 0xff) << 16) | //
        ((v1 & 0xff) << 8) | //
        ((v1 >>> 8) & 0xff);
  }
}
