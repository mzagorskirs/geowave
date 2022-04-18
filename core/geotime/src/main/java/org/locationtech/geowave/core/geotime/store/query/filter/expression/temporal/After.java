/**
 * Copyright (c) 2013-2022 Contributors to the Eclipse Foundation
 *
 * <p> See the NOTICE file distributed with this work for additional information regarding copyright
 * ownership. All rights reserved. This program and the accompanying materials are made available
 * under the terms of the Apache License, Version 2.0 which accompanies this distribution and is
 * available at http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package org.locationtech.geowave.core.geotime.store.query.filter.expression.temporal;

import org.locationtech.geowave.core.geotime.util.TimeUtils;
import org.locationtech.geowave.core.store.query.filter.expression.numeric.NumericFieldConstraints;
import org.threeten.extra.Interval;

/**
 * Predicate that passes when the first operand takes place after the second operand.
 */
public class After extends BinaryTemporalPredicate {

  public After() {}

  public After(final TemporalExpression expression1, final TemporalExpression expression2) {
    super(expression1, expression2);
  }

  @Override
  public boolean evaluateInternal(final Interval value1, final Interval value2) {
    if ((value1 == null) || (value2 == null)) {
      return false;
    }
    return value1.getStart().compareTo(TimeUtils.getIntervalEnd(value2)) >= 0;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder(expression1.toString());
    sb.append(" AFTER ");
    sb.append(expression2.toString());
    return sb.toString();
  }

  @Override
  public NumericFieldConstraints getConstraints(
      final Interval literal,
      final Double minValue,
      final Double maxValue,
      final boolean reversed,
      final boolean exact) {
    if (reversed) {
      return NumericFieldConstraints.of(
          minValue,
          (double) literal.getStart().toEpochMilli(),
          true,
          false,
          exact);
    }
    return NumericFieldConstraints.of(
        (double) TimeUtils.getIntervalEnd(literal).toEpochMilli(),
        maxValue,
        false,
        true,
        exact);
  }

}
