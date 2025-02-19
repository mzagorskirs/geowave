/**
 * Copyright (c) 2013-2020 Contributors to the Eclipse Foundation
 *
 * <p> See the NOTICE file distributed with this work for additional information regarding copyright
 * ownership. All rights reserved. This program and the accompanying materials are made available
 * under the terms of the Apache License, Version 2.0 which accompanies this distribution and is
 * available at http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package org.locationtech.geowave.format.landsat8.index;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.locationtech.geowave.core.index.dimension.bin.BinRange;
import org.locationtech.geowave.core.index.dimension.bin.BinValue;
import org.locationtech.geowave.core.index.dimension.bin.IndexBinningStrategy;
import org.locationtech.geowave.core.index.numeric.NumericData;
import org.locationtech.geowave.core.index.numeric.NumericRange;

/**
 * This class is useful for establishing a consistent binning strategy with the bin size being 256 *
 * 16 days.
 */
public class Landsat8TemporalBinningStrategy implements IndexBinningStrategy {
  protected static final long MILLIS_PER_DAY = 86400000L;
  protected static final long BIN_SIZE_MILLIS = MILLIS_PER_DAY * 16 * 256;
  protected static final long ORIGIN_MILLIS = 1420070400L;

  public Landsat8TemporalBinningStrategy() {}

  @Override
  public double getBinMin() {
    return 0;
  }

  @Override
  public double getBinMax() {
    return getBinSizeMillis() - 1;
  }

  /** Method used to bin a raw date in milliseconds to a binned value of the Binning Strategy. */
  @Override
  public BinValue getBinnedValue(final double value) {
    final long millisFromOrigin = (long) value - ORIGIN_MILLIS;
    if (millisFromOrigin < 0) {
      final int binId = (int) (((millisFromOrigin - BIN_SIZE_MILLIS) + 1) / BIN_SIZE_MILLIS);
      final long startOfEpochFromOrigin = binId * BIN_SIZE_MILLIS;
      final ByteBuffer buf = ByteBuffer.allocate(4);
      buf.putInt(binId);
      return new BinValue(buf.array(), millisFromOrigin - startOfEpochFromOrigin);
    } else {
      final int binId = (int) (millisFromOrigin / BIN_SIZE_MILLIS);
      final ByteBuffer buf = ByteBuffer.allocate(4);
      buf.putInt(binId);
      return new BinValue(buf.array(), millisFromOrigin % BIN_SIZE_MILLIS);
    }
  }

  private long getStartEpoch(final byte[] binId) {
    final ByteBuffer buf = ByteBuffer.wrap(binId);
    final int binsFromOrigin = buf.getInt();
    final long millisFromOrigin = binsFromOrigin * BIN_SIZE_MILLIS;
    return ORIGIN_MILLIS + millisFromOrigin;
  }

  private long getBinSizeMillis() {
    return BIN_SIZE_MILLIS;
  }

  @Override
  public int getFixedBinIdSize() {
    return 4;
  }

  private byte[] getBinId(final long value) {
    final long millisFromOrigin = value - ORIGIN_MILLIS;
    if (millisFromOrigin < 0) {
      final int binId = (int) (((millisFromOrigin - BIN_SIZE_MILLIS) + 1) / BIN_SIZE_MILLIS);
      final ByteBuffer buf = ByteBuffer.allocate(4);
      buf.putInt(binId);
      return buf.array();
    } else {
      final int binId = (int) (millisFromOrigin / BIN_SIZE_MILLIS);
      final ByteBuffer buf = ByteBuffer.allocate(4);
      buf.putInt(binId);
      return buf.array();
    }
  }

  @Override
  public BinRange[] getNormalizedRanges(final NumericData range) {
    // now make sure all bin definitions between the start and end bins
    // are covered
    final long millisFromOrigin = range.getMin().longValue() - ORIGIN_MILLIS;
    final int binId;
    if (millisFromOrigin < 0) {
      binId = (int) (millisFromOrigin / BIN_SIZE_MILLIS) - 1;
    } else {
      binId = (int) (millisFromOrigin / BIN_SIZE_MILLIS);
    }
    final long startOfEpochFromOrigin = binId * BIN_SIZE_MILLIS;
    long epochIterator = startOfEpochFromOrigin + ORIGIN_MILLIS;
    final List<BinRange> bins = new ArrayList<>();
    // track this, so that we can easily declare a range to be the full
    // extent and use the information to perform a more efficient scan
    boolean firstBin = (millisFromOrigin != startOfEpochFromOrigin);
    boolean lastBin = false;
    do {
      final long nextEpoch = epochIterator + BIN_SIZE_MILLIS;
      final long maxOfBin = nextEpoch - 1;
      long startMillis, endMillis;
      boolean fullExtent;
      if (range.getMax().longValue() <= maxOfBin) {
        lastBin = true;
        endMillis = range.getMax().longValue();
        // its questionable whether we use
        fullExtent = (range.getMax().longValue()) == maxOfBin;
      } else {
        endMillis = maxOfBin;
        fullExtent = !firstBin;
      }

      if (firstBin) {
        startMillis = range.getMin().longValue();
        firstBin = false;
      } else {
        startMillis = epochIterator;
      }

      // we have the millis for range, but to normalize for this bin we
      // need to subtract the epoch of the bin
      bins.add(
          new BinRange(
              getBinId(epochIterator),
              startMillis - epochIterator,
              endMillis - epochIterator,
              fullExtent));
      epochIterator = nextEpoch;
      // iterate until we reach our end epoch
    } while (!lastBin);
    return bins.toArray(new BinRange[bins.size()]);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    final String className = getClass().getName();
    result = (prime * result) + ((className == null) ? 0 : className.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    return true;
  }

  @Override
  public NumericRange getDenormalizedRanges(final BinRange binnedRange) {
    final long startOfEpochMillis = getStartEpoch(binnedRange.getBinId());
    final long minMillis = startOfEpochMillis + (long) binnedRange.getNormalizedMin();
    final long maxMillis = startOfEpochMillis + (long) binnedRange.getNormalizedMax();
    return new NumericRange(minMillis, maxMillis);
  }

  @Override
  public byte[] toBinary() {
    return new byte[] {};
  }

  @Override
  public void fromBinary(final byte[] bytes) {}
}
