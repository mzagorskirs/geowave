/**
 * Copyright (c) 2013-2020 Contributors to the Eclipse Foundation
 *
 * <p> See the NOTICE file distributed with this work for additional information regarding copyright
 * ownership. All rights reserved. This program and the accompanying materials are made available
 * under the terms of the Apache License, Version 2.0 which accompanies this distribution and is
 * available at http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package org.locationtech.geowave.core.index.sfc.tiered;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.geowave.core.index.ByteArrayUtils;
import org.locationtech.geowave.core.index.IndexMetaData;
import org.locationtech.geowave.core.index.IndexUtils;
import org.locationtech.geowave.core.index.InsertionIds;
import org.locationtech.geowave.core.index.MultiDimensionalCoordinateRanges;
import org.locationtech.geowave.core.index.MultiDimensionalCoordinates;
import org.locationtech.geowave.core.index.NumericIndexStrategy;
import org.locationtech.geowave.core.index.QueryRanges;
import org.locationtech.geowave.core.index.SinglePartitionInsertionIds;
import org.locationtech.geowave.core.index.StringUtils;
import org.locationtech.geowave.core.index.VarintUtils;
import org.locationtech.geowave.core.index.dimension.NumericDimensionDefinition;
import org.locationtech.geowave.core.index.dimension.bin.BinRange;
import org.locationtech.geowave.core.index.numeric.BinnedNumericDataset;
import org.locationtech.geowave.core.index.numeric.MultiDimensionalNumericData;
import org.locationtech.geowave.core.index.persist.PersistenceUtils;
import org.locationtech.geowave.core.index.sfc.SpaceFillingCurve;
import org.locationtech.geowave.core.index.sfc.binned.BinnedSFCUtils;

/**
 * This class wraps a single SpaceFillingCurve implementation with a tiered approach to indexing (an
 * SFC with a tier ID). This can be utilized by an overall HierarchicalNumericIndexStrategy as an
 * encapsulated sub-strategy.
 */
public class SingleTierSubStrategy implements NumericIndexStrategy {
  private static final Logger LOGGER = LogManager.getLogger(SingleTierSubStrategy.class);
  private SpaceFillingCurve sfc;
  private NumericDimensionDefinition[] baseDefinitions;
  public byte tier;

  public SingleTierSubStrategy() {}

  public SingleTierSubStrategy(
      final SpaceFillingCurve sfc,
      final NumericDimensionDefinition[] baseDefinitions,
      final byte tier) {
    this.sfc = sfc;
    this.baseDefinitions = baseDefinitions;
    this.tier = tier;
  }

  @Override
  public QueryRanges getQueryRanges(
      final MultiDimensionalNumericData indexedRange,
      final IndexMetaData... hints) {
    return getQueryRanges(indexedRange, TieredSFCIndexStrategy.DEFAULT_MAX_RANGES);
  }

  @Override
  public QueryRanges getQueryRanges(
      final MultiDimensionalNumericData indexedRange,
      final int maxRangeDecomposition,
      final IndexMetaData... hints) {
    final List<BinnedNumericDataset> binnedQueries =
        BinnedNumericDataset.applyBins(indexedRange, baseDefinitions);
    return new QueryRanges(
        BinnedSFCUtils.getQueryRanges(binnedQueries, sfc, maxRangeDecomposition, tier));
  }

  @Override
  public MultiDimensionalNumericData getRangeForId(
      final byte[] partitionKey,
      final byte[] sortKey) {
    final List<byte[]> insertionIds =
        new SinglePartitionInsertionIds(partitionKey, sortKey).getCompositeInsertionIds();
    if (insertionIds.isEmpty()) {
      LOGGER.warn("Unexpected empty insertion ID in getRangeForId()");
      return null;
    }
    final byte[] rowId = insertionIds.get(0);
    return BinnedSFCUtils.getRangeForId(rowId, baseDefinitions, sfc);
  }

  @Override
  public MultiDimensionalCoordinates getCoordinatesPerDimension(
      final byte[] partitionKey,
      final byte[] sortKey) {
    final byte[] rowId =
        ByteArrayUtils.combineArrays(
            partitionKey == null ? null : partitionKey,
            sortKey == null ? null : sortKey);
    return new MultiDimensionalCoordinates(
        new byte[] {tier},
        BinnedSFCUtils.getCoordinatesForId(rowId, baseDefinitions, sfc));
  }

  @Override
  public InsertionIds getInsertionIds(final MultiDimensionalNumericData indexedData) {
    return getInsertionIds(indexedData, 1);
  }

  @Override
  public InsertionIds getInsertionIds(
      final MultiDimensionalNumericData indexedData,
      final int maxDuplicateInsertionIds) {
    if (indexedData.isEmpty()) {
      LOGGER.warn("Cannot index empty fields, skipping writing row to index '" + getId() + "'");
      return new InsertionIds();
    }
    // we need to duplicate per bin so we can't adhere to max duplication
    // anyways
    final List<BinnedNumericDataset> ranges =
        BinnedNumericDataset.applyBins(indexedData, baseDefinitions);
    final Set<SinglePartitionInsertionIds> retVal = new HashSet<>(ranges.size());
    for (final BinnedNumericDataset range : ranges) {
      final SinglePartitionInsertionIds binRowIds =
          TieredSFCIndexStrategy.getRowIdsAtTier(range, tier, sfc, null, tier);
      if (binRowIds != null) {
        retVal.add(binRowIds);
      }
    }
    return new InsertionIds(retVal);
  }

