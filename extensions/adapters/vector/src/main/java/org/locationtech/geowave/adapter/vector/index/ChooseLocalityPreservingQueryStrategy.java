/**
 * Copyright (c) 2013-2020 Contributors to the Eclipse Foundation
 *
 * <p> See the NOTICE file distributed with this work for additional information regarding copyright
 * ownership. All rights reserved. This program and the accompanying materials are made available
 * under the terms of the Apache License, Version 2.0 which accompanies this distribution and is
 * available at http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package org.locationtech.geowave.adapter.vector.index;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.locationtech.geowave.core.index.IndexUtils;
import org.locationtech.geowave.core.index.numeric.MultiDimensionalNumericData;
import org.locationtech.geowave.core.store.CloseableIterator;
import org.locationtech.geowave.core.store.adapter.AdapterIndexMappingStore;
import org.locationtech.geowave.core.store.adapter.InternalDataAdapter;
import org.locationtech.geowave.core.store.api.Index;
import org.locationtech.geowave.core.store.query.constraints.QueryConstraints;
import org.locationtech.geowave.core.store.statistics.DataStatisticsStore;

/**
 * This Query Strategy purely chooses the index that most closely preserves locality given a query.
 * It will behave the best assuming a single prefix query but because it doesn't always choose the
 * index with the most dimensions defined, it will not always have the most fine-grained contraints
 * given a larger set of indexable ranges.
 */
public class ChooseLocalityPreservingQueryStrategy implements IndexQueryStrategySPI {
  public static final String NAME = "Preserve Locality";

  @Override
  public String toString() {
    return NAME;
  }

  @Override
  public CloseableIterator<Index> getIndices(
      final DataStatisticsStore statisticsStore,
      final AdapterIndexMappingStore mappingStore,
      final QueryConstraints query,
      final Index[] indices,
      final InternalDataAdapter<?> adapter,
      final Map<QueryHint, Object> hints) {
    return new CloseableIterator<Index>() {
      Index nextIdx = null;
      boolean done = false;
      int i = 0;

      @Override
      public boolean hasNext() {
        double indexMax = -1;
        Index bestIdx = null;
        while (!done && (i < indices.length)) {
          nextIdx = indices[i++];
          if (nextIdx.getIndexStrategy().getOrderedDimensionDefinitions().length == 0) {
            continue;
          }
          final List<MultiDimensionalNumericData> queryRanges = query.getIndexConstraints(nextIdx);
          if (IndexUtils.isFullTableScan(queryRanges)) {
            // keep this is as a default in case all indices
            // result in a full table scan
            if (bestIdx == null) {
              bestIdx = nextIdx;
            }
          } else {
            double totalMax = 0;
            for (final MultiDimensionalNumericData qr : queryRanges) {
              final double[] dataRangePerDimension = new double[qr.getDimensionCount()];
              for (int d = 0; d < dataRangePerDimension.length; d++) {
                dataRangePerDimension[d] =
                    qr.getMaxValuesPerDimension()[d] - qr.getMinValuesPerDimension()[d];
              }
              totalMax +=
                  IndexUtils.getDimensionalBitsUsed(
                      nextIdx.getIndexStrategy(),
                      dataRangePerDimension);
            }
            if (totalMax > indexMax) {
              indexMax = totalMax;
              bestIdx = nextIdx;
            }
          }
        }
        nextIdx = bestIdx;
        done = true;
        return nextIdx != null;
      }

      @Override
      public Index next() throws NoSuchElementException {
        if (nextIdx == null) {
          throw new NoSuchElementException();
        }
        final Index returnVal = nextIdx;
        nextIdx = null;
        return returnVal;
      }

      @Override
      public void remove() {}

      @Override
      public void close() {}
    };
  }

  @Override
  public boolean requiresStats() {
    return false;
  }
}
