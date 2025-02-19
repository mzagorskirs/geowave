/**
 * Copyright (c) 2013-2020 Contributors to the Eclipse Foundation
 *
 * <p> See the NOTICE file distributed with this work for additional information regarding copyright
 * ownership. All rights reserved. This program and the accompanying materials are made available
 * under the terms of the Apache License, Version 2.0 which accompanies this distribution and is
 * available at http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package org.locationtech.geowave.core.index;

import java.util.Collections;
import java.util.List;
import org.locationtech.geowave.core.index.dimension.NumericDimensionDefinition;
import org.locationtech.geowave.core.index.numeric.MultiDimensionalNumericData;

/**
 * This is a completely empty numeric index strategy representing no dimensions, and always
 * returning empty IDs and ranges. It can be used in cases when the data is "indexed" by another
 * means, and not using multi-dimensional numeric data.
 */
public class NullNumericIndexStrategy implements NumericIndexStrategy {
  private String id;

  protected NullNumericIndexStrategy() {
    super();
  }

  public NullNumericIndexStrategy(final String id) {
    this.id = id;
  }

  @Override
  public byte[] toBinary() {
    return StringUtils.stringToBinary(id);
  }

  @Override
  public void fromBinary(final byte[] bytes) {
    id = StringUtils.stringFromBinary(bytes);
  }

  @Override
  public QueryRanges getQueryRanges(
      final MultiDimensionalNumericData indexedRange,
      final IndexMetaData... hints) {
    return getQueryRanges(indexedRange, -1);
  }

  @Override
  public QueryRanges getQueryRanges(
      final MultiDimensionalNumericData indexedRange,
      final int maxRangeDecomposition,
      final IndexMetaData... hints) {
    // a null return here should be interpreted as negative to positive
    // infinite
    return new QueryRanges(null, null);
  }

  @Override
  public InsertionIds getInsertionIds(final MultiDimensionalNumericData indexedData) {
    return getInsertionIds(indexedData, 1);
  }

  @Override
  public NumericDimensionDefinition[] getOrderedDimensionDefinitions() {
    // there are no dimensions so return an empty array
    return new NumericDimensionDefinition[] {};
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public MultiDimensionalNumericData getRangeForId(
      final byte[] partitionKey,
      final byte[] sortKey) {
    // a null return here should be interpreted as negative to positive
    // infinite
    return null;
  }

  @Override
  public double[] getHighestPrecisionIdRangePerDimension() {
    // there are no dimensions so return an empty array
    return new double[] {};
  }

  @Override
  public MultiDimensionalCoordinates getCoordinatesPerDimension(
      final byte[] partitionKey,
      final byte[] sortKey) {
    // there are no dimensions so return an empty array
    return new MultiDimensionalCoordinates();
  }

  @Override
  public InsertionIds getInsertionIds(
      final MultiDimensionalNumericData indexedData,
      final int maxDuplicateInsertionIds) {
    // return a single empty sort key as the ID
    return new InsertionIds(null, Collections.singletonList(new byte[0]));
  }

  @Override
  public int getPartitionKeyLength() {
    return 0;
  }

  @Override
  public List<IndexMetaData> createMetaData() {
    return Collections.emptyList();
  }

  @Override
  public MultiDimensionalCoordinateRanges[] getCoordinateRangesPerDimension(
      final MultiDimensionalNumericData dataRange,
      final IndexMetaData... hints) {
    return new MultiDimensionalCoordinateRanges[] {new MultiDimensionalCoordinateRanges()};
  }

  @Override
  public byte[][] getInsertionPartitionKeys(final MultiDimensionalNumericData insertionData) {
    return null;
  }

  @Override
  public byte[][] getQueryPartitionKeys(
      final MultiDimensionalNumericData queryData,
      final IndexMetaData... hints) {
    return null;
  }
}
