/**
 * Copyright (c) 2013-2020 Contributors to the Eclipse Foundation
 *
 * <p> See the NOTICE file distributed with this work for additional information regarding copyright
 * ownership. All rights reserved. This program and the accompanying materials are made available
 * under the terms of the Apache License, Version 2.0 which accompanies this distribution and is
 * available at http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package org.locationtech.geowave.core.geotime.index.sfc.hilbert;

import java.nio.ByteBuffer;
import org.junit.Assert;
import org.junit.Test;
import org.locationtech.geowave.core.geotime.index.dimension.LatitudeDefinition;
import org.locationtech.geowave.core.geotime.index.dimension.LongitudeDefinition;
import org.locationtech.geowave.core.index.numeric.BasicNumericDataset;
import org.locationtech.geowave.core.index.numeric.NumericData;
import org.locationtech.geowave.core.index.numeric.NumericRange;
import org.locationtech.geowave.core.index.sfc.RangeDecomposition;
import org.locationtech.geowave.core.index.sfc.SFCDimensionDefinition;
import org.locationtech.geowave.core.index.sfc.SFCFactory;
import org.locationtech.geowave.core.index.sfc.SFCFactory.SFCType;
import org.locationtech.geowave.core.index.sfc.SpaceFillingCurve;
import com.google.common.primitives.SignedBytes;

public class HilbertSFCTest {

  @Test
  public void testGetId_2DSpatialMaxValue() throws Exception {

    final int LATITUDE_BITS = 31;
    final int LONGITUDE_BITS = 31;

    final Double[] testValues = new Double[] {90d, 180d};
    final long expectedID = 3074457345618258602L;

    final SFCDimensionDefinition[] SPATIAL_DIMENSIONS =
        new SFCDimensionDefinition[] {
            new SFCDimensionDefinition(new LatitudeDefinition(), LATITUDE_BITS),
            new SFCDimensionDefinition(new LongitudeDefinition(), LONGITUDE_BITS)};

    final SpaceFillingCurve hilbertSFC =
        SFCFactory.createSpaceFillingCurve(SPATIAL_DIMENSIONS, SFCType.HILBERT);
    Assert.assertEquals(expectedID, ByteBuffer.wrap(hilbertSFC.getId(testValues)).getLong());
  }

  @Test
  public void testGetId_2DSpatialMinValue() throws Exception {

    final int LATITUDE_BITS = 31;
    final int LONGITUDE_BITS = 31;

    final Double[] testValues = new Double[] {-90d, -180d};
    final long expectedID = 0L;

    final SFCDimensionDefinition[] SPATIAL_DIMENSIONS =
        new SFCDimensionDefinition[] {
            new SFCDimensionDefinition(new LatitudeDefinition(), LATITUDE_BITS),
            new SFCDimensionDefinition(new LongitudeDefinition(), LONGITUDE_BITS)};

    final SpaceFillingCurve hilbertSFC =
        SFCFactory.createSpaceFillingCurve(SPATIAL_DIMENSIONS, SFCType.HILBERT);

    Assert.assertEquals(expectedID, ByteBuffer.wrap(hilbertSFC.getId(testValues)).getLong());
  }

  @Test
  public void testGetId_2DSpatialCentroidValue() throws Exception {

    final int LATITUDE_BITS = 31;
    final int LONGITUDE_BITS = 31;

    final Double[] testValues = new Double[] {0d, 0d};
    final long expectedID = 768614336404564650L;

    final SFCDimensionDefinition[] SPATIAL_DIMENSIONS =
        new SFCDimensionDefinition[] {
            new SFCDimensionDefinition(new LatitudeDefinition(), LATITUDE_BITS),
            new SFCDimensionDefinition(new LongitudeDefinition(), LONGITUDE_BITS)};

    final SpaceFillingCurve hilbertSFC =
        SFCFactory.createSpaceFillingCurve(SPATIAL_DIMENSIONS, SFCType.HILBERT);
    Assert.assertEquals(expectedID, ByteBuffer.wrap(hilbertSFC.getId(testValues)).getLong());
  }

  @Test
  public void testGetId_2DSpatialLexicographicOrdering() throws Exception {

    final int LATITUDE_BITS = 31;
    final int LONGITUDE_BITS = 31;

    final Double[] minValue = new Double[] {-90d, -180d};
    final Double[] maxValue = new Double[] {90d, 180d};

    final SFCDimensionDefinition[] SPATIAL_DIMENSIONS =
        new SFCDimensionDefinition[] {
            new SFCDimensionDefinition(new LatitudeDefinition(), LATITUDE_BITS),
            new SFCDimensionDefinition(new LongitudeDefinition(), LONGITUDE_BITS)};

    final SpaceFillingCurve hilbertSFC =
        SFCFactory.createSpaceFillingCurve(SPATIAL_DIMENSIONS, SFCType.HILBERT);

    Assert.assertTrue(
        SignedBytes.lexicographicalComparator().compare(
            hilbertSFC.getId(minValue),
            hilbertSFC.getId(maxValue)) < 0);
  }

  // @Test(expected = IllegalArgumentException.class)
  public void testGetId_2DSpatialIllegalArgument() throws Exception {

    final int LATITUDE_BITS = 31;
    final int LONGITUDE_BITS = 31;

    final Double[] testValues = new Double[] {-100d, -180d};
    final long expectedID = 0L;

    final SFCDimensionDefinition[] SPATIAL_DIMENSIONS =
        new SFCDimensionDefinition[] {
            new SFCDimensionDefinition(new LatitudeDefinition(), LATITUDE_BITS),
            new SFCDimensionDefinition(new LongitudeDefinition(), LONGITUDE_BITS)};

    final SpaceFillingCurve hilbertSFC =
        SFCFactory.createSpaceFillingCurve(SPATIAL_DIMENSIONS, SFCType.HILBERT);

    Assert.assertEquals(expectedID, ByteBuffer.wrap(hilbertSFC.getId(testValues)).getLong());
  }

  @Test
  public void testDecomposeQuery_2DSpatialOneIndexFilter() {

    final int LATITUDE_BITS = 31;
    final int LONGITUDE_BITS = 31;

    final SFCDimensionDefinition[] SPATIAL_DIMENSIONS =
        new SFCDimensionDefinition[] {
            new SFCDimensionDefinition(new LongitudeDefinition(), LONGITUDE_BITS),
            new SFCDimensionDefinition(new LatitudeDefinition(), LATITUDE_BITS)};

    final SpaceFillingCurve hilbertSFC =
        SFCFactory.createSpaceFillingCurve(SPATIAL_DIMENSIONS, SFCType.HILBERT);

    // Create a IndexRange object using the x axis
    final NumericRange rangeX = new NumericRange(55, 57);

    // Create a IndexRange object using the y axis
    final NumericRange rangeY = new NumericRange(25, 27);
    final BasicNumericDataset spatialQuery =
        new BasicNumericDataset(new NumericData[] {rangeX, rangeY});

    final RangeDecomposition rangeDecomposition = hilbertSFC.decomposeRange(spatialQuery, true, 1);

    Assert.assertEquals(1, rangeDecomposition.getRanges().length);
  }

  @Test
  public void testDecomposeQuery_2DSpatialTwentyIndexFilters() {

    final int LATITUDE_BITS = 31;
    final int LONGITUDE_BITS = 31;

    final SFCDimensionDefinition[] SPATIAL_DIMENSIONS =
        new SFCDimensionDefinition[] {
            new SFCDimensionDefinition(new LongitudeDefinition(), LONGITUDE_BITS),
            new SFCDimensionDefinition(new LatitudeDefinition(), LATITUDE_BITS)};

    final SpaceFillingCurve hilbertSFC =
        SFCFactory.createSpaceFillingCurve(SPATIAL_DIMENSIONS, SFCType.HILBERT);
    // Create a IndexRange object using the x axis
    final NumericRange rangeX = new NumericRange(10, 57);

    // Create a IndexRange object using the y axis
    final NumericRange rangeY = new NumericRange(25, 50);
    final BasicNumericDataset spatialQuery =
        new BasicNumericDataset(new NumericData[] {rangeX, rangeY});

    final RangeDecomposition rangeDecomposition = hilbertSFC.decomposeRange(spatialQuery, true, 20);

    Assert.assertEquals(20, rangeDecomposition.getRanges().length);
  }

  /* public void testDecomposeQuery_2DSpatialRanges() {} */
}
