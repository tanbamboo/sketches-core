/*
 * Copyright 2015-16, Yahoo! Inc.
 * Licensed under the terms of the Apache License 2.0. See LICENSE file at the project root for terms.
 */

package com.yahoo.sketches.theta;

import static com.yahoo.sketches.Util.checkSeedHashes;
import static com.yahoo.sketches.Util.computeSeedHash;
import static com.yahoo.sketches.theta.PreambleUtil.COMPACT_FLAG_MASK;
import static com.yahoo.sketches.theta.PreambleUtil.EMPTY_FLAG_MASK;
import static com.yahoo.sketches.theta.PreambleUtil.ORDERED_FLAG_MASK;
import static com.yahoo.sketches.theta.PreambleUtil.PREAMBLE_LONGS_BYTE;
import static com.yahoo.sketches.theta.PreambleUtil.READ_ONLY_FLAG_MASK;
import static com.yahoo.sketches.theta.PreambleUtil.RETAINED_ENTRIES_INT;
import static com.yahoo.sketches.theta.PreambleUtil.THETA_LONG;
import static com.yahoo.sketches.theta.PreambleUtil.extractFlags;
import static com.yahoo.sketches.theta.PreambleUtil.extractPreLongs;
import static com.yahoo.sketches.theta.PreambleUtil.extractSeedHash;

import com.yahoo.memory.Memory;
import com.yahoo.memory.NativeMemory;

/**
 * An on-heap, compact, ordered, read-only sketch.
 *
 * @author Lee Rhodes
 */
final class HeapCompactOrderedSketch extends CompactSketch {
  private final long[] cache_;

  private HeapCompactOrderedSketch(final boolean empty, final short seedHash, final int curCount,
      final long thetaLong, final long[] cache) {
    super(empty, seedHash, curCount, thetaLong);
    cache_ = cache;
  }

  /**
   * Heapifies the given source Memory with seed
   * @param srcMem <a href="{@docRoot}/resources/dictionary.html#mem">See Memory</a>
   * @param seed <a href="{@docRoot}/resources/dictionary.html#seed">See Update Hash Seed</a>.
   * @return this sketch
   */
  static HeapCompactOrderedSketch heapifyInstance(final Memory srcMem, final long seed) {
    final long pre0 = srcMem.getLong(PREAMBLE_LONGS_BYTE);
    final int preLongs = extractPreLongs(pre0);
    final int flags = extractFlags(pre0);
    final boolean empty = (flags & EMPTY_FLAG_MASK) > 0;
    //Note compact flag has already been verified
    final short memSeedHash = (short) extractSeedHash(pre0);
    final short computedSeedHash = computeSeedHash(seed);
    checkSeedHashes(memSeedHash, computedSeedHash);
    final int curCount = (preLongs > 1) ? srcMem.getInt(RETAINED_ENTRIES_INT) : 0;
    final long thetaLong = (preLongs > 2) ? srcMem.getLong(THETA_LONG) : Long.MAX_VALUE;
    final long[] cacheArr = new long[curCount];
    if (curCount > 0) {
      srcMem.getLongArray(preLongs << 3, cacheArr, 0, curCount);
    }
    final HeapCompactOrderedSketch hcs =
        new HeapCompactOrderedSketch(empty, memSeedHash, curCount, thetaLong, cacheArr);
    return hcs;
  }

  /**
   * Converts the given UpdateSketch to this compact form.
   * @param sketch the given UpdateSketch
   */
  HeapCompactOrderedSketch(final UpdateSketch sketch) {
    super(sketch.isEmpty(),
        sketch.getSeedHash(),
        sketch.getRetainedEntries(true), //curCount_  set here
        sketch.getThetaLong()            //thetaLong_ set here
        );
    final boolean ordered = true;
    cache_ = CompactSketch.compactCache(sketch.getCache(), getRetainedEntries(false),
        getThetaLong(), ordered);
  }

  /**
   * Constructs this sketch from correct, valid components.
   * @param compactOrderedCache in compact, ordered form
   * @param empty The correct <a href="{@docRoot}/resources/dictionary.html#empty">Empty</a>.
   * @param seedHash The correct
   * <a href="{@docRoot}/resources/dictionary.html#seedHash">Seed Hash</a>.
   * @param curCount correct value
   * @param thetaLong The correct
   * <a href="{@docRoot}/resources/dictionary.html#thetaLong">thetaLong</a>.
   */
  HeapCompactOrderedSketch(final long[] compactOrderedCache, final boolean empty,
      final short seedHash, final int curCount, final long thetaLong) {
    super(empty, seedHash, curCount, thetaLong);
    assert compactOrderedCache != null;
    cache_ = (curCount == 0) ? new long[0] : compactOrderedCache;
  }

  //Sketch interface

  @Override
  public byte[] toByteArray() {
    final byte[] byteArray = new byte[getCurrentBytes(true)];
    final Memory dstMem = new NativeMemory(byteArray);
    final int emptyBit = isEmpty() ? (byte) EMPTY_FLAG_MASK : 0;
    final byte flags = (byte) (emptyBit |  READ_ONLY_FLAG_MASK | COMPACT_FLAG_MASK | ORDERED_FLAG_MASK);
    loadCompactMemory(getCache(), isEmpty(), getSeedHash(), getRetainedEntries(true),
        getThetaLong(), dstMem, flags);
    return byteArray;
  }

  @Override
  public boolean isDirect() {
    return false;
  }

  //restricted methods

  @Override
  long[] getCache() {
    return cache_;
  }

  @Override
  Memory getMemory() {
    return null;
  }

  @Override
  public boolean isOrdered() {
    return true;
  }

}
