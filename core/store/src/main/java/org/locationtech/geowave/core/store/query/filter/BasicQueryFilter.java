/**
 * Copyright (c) 2013-2020 Contributors to the Eclipse Foundation
 *
 * <p> See the NOTICE file distributed with this work for additional information regarding copyright
 * ownership. All rights reserved. This program and the accompanying materials are made available
 * under the terms of the Apache License, Version 2.0 which accompanies this distribution and is
 * available at http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package org.locationtech.geowave.core.store.query.filter;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.locationtech.geowave.core.index.ByteArray;
import org.locationtech.geowave.core.index.ByteArrayUtils;
import org.locationtech.geowave.core.index.FloatCompareUtils;
import org.locationtech.geowave.core.index.VarintUtils;
import org.locationtech.geowave.core.index.numeric.BasicNumericDataset;
import org.locationtech.geowave.core.index.numeric.BinnedNumericDataset;
import org.locationtech.geowave.core.index.numeric.MultiDimensionalNumericData;
import org.locationtech.geowave.core.index.numeric.NumericData;
import org.locationtech.geowave.core.index.numeric.NumericRange;
import org.locationtech.geowave.core.index.persist.PersistenceUtils;
import org.locationtech.geowave.core.store.data.CommonIndexedPersistenceEncoding;
import org.locationtech.geowave.core.store.data.IndexedPersistenceEncoding;
import org.locationtech.geowave.core.store.dimension.NumericDimensionField;
import org.locationtech.geowave.core.store.index.CommonIndexModel;

/**
 * This filter can perform fine-grained acceptance testing on generic dimensions, but is limited to
 * only using MBR (min-max in a single dimension, hyper-cubes in multi-dimensional space)
 */
public class BasicQueryFilter implements QueryFilter {

  protected interface BasicQueryCompareOp {
    public boolean compare(double dataMin, double dataMax, double queryMin, double queryMax);
  }

  public enum BasicQueryCompareOperation implements BasicQueryCompareOp {
    CONTAINS {
      @Override
      public boolean compare(
          final double dataMin,
          final double dataMax,
          final double queryMin,
          final double queryMax) {
        // checking if data range contains query range
        return !((dataMin < queryMin) || (dataMax > queryMax));
      }
    },
    OVERLAPS {
      @Override
      public boolean compare(
          final double dataMin,
          final double dataMax,
          final double queryMin,
          final double queryMax) {
        // per definition, it shouldn't allow only boundary points to
        // overlap (stricter than intersect, see DE-9IM definitions)
        return !((dataMax <= queryMin) || (dataMin >= queryMax))
            && !EQUALS.compare(dataMin, dataMax, queryMin, queryMax)
            && !CONTAINS.compare(dataMin, dataMax, queryMin, queryMax)
            && !WITHIN.compare(dataMin, dataMax, queryMin, queryMax);
      }
    },
    INTERSECTS {
      @Override
      public boolean compare(
          final double dataMin,
          final double dataMax,
          final double queryMin,
          final double queryMax) {
        // similar to overlap but a bit relaxed (allows boundary points
        // to touch)
        // this is equivalent to !((dataMax < queryMin) || (dataMin >
        // queryMax));
        return !DISJOINT.compare(dataMin, dataMax, queryMin, queryMax);
      }
    },
    TOUCHES {
      @Override
      public boolean compare(
          final double dataMin,
          final double dataMax,
          final double queryMin,
          final double queryMax) {
        return (FloatCompareUtils.checkDoublesEqual(dataMin, queryMax))
            || (FloatCompareUtils.checkDoublesEqual(dataMax, queryMin));
      }
    },
    WITHIN {
      @Override
      public boolean compare(
          final double dataMin,
          final double dataMax,
          final double queryMin,
          final double queryMax) {
        // checking if query range is within the data range
        // this is equivalent to (queryMin >= dataMin) && (queryMax <=
        // dataMax);
        return CONTAINS.compare(queryMin, queryMax, dataMin, dataMax);
      }
    },
    DISJOINT {
      @Override
      public boolean compare(
          final double dataMin,
          final double dataMax,
          final double queryMin,
          final double queryMax) {
        return ((dataMax < queryMin) || (dataMin > queryMax));
      }
    },
    CROSSES {
      @Override
      public boolean compare(
          final double dataMin,
          final double dataMax,
          final double queryMin,
          final double queryMax) {
        // accordingly to the def. intersection point must be interior
        // to both source geometries.
        // this is not possible in 1D data so always returns false
        return false;
      }
    },
    EQUALS {
      @Override
      public boolean compare(
          final double dataMin,
          final double dataMax,
          final double queryMin,
          final double queryMax) {
        return (FloatCompareUtils.checkDoublesEqual(dataMin, queryMin))
            && (FloatCompareUtils.checkDoublesEqual(dataMax, queryMax));
      }
    }
  };

  protected Map<ByteArray, List<MultiDimensionalNumericData>> binnedConstraints;
  protected NumericDimensionField<?>[] dimensionFields;
  // this is referenced for serialization purposes only
  protected MultiDimensionalNumericData constraints;
  protected BasicQueryCompareOperation compareOp = BasicQueryCompareOperation.INTERSECTS;

  public BasicQueryFilter() {}

  public BasicQueryFilter(
      final MultiDimensionalNumericData constraints,
      final NumericDimensionField<?>[] dimensionFields) {
    init(constraints, dimensionFields);
  }