  @Override
  public NumericDimensionDefinition[] getOrderedDimensionDefinitions() {
    return baseDefinitions;
  }

  @Override
  public String getId() {
    return StringUtils.intToString(hashCode());
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = (prime * result) + Arrays.hashCode(baseDefinitions);
    result = (prime * result) + ((sfc == null) ? 0 : sfc.hashCode());
    result = (prime * result) + tier;
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
    final SingleTierSubStrategy other = (SingleTierSubStrategy) obj;
    if (!Arrays.equals(baseDefinitions, other.baseDefinitions)) {
      return false;
    }
    if (sfc == null) {
      if (other.sfc != null) {
        return false;
      }
    } else if (!sfc.equals(other.sfc)) {
      return false;
    }
    if (tier != other.tier) {
      return false;
    }
    return true;
  }

  @Override
  public byte[] toBinary() {
    int byteBufferLength = 1 + VarintUtils.unsignedIntByteLength(baseDefinitions.length);
    final List<byte[]> dimensionBinaries = new ArrayList<>(baseDefinitions.length);
    final byte[] sfcBinary = PersistenceUtils.toBinary(sfc);
    byteBufferLength += (VarintUtils.unsignedIntByteLength(sfcBinary.length) + sfcBinary.length);
    for (final NumericDimensionDefinition dimension : baseDefinitions) {
      final byte[] dimensionBinary = PersistenceUtils.toBinary(dimension);
      byteBufferLength +=
          (VarintUtils.unsignedIntByteLength(dimensionBinary.length) + dimensionBinary.length);
      dimensionBinaries.add(dimensionBinary);
    }
    final ByteBuffer buf = ByteBuffer.allocate(byteBufferLength);
    buf.put(tier);
    VarintUtils.writeUnsignedInt(baseDefinitions.length, buf);
    VarintUtils.writeUnsignedInt(sfcBinary.length, buf);
    buf.put(sfcBinary);
    for (final byte[] dimensionBinary : dimensionBinaries) {
      VarintUtils.writeUnsignedInt(dimensionBinary.length, buf);
      buf.put(dimensionBinary);
    }
    return buf.array();
  }

  @Override
  public void fromBinary(final byte[] bytes) {
    final ByteBuffer buf = ByteBuffer.wrap(bytes);
    tier = buf.get();
    final int numDimensions = VarintUtils.readUnsignedInt(buf);
    baseDefinitions = new NumericDimensionDefinition[numDimensions];
    final byte[] sfcBinary = ByteArrayUtils.safeRead(buf, VarintUtils.readUnsignedInt(buf));
    sfc = (SpaceFillingCurve) PersistenceUtils.fromBinary(sfcBinary);
    for (int i = 0; i < numDimensions; i++) {
      final byte[] dim = ByteArrayUtils.safeRead(buf, VarintUtils.readUnsignedInt(buf));
      baseDefinitions[i] = (NumericDimensionDefinition) PersistenceUtils.fromBinary(dim);
    }
  }

  @Override
  public double[] getHighestPrecisionIdRangePerDimension() {
    return sfc.getInsertionIdRangePerDimension();
  }

  @Override
  public int getPartitionKeyLength() {
    int rowIdOffset = 1;
    for (int dimensionIdx = 0; dimensionIdx < baseDefinitions.length; dimensionIdx++) {
      final int binSize = baseDefinitions[dimensionIdx].getFixedBinIdSize();
      if (binSize > 0) {
        rowIdOffset += binSize;
      }
    }
    return rowIdOffset;
  }

  @Override
  public List<IndexMetaData> createMetaData() {
    return Collections.<IndexMetaData>emptyList();
  }

  @Override
  public MultiDimensionalCoordinateRanges[] getCoordinateRangesPerDimension(
      final MultiDimensionalNumericData dataRange,
      final IndexMetaData... hints) {
    final BinRange[][] binRangesPerDimension =
        BinnedNumericDataset.getBinnedRangesPerDimension(dataRange, baseDefinitions);
    return new MultiDimensionalCoordinateRanges[] {
        BinnedSFCUtils.getCoordinateRanges(
            binRangesPerDimension,
            sfc,
            baseDefinitions.length,
            tier)};
  }

  @Override
  public byte[][] getInsertionPartitionKeys(final MultiDimensionalNumericData insertionData) {
    return IndexUtils.getInsertionPartitionKeys(this, insertionData);
  }

  @Override
  public byte[][] getQueryPartitionKeys(
      final MultiDimensionalNumericData queryData,
      final IndexMetaData... hints) {
    return IndexUtils.getQueryPartitionKeys(this, queryData, hints);
  }
}
