/**
 * Copyright (c) 2013-2020 Contributors to the Eclipse Foundation
 *
 * <p> See the NOTICE file distributed with this work for additional information regarding copyright
 * ownership. All rights reserved. This program and the accompanying materials are made available
 * under the terms of the Apache License, Version 2.0 which accompanies this distribution and is
 * available at http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package org.locationtech.geowave.core.geotime.store.query.aggregate;

import org.locationtech.geowave.core.geotime.store.dimension.SpatialField;
import org.locationtech.geowave.core.index.persist.Persistable;
import org.locationtech.geowave.core.store.api.DataTypeAdapter;
import org.locationtech.geowave.core.store.data.CommonIndexedPersistenceEncoding;
import org.locationtech.geowave.core.store.query.aggregate.CommonIndexAggregation;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

public class CommonIndexBoundingBoxAggregation<P extends Persistable> extends
    BoundingBoxAggregation<P, CommonIndexedPersistenceEncoding> implements
    CommonIndexAggregation<P, Envelope> {

  @Override
  protected Envelope getEnvelope(
      final DataTypeAdapter<CommonIndexedPersistenceEncoding> adapter,
      final CommonIndexedPersistenceEncoding entry) {
    final Object v = entry.getCommonData().getValue(SpatialField.DEFAULT_GEOMETRY_FIELD_NAME);
    if ((v != null) && (v instanceof Geometry)) {
      return ((Geometry) v).getEnvelopeInternal();
    }
    return null;
  }
}