  public BasicQueryFilter(
      final MultiDimensionalNumericData constraints,
      final NumericDimensionField<?>[] dimensionFields,
      final BasicQueryCompareOperation compareOp) {
    init(constraints, dimensionFields);
    this.compareOp = compareOp;
  }

  private void init(
      final MultiDimensionalNumericData constraints,
      final NumericDimensionField<?>[] dimensionFields) {
    this.dimensionFields = dimensionFields;

    binnedConstraints = new HashMap<>();
    this.constraints = constraints;
    final List<BinnedNumericDataset> queries =
        BinnedNumericDataset.applyBins(constraints, dimensionFields);
    for (final BinnedNumericDataset q : queries) {
      final ByteArray binId = new ByteArray(q.getBinId());
      List<MultiDimensionalNumericData> ranges = binnedConstraints.get(binId);
      if (ranges == null) {
        ranges = new ArrayList<>();
        binnedConstraints.put(binId, ranges);
      }
      ranges.add(q);
    }
  }

  protected boolean validateConstraints(
      final BasicQueryCompareOp op,
      final MultiDimensionalNumericData queryRange,
      final MultiDimensionalNumericData dataRange) {
    final NumericData[] queryRangePerDimension = queryRange.getDataPerDimension();
    final Double[] minPerDimension = dataRange.getMinValuesPerDimension();
    final Double[] maxPerDimension = dataRange.getMaxValuesPerDimension();
    boolean ok = true;
    for (int d = 0; (d < dimensionFields.length) && ok; d++) {
      ok &=
          op.compare(
              minPerDimension[d],
              maxPerDimension[d],
              queryRangePerDimension[d].getMin(),
              queryRangePerDimension[d].getMax());
    }
    return ok;
  }

  @Override
  public boolean accept(
      final CommonIndexModel indexModel,
      final IndexedPersistenceEncoding<?> persistenceEncoding) {
    if (!(persistenceEncoding instanceof CommonIndexedPersistenceEncoding)) {
      return false;
    }
    final List<BinnedNumericDataset> dataRanges =
        BinnedNumericDataset.applyBins(
            ((CommonIndexedPersistenceEncoding) persistenceEncoding).getNumericData(
                dimensionFields),
            dimensionFields);
    if (persistenceEncoding.isAsync()) {
      return false;
    }
    // check that at least one data range overlaps at least one query range
    for (final BinnedNumericDataset dataRange : dataRanges) {
      final List<MultiDimensionalNumericData> queries =
          binnedConstraints.get(new ByteArray(dataRange.getBinId()));
      if (queries != null) {
        for (final MultiDimensionalNumericData query : queries) {
          if ((query != null) && validateConstraints(compareOp, query, dataRange)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public byte[] toBinary() {
    int byteBufferLength = VarintUtils.unsignedIntByteLength(compareOp.ordinal());
    final int dimensions = Math.min(constraints.getDimensionCount(), dimensionFields.length);
    byteBufferLength += VarintUtils.unsignedIntByteLength(dimensions);
    final byte[][] lengthDimensionAndQueryBinaries = new byte[dimensions][];
    final NumericData[] dataPerDimension = constraints.getDataPerDimension();
    for (int d = 0; d < dimensions; d++) {
      final NumericDimensionField<?> dimension = dimensionFields[d];
      final NumericData data = dataPerDimension[d];
      final byte[] dimensionBinary = PersistenceUtils.toBinary(dimension);
      final int currentDimensionByteBufferLength =
          (16 + dimensionBinary.length + VarintUtils.unsignedIntByteLength(dimensionBinary.length));

      final ByteBuffer buf = ByteBuffer.allocate(currentDimensionByteBufferLength);
      VarintUtils.writeUnsignedInt(dimensionBinary.length, buf);
      buf.putDouble(data.getMin());
      buf.putDouble(data.getMax());
      buf.put(dimensionBinary);
      byteBufferLength += currentDimensionByteBufferLength;
      lengthDimensionAndQueryBinaries[d] = buf.array();
    }
    final ByteBuffer buf = ByteBuffer.allocate(byteBufferLength);
    VarintUtils.writeUnsignedInt(compareOp.ordinal(), buf);
    VarintUtils.writeUnsignedInt(dimensions, buf);
    for (final byte[] binary : lengthDimensionAndQueryBinaries) {
      buf.put(binary);
    }
    return buf.array();
  }

  @Override
  public void fromBinary(final byte[] bytes) {
    final ByteBuffer buf = ByteBuffer.wrap(bytes);
    compareOp = BasicQueryCompareOperation.values()[VarintUtils.readUnsignedInt(buf)];
    final int numDimensions = VarintUtils.readUnsignedInt(buf);
    ByteArrayUtils.verifyBufferSize(buf, numDimensions);
    dimensionFields = new NumericDimensionField<?>[numDimensions];
    final NumericData[] data = new NumericData[numDimensions];
    for (int d = 0; d < numDimensions; d++) {
      final int fieldLength = VarintUtils.readUnsignedInt(buf);
      data[d] = new NumericRange(buf.getDouble(), buf.getDouble());
      final byte[] field = ByteArrayUtils.safeRead(buf, fieldLength);
      dimensionFields[d] = (NumericDimensionField<?>) PersistenceUtils.fromBinary(field);
    }
    constraints = new BasicNumericDataset(data);
    init(constraints, dimensionFields);
  }
}
