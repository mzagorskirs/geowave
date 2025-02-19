/**
 * Copyright (c) 2013-2020 Contributors to the Eclipse Foundation
 *
 * <p> See the NOTICE file distributed with this work for additional information regarding copyright
 * ownership. All rights reserved. This program and the accompanying materials are made available
 * under the terms of the Apache License, Version 2.0 which accompanies this distribution and is
 * available at http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package org.locationtech.geowave.core.geotime.adapter;

import java.util.Set;
import org.locationtech.geowave.core.index.IndexDimensionHint;
import org.locationtech.geowave.core.store.adapter.BaseFieldDescriptor;

/**
 * An adapter field descriptor to represent temporal fields.
 *
 * @param <T> the adapter field type
 */
public class TemporalFieldDescriptor<T> extends BaseFieldDescriptor<T> {
  public TemporalFieldDescriptor() {}

  public TemporalFieldDescriptor(
      final Class<T> bindingClass,
      final String fieldName,
      final Set<IndexDimensionHint> indexHints) {
    super(bindingClass, fieldName, indexHints);
  }

}
