/*
 * Copyright 2015-16, Yahoo! Inc.
 * Licensed under the terms of the Apache License 2.0. See LICENSE file at the project root for terms.
 */

package com.yahoo.sketches.hll;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.yahoo.memory.Memory;
import com.yahoo.memory.NativeMemory;
import com.yahoo.sketches.SketchesArgumentException;

/**
 */
public class OnHeapImmutableCompactFieldsTest
{
  Preamble preamble;
  int[] vals;
  OnHeapImmutableCompactFields fields;

  @BeforeMethod
  public void setUp() throws Exception
  {
    preamble = Preamble.fromLogK(10);
    vals = new int[] {
        HashUtils.pairOfKeyAndVal(10, (byte) 27),
        HashUtils.pairOfKeyAndVal(486, (byte) 1)
    };
    fields = new OnHeapImmutableCompactFields(preamble, vals);
  }

  @Test
  public void testGetPreamble() throws Exception
  {
    Assert.assertSame(fields.getPreamble(), preamble);
  }

  @Test(expectedExceptions = {UnsupportedOperationException.class})
  public void testUpdateBucket() throws Exception
  {
    fields.updateBucket(
        1, (byte) 29, new Fields.UpdateCallback()
        {
          @Override
          public void bucketUpdated(int bucket, byte oldVal, byte newVal)
          {

          }
        }
    );
  }

  @Test
  public void testIntoByteArray() throws Exception
  {
    byte[] stored = new byte[9];
    byte[] expected = new byte[9];

    Memory expectedMemory = new NativeMemory(expected);
    expectedMemory.putByte(0, (byte) 0x2);
    expectedMemory.putInt(1, vals[0]);
    expectedMemory.putInt(5, vals[1]);

    fields.intoByteArray(stored, 0);
    Assert.assertEquals(stored, expected);


    boolean exceptionThrown = false;
    try {
      fields.intoByteArray(new byte[stored.length - 1], 0);
    } catch (SketchesArgumentException e) {
      exceptionThrown = true;
    }
    Assert.assertTrue(exceptionThrown, "Expected exception about length of array to be thrown.");
  }

  @Test
  public void testNumBytesToSerialize() throws Exception
  {
    Assert.assertEquals(fields.numBytesToSerialize(), 9);
  }

  @Test
  public void testToCompact() throws Exception
  {
    Assert.assertSame(fields.toCompact(), fields);
  }

  @Test
  public void testGetBucketIterator() throws Exception
  {
    BucketIterator biter = fields.getBucketIterator();

    int count = 0;
    while (biter.next()) {
      Assert.assertEquals(biter.getKey(), HashUtils.keyOfPair(vals[count]));
      Assert.assertEquals(biter.getValue(), HashUtils.valOfPair(vals[count]));
      ++count;
    }
    Assert.assertEquals(count, vals.length);
  }

  @Test
  public void testFromFields() throws Exception
  {
    OnHeapHashFields hashFields = new OnHeapHashFields(preamble, 16, 256, new DenseFieldsFactory());
    for (int val : vals) {
      hashFields.updateBucket(HashUtils.keyOfPair(val), HashUtils.valOfPair(val), Fields.NOOP_CB);
    }

    // We ensure that the values in the hashFields are not sorted because one of the
    // things this should test is that the static method is sorting correctly.
    boolean sorted = true;
    int prevKey = -1;
    BucketIterator biter = hashFields.getBucketIterator();
    while(biter.next()) {
      if (prevKey > biter.getKey()) {
        sorted = false;
        break;
      }
      prevKey = biter.getKey();
    }
    Assert.assertFalse(sorted, "vals was changed!  Please adjust so that it doesn't result in sorted ordering.");

    fields = OnHeapImmutableCompactFields.fromFields(hashFields);
    testGetPreamble();
    testToCompact();
    testIntoByteArray();
    testNumBytesToSerialize();
    testGetBucketIterator();
  }

  @Test(expectedExceptions = UnsupportedOperationException.class)
  public void testNoUnionBucket()
  {
    fields.unionBucketIterator(null, null);
  }

  @Test(expectedExceptions = UnsupportedOperationException.class)
  public void testNoUnionCompressedAndExceptions()
  {
    fields.unionCompressedAndExceptions(null, 0, null, null);
  }
}
