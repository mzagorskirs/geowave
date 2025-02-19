/**
 * Copyright (c) 2013-2020 Contributors to the Eclipse Foundation
 *
 * <p> See the NOTICE file distributed with this work for additional information regarding copyright
 * ownership. All rights reserved. This program and the accompanying materials are made available
 * under the terms of the Apache License, Version 2.0 which accompanies this distribution and is
 * available at http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package org.locationtech.geowave.core.geotime.index.dimension;

import org.locationtech.geowave.core.index.FloatCompareUtils;
import org.locationtech.geowave.core.index.dimension.BasicDimensionDefinition;
import org.locationtech.geowave.core.index.dimension.bin.BinRange;
import org.locationtech.geowave.core.index.numeric.NumericData;

/**
 * The Longitude Definition class is a convenience class used to define a dimension which is
 * associated with the X axis on a Cartesian plane.
 *
 * <p> Minimum bounds = -180 and maximum bounds = 180
 */
public class LongitudeDefinition extends BasicDimensionDefinition {

  /**
   * Convenience constructor used to construct a longitude dimension object which sits on a
   * Cartesian plane.
   */
  public LongitudeDefinition() {
    super(-180, 180);
  }

  /**
   * Method is used to normalize the ranges on a Cartesian plane. If the values are outside of the
   * bounds [ -180, 180 ], two Bin ranges might be created to account for the possible date line
   * crossing.
   *
   * @param range the numeric range of our data set
   * @return new BinRange[] object
   */
  @Override
  public BinRange[] getNormalizedRanges(final NumericData range) {
    if (range == null) {
      return new BinRange[0];
    }
    // if the range is a single value, clamp at -180, 180
    if (FloatCompareUtils.checkDoublesEqual(range.getMin(), range.getMax())) {

      return super.getNormalizedRanges(range);
    }
    // if its a range, treat values outside of (-180,180) as possible date
    // line crossing
    final double normalizedMin = getNormalizedLongitude(range.getMin());
    final double normalizedMax = getNormalizedLongitude(range.getMax());

    // If the normalized max is less than normalized min, the range
    // crosses the date line
    // also, special case min=0, max=-1 as this is used within JTS as the
    // envelope for empty geometry and we don't want empty geometry
    // interpreted as a dateline crossing
    if ((normalizedMax < normalizedMin)
        && !((FloatCompareUtils.checkDoublesEqual(normalizedMax, -1)
            && (FloatCompareUtils.checkDoublesEqual(normalizedMin, 0))))) {

      return new BinRange[] {new BinRange(-180, normalizedMax), new BinRange(normalizedMin, 180)};
    }

    return new BinRange[] {new BinRange(normalizedMin, normalizedMax)};
  }

  /**
   * Normalizes a longitude value
   *
   * @param lon value to normalize
   * @return a normalized longitude value
   */
  public static double getNormalizedLongitude(final double lon) {
    if ((lon <= 180) && (lon >= -180)) {
      return lon;
    }
    // the sign of the mod should be the sign of the dividend, but just in
    // case guarantee a mod on a positive dividend and subtract 180
    final double offsetLon = lon + 180;
    return (((Math.ceil(Math.abs(offsetLon) / 360) * 360) + offsetLon) % 360) - 180;
  }

  @Override
  public byte[] toBinary() {
    // essentially all that is needed is the class name for reflection
    return new byte[] {};
  }

  @Override
  public void fromBinary(final byte[] bytes) {}
}
